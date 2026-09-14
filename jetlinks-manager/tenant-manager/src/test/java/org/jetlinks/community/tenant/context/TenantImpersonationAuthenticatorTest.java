package org.jetlinks.community.tenant.context;

import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.DefaultDimensionType;
import org.hswebframework.web.authorization.Permission;
import org.hswebframework.web.authorization.events.AuthorizationInitializeEvent;
import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.hswebframework.web.authorization.simple.SimpleDimension;
import org.hswebframework.web.authorization.simple.SimplePermission;
import org.hswebframework.web.authorization.simple.SimpleUser;
import org.jetlinks.community.auth.entity.RoleEntity;
import org.jetlinks.community.auth.initialize.MenuAuthenticationInitializeService;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantDimensionType;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantImpersonationAuthenticatorTest {

    private static final String ADMIN_ROLE = "platform-admin";
    private static final String TENANT = "7";
    private static final String TENANT_ADMIN_ROLE = TenantConstants.tenantAdminRoleId(TENANT);

    @SuppressWarnings("unchecked")
    private final ReactiveRepository<TenantEntity, String> tenantRepository = mock(ReactiveRepository.class);

    @SuppressWarnings("unchecked")
    private final ReactiveRepository<RoleEntity, String> roleRepository = mock(ReactiveRepository.class);

    private final MenuAuthenticationInitializeService menuPermissionService = mock(MenuAuthenticationInitializeService.class);

    private final TenantImpersonationAuthenticator authenticator =
        new TenantImpersonationAuthenticator(new TenantProperties(), tenantRepository, roleRepository, menuPermissionService);

    @Test
    void platformAdminIsDowngradedToTenantAdmin() {
        givenTenant();
        givenAdminRole(Mono.just(adminRole()));
        givenGrantedPermissions("device-instance");

        StepVerifier
            .create(authenticator.resolve(platformAdmin(), TENANT))
            .assertNext(derived -> {
                assertEquals("admin", derived.getUser().getId());
                assertTrue(TenantImpersonation.isImpersonated(derived));
                assertFalse(TenantContext.isPlatformAdmin(derived, ADMIN_ROLE), "降权后不得保留平台管理员身份");
                assertEquals(TENANT, TenantContext.resolve(derived, Context.empty(), ADMIN_ROLE).getTenantId().orElse(null));
                assertTrue(derived.hasDimension(DefaultDimensionType.role, TENANT_ADMIN_ROLE));
                assertTrue(derived.hasPermission("device-instance", "query"));
                assertFalse(derived.hasPermission("tenant", "query"), "*:* 不得随降权身份带入");
            })
            .verifyComplete();
    }

    @Test
    void nonPlatformUserIsReturnedUnchanged() {
        Authentication tenantUser = tenantUser();

        StepVerifier
            .create(authenticator.resolve(tenantUser, "8"))
            .assertNext(result -> assertSame(tenantUser, result))
            .verifyComplete();
        verifyNoInteractions(tenantRepository, roleRepository, menuPermissionService);
    }

    @Test
    void tenantWithoutAdminRoleGetsNoPermissions() {
        givenTenant();
        givenAdminRole(Mono.empty());
        givenGrantedPermissions();

        StepVerifier
            .create(authenticator.resolve(platformAdmin(), TENANT))
            .assertNext(derived -> {
                assertEquals(TENANT, TenantContext.currentTenant(derived).orElse(null));
                assertFalse(derived.hasDimension(DefaultDimensionType.role, TENANT_ADMIN_ROLE));
                assertTrue(derived.getPermissions().isEmpty());
            })
            .verifyComplete();
    }

    @Test
    void missingTenantStillScopedToRequestedTenant() {
        // 租户被删后仍按原租户号收口, 不能退回平台视角
        when(tenantRepository.findById(TENANT)).thenReturn(Mono.empty());
        givenAdminRole(Mono.empty());
        givenGrantedPermissions();

        StepVerifier
            .create(authenticator.resolve(platformAdmin(), TENANT))
            .assertNext(derived -> assertEquals(TENANT, TenantContext.currentTenant(derived).orElse(null)))
            .verifyComplete();
    }

    @Test
    void grantIsCachedPerTenantButPermissionsAreCopied() {
        givenTenant();
        givenAdminRole(Mono.just(adminRole()));
        givenGrantedPermissions("device-instance");

        Authentication first = authenticator.resolve(platformAdmin(), TENANT).block();
        Authentication second = authenticator.resolve(platformAdmin(), TENANT).block();

        verify(menuPermissionService, times(1)).refactorPermission(any());
        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first.getPermissions().get(0), second.getPermissions().get(0));
    }

    @Test
    void failedLoadIsNotCached() {
        AtomicInteger attempts = new AtomicInteger();
        givenTenant();
        givenAdminRole(Mono.defer(() -> attempts.incrementAndGet() == 1
            ? Mono.error(new IllegalStateException("db down"))
            : Mono.just(adminRole())));
        givenGrantedPermissions("device-instance");

        StepVerifier
            .create(authenticator.resolve(platformAdmin(), TENANT))
            .expectError(IllegalStateException.class)
            .verify();
        StepVerifier
            .create(authenticator.resolve(platformAdmin(), TENANT))
            .assertNext(derived -> assertTrue(derived.hasPermission("device-instance", "query")))
            .verifyComplete();
    }

    @Test
    void loadingRunsWithoutAuthenticationInContext() {
        // 装配期查询若带着身份, 会被隔离监听器注入租户条件
        givenTenant();
        givenAdminRole(Mono.deferContextual(ctx -> ctx.hasKey(Authentication.class)
            ? Mono.error(new IllegalStateException("authentication leaked into loading"))
            : Mono.just(adminRole())));
        givenGrantedPermissions();

        StepVerifier
            .create(authenticator
                        .resolve(platformAdmin(), TENANT)
                        .contextWrite(Context.of(Authentication.class, platformAdmin())))
            .expectNextCount(1)
            .verifyComplete();
    }

    private void givenTenant() {
        TenantEntity tenant = new TenantEntity();
        tenant.setId(TENANT);
        tenant.setName("长沙澜光");
        when(tenantRepository.findById(TENANT)).thenReturn(Mono.just(tenant));
    }

    private void givenAdminRole(Mono<RoleEntity> role) {
        when(roleRepository.findById(TENANT_ADMIN_ROLE)).thenReturn(role);
    }

    private void givenGrantedPermissions(String... permissionIds) {
        doAnswer(invocation -> {
            AuthorizationInitializeEvent event = invocation.getArgument(0);
            event.async(Mono.fromRunnable(() -> {
                SimpleAuthentication granted = new SimpleAuthentication();
                granted.setPermissions(Arrays
                                           .stream(permissionIds)
                                           .map(id -> (Permission) SimplePermission
                                               .builder()
                                               .id(id)
                                               .actions(new HashSet<>(Collections.singleton("query")))
                                               .build())
                                           .collect(Collectors.toList()));
                event.setAuthentication(event.getAuthentication().merge(granted));
            }));
            return null;
        }).when(menuPermissionService).refactorPermission(any());
    }

    private RoleEntity adminRole() {
        RoleEntity role = new RoleEntity();
        role.setId(TENANT_ADMIN_ROLE);
        role.setName("租户管理员");
        return role;
    }

    private Authentication platformAdmin() {
        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(SimpleUser.builder().id("admin").username("admin").name("admin").build());
        auth.addDimension(SimpleDimension.of(ADMIN_ROLE, "平台管理员", DefaultDimensionType.role, Collections.emptyMap()));
        auth.setPermissions(Collections.singletonList(SimplePermission
                                                          .builder()
                                                          .id("*")
                                                          .actions(new HashSet<>(Collections.singleton("*")))
                                                          .build()));
        return auth;
    }

    private Authentication tenantUser() {
        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(SimpleUser.builder().id("u8").username("easytrans_admin").name("user").build());
        auth.addDimension(SimpleDimension.of("8", "租户", TenantDimensionType.tenant, Collections.emptyMap()));
        return auth;
    }
}
