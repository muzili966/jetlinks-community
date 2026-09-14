package org.jetlinks.community.tenant.role;

import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.DefaultDimensionType;
import org.hswebframework.web.authorization.exception.AccessDenyException;
import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.hswebframework.web.authorization.simple.SimpleDimension;
import org.hswebframework.web.authorization.simple.SimpleUser;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.jetlinks.community.auth.entity.RoleEntity;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantDimensionType;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.ext.TenantRoleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantGrantGuardTest {

    private final TenantProperties properties = new TenantProperties();

    @SuppressWarnings("unchecked")
    private final ReactiveRepository<RoleEntity, String> roleRepository = mock(ReactiveRepository.class);

    private final UserTokenManager userTokenManager = mock(UserTokenManager.class);

    private final TenantGrantGuard guard;

    private final AtomicBoolean passed = new AtomicBoolean();

    private final WebFilterChain chain = exchange -> Mono.fromRunnable(() -> passed.set(true));

    TenantGrantGuardTest() {
        properties.setEnabled(true);
        guard = new TenantGrantGuard(properties, roleRepository, userTokenManager);
    }

    @Test
    void impersonatedAdminDeniedOnPlatformOnlyPath() {
        StepVerifier
            .create(filter("/tenant/_query", impersonatedAdmin("7")))
            .expectError(AccessDenyException.class)
            .verify();
        assertFalse(passed.get());
        verifyNoInteractions(userTokenManager);
    }

    @Test
    void platformAdminOutsideTenantViewPassesPlatformOnlyPath() {
        StepVerifier
            .create(filter("/tenant/_query", platformAdmin()))
            .verifyComplete();
        assertTrue(passed.get());
    }

    @Test
    void impersonatedAdminCannotGrantMenusToForeignTenantRole() {
        when(roleRepository.findById("tenant-admin-8")).thenReturn(Mono.just(roleOf("tenant-admin-8", "8")));

        StepVerifier
            .create(filter("/menu/role/tenant-admin-8/_grant", impersonatedAdmin("7")))
            .expectError(AccessDenyException.class)
            .verify();
        assertFalse(passed.get());
    }

    @Test
    void impersonatedAdminCanGrantMenusToImpersonatedTenantRole() {
        when(roleRepository.findById("tenant-admin-7")).thenReturn(Mono.just(roleOf("tenant-admin-7", "7")));

        StepVerifier
            .create(filter("/menu/role/tenant-admin-7/_grant", impersonatedAdmin("7")))
            .verifyComplete();
        assertTrue(passed.get());
    }

    private Mono<Void> filter(String path, Authentication auth) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.put(path).build());
        return guard
            .filter(exchange, chain)
            .contextWrite(Context.of(Authentication.class, auth));
    }

    private TenantRoleEntity roleOf(String roleId, String tenantId) {
        TenantRoleEntity role = new TenantRoleEntity();
        role.setId(roleId);
        role.setTenantId(tenantId);
        return role;
    }

    private Authentication platformAdmin() {
        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(SimpleUser.builder().id("admin").username("admin").name("admin").build());
        auth.addDimension(SimpleDimension.of("platform-admin", "平台管理员", DefaultDimensionType.role, Collections.emptyMap()));
        return auth;
    }

    private Authentication impersonatedAdmin(String tenantId) {
        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(SimpleUser.builder().id("admin").username("admin").name("admin").build());
        auth.addDimension(SimpleDimension.of(tenantId, "租户", TenantDimensionType.tenant, Collections.emptyMap()));
        auth.addDimension(SimpleDimension.of(TenantConstants.tenantAdminRoleId(tenantId), "租户管理员",
                                             DefaultDimensionType.role, Collections.emptyMap()));
        auth.setAttribute(TenantConstants.IMPERSONATOR_ATTRIBUTE, "admin");
        return auth;
    }
}
