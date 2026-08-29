package org.jetlinks.community.tenant.context;

import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.DefaultDimensionType;
import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.hswebframework.web.authorization.simple.SimpleDimension;
import org.hswebframework.web.authorization.simple.SimpleUser;
import org.jetlinks.community.tenant.TenantDimensionType;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    private static final String ADMIN_ROLE = "platform-admin";
    private static final String TENANT_A = "t001";
    private static final String TENANT_B = "t002";

    private Authentication tenantUser(String tenantId) {
        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(SimpleUser.builder().id("u1").name("user").build());
        auth.setDimensions(Collections.singletonList(
            SimpleDimension.of(tenantId, "租户", TenantDimensionType.tenant, Collections.emptyMap())
        ));
        return auth;
    }

    private Authentication platformAdmin() {
        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(SimpleUser.builder().id("admin").name("admin").build());
        auth.setDimensions(Collections.singletonList(
            SimpleDimension.of(ADMIN_ROLE, "平台管理员", DefaultDimensionType.role, Collections.emptyMap())
        ));
        return auth;
    }

    private Authentication noTenantUser() {
        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(SimpleUser.builder().id("u2").name("orphan").build());
        auth.setDimensions(Collections.emptyList());
        return auth;
    }

    @Test
    void tenantUserResolvesOwnTenant() {
        TenantContext.Resolution resolution =
            TenantContext.resolve(tenantUser(TENANT_A), Context.empty(), ADMIN_ROLE);

        assertFalse(resolution.isPlatformBypass());
        assertFalse(resolution.isMissing());
        assertEquals(TENANT_A, resolution.getTenantId().orElse(null));
    }

    @Test
    void userWithoutTenantIsMissing() {
        TenantContext.Resolution resolution =
            TenantContext.resolve(noTenantUser(), Context.empty(), ADMIN_ROLE);

        assertTrue(resolution.isMissing());
        assertFalse(resolution.isPlatformBypass());
        assertTrue(resolution.getTenantId().isEmpty());
    }

    @Test
    void platformAdminBypassesWithoutImpersonation() {
        TenantContext.Resolution resolution =
            TenantContext.resolve(platformAdmin(), Context.empty(), ADMIN_ROLE);

        assertTrue(resolution.isPlatformBypass());
        assertTrue(resolution.getTenantId().isEmpty());
    }

    @Test
    void platformAdminImpersonatesTargetTenant() {
        Context ctx = TenantImpersonation.write(Context.empty(), TENANT_B);

        TenantContext.Resolution resolution =
            TenantContext.resolve(platformAdmin(), ctx, ADMIN_ROLE);

        assertFalse(resolution.isPlatformBypass());
        assertEquals(TENANT_B, resolution.getTenantId().orElse(null));
    }

    @Test
    void impersonationHeaderIgnoredForNormalUser() {
        // 普通用户携带代理头不生效, 仍按自己的租户过滤
        Context ctx = TenantImpersonation.write(Context.empty(), TENANT_B);

        TenantContext.Resolution resolution =
            TenantContext.resolve(tenantUser(TENANT_A), ctx, ADMIN_ROLE);

        assertEquals(TENANT_A, resolution.getTenantId().orElse(null));
    }
}
