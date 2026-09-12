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
    void globalConfigMenusArePlatformOnly() {
        // 全局唯一 / 全租户共享: 租户改了会串到别的租户
        assertTrue(PlatformMenuGuard.isPlatformOnly("system/Basis"));
        assertTrue(PlatformMenuGuard.isPlatformOnly("system/Dictionary"));
        assertTrue(PlatformMenuGuard.isPlatformOnly("system/Relationship"));
        assertTrue(PlatformMenuGuard.isPlatformOnly("device/Category"));
    }

    @Test
    void infrastructureMenusArePlatformOnly() {
        // 触及宿主机资源或在平台内执行代码
        assertTrue(PlatformMenuGuard.isPlatformOnly("link/Protocol"));
        assertTrue(PlatformMenuGuard.isPlatformOnly("link/Type"));
        assertTrue(PlatformMenuGuard.isPlatformOnly("link/plugin"));
        // 运维仪表盘整页是宿主机 CPU/内存/JVM 指标，与租户业务无关
        assertTrue(PlatformMenuGuard.isPlatformOnly("link/DashBoard"));
    }

    @Test
    void tenantOwnedMenusStayAvailable() {
        // 这些实体已按租户隔离, 租户应当保留管理入口; 误判为平台专属会让租户没法自管
        for (String code : new String[]{
            "device/Instance", "device/Product", "link/AccessConfig", "link/Certificate",
            "notice/Config", "notice/Template", "rule-engine/Scene",
            "system/User", "system/Role", "system/Department",
            "system/NoticeRule", "system/Log/Access", "account/Subscription",
            "device/DashBoard", "rule-engine/DashBoard"}) {
            assertFalse(PlatformMenuGuard.isPlatformOnly(code), code + " 应对租户开放");
        }
    }

    @Test
    void groupMenuMustBeListedToo() {
        // 分组菜单本身也要在清单里：它的 scope 同样会被表单保存/全量同步冲掉，
        // 漏掉它虽不至于泄漏（分组可见性由子菜单决定），但会让「手滑把父节点
        // 授给租户角色」这道防线失效
        assertTrue(PlatformMenuGuard.isPlatformOnly("tenant-ops"));
    }

    @Test
    void tenantOnlyMenusRecognized() {
        assertTrue(PlatformMenuGuard.isTenantOnly("account/Subscription"));
        assertFalse(PlatformMenuGuard.isTenantOnly("system/Tenant"));
        assertFalse(PlatformMenuGuard.isTenantOnly(null));
    }

    @Test
    void twoScopeListsMustNotOverlap() {
        // 同一个 code 既平台专属又租户专属是矛盾的；
        // TenantMenuScopeListener 按先后顺序判定，重叠会让后者永远不生效
        for (String code : PlatformMenuGuard.tenantOnlyMenus()) {
            assertFalse(PlatformMenuGuard.isPlatformOnly(code),
                        code + " 同时出现在平台专属与租户专属清单中");
        }
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
