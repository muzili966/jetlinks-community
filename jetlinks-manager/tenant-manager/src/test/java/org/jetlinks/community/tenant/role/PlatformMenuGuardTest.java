package org.jetlinks.community.tenant.role;

import org.jetlinks.community.tenant.TenantConstants;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class PlatformMenuGuardTest {

    private static final Collection<String> MIXED = Arrays.asList(
        "device/Instance", "system/Tenant", "device/Product", "system/Menu");

    @Test
    void platformRoleKeepsAllMenus() {
        Collection<String> kept = PlatformMenuGuard.filterForRole("platform-admin", MIXED);
        assertEquals(4, kept.size());
        assertTrue(kept.contains("system/Tenant"));
    }

    @Test
    void tenantAdminRoleStrippedOfPlatformMenus() {
        String roleId = TenantConstants.tenantAdminRoleId("t001");
        Collection<String> kept = PlatformMenuGuard.filterForRole(roleId, MIXED);

        assertEquals(2, kept.size());
        assertTrue(kept.contains("device/Instance"));
        assertFalse(kept.contains("system/Tenant"), "租户管理员不得被授予租户管理菜单");
        assertFalse(kept.contains("system/Menu"));
    }

    @Test
    void tenantCustomRoleStrippedToo() {
        Collection<String> kept = PlatformMenuGuard.filterForRole("tenant-user", MIXED);
        assertEquals(2, kept.size());
        assertFalse(kept.contains("system/Tenant"));
    }

    @Test
    void platformOnlyRecognition() {
        assertTrue(PlatformMenuGuard.isPlatformOnly("system/Tenant"));
        assertTrue(PlatformMenuGuard.isPlatformOnly("system/Menu"));
        assertFalse(PlatformMenuGuard.isPlatformOnly("device/Instance"));
        assertFalse(PlatformMenuGuard.isPlatformOnly(null));
    }

    @Test
    void tenantAdminRoleIdRoundTrip() {
        String id = TenantConstants.tenantAdminRoleId("t001");
        assertEquals("tenant-admin-t001", id);
        assertTrue(TenantConstants.isTenantAdminRole(id));
        assertFalse(TenantConstants.isTenantAdminRole("platform-admin"));
        assertFalse(TenantConstants.isTenantAdminRole(null));
    }
}
