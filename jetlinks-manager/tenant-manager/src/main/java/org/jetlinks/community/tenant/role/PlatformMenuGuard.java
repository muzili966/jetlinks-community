package org.jetlinks.community.tenant.role;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.tenant.TenantConstants;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 平台专属菜单清单。
 * <p>
 * 这些菜单只应授予平台管理员角色；授予租户角色会让租户用户看到并进入
 * 平台功能（如租户管理、菜单管理），构成越权。
 * <p>
 * 与 {@link TenantGrantGuard} 是两道防线：后者拦 HTTP 接口，本清单
 * 用于授权时校验，避免"菜单能点开但接口报错"的糟糕体验。
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
public final class PlatformMenuGuard {

    /**
     * 平台专属菜单 code。租户角色不得被授予。
     */
    private static final List<String> PLATFORM_ONLY_MENUS = Arrays.asList(
        "system/Tenant",      // 租户管理（含套餐/订单/发票）
        "system/Menu",        // 菜单管理
        "system/Permission",  // 权限管理
        "system/Platforms"    // 平台接入配置
    );

    private PlatformMenuGuard() {
    }

    public static boolean isPlatformOnly(String menuCode) {
        return menuCode != null && PLATFORM_ONLY_MENUS.contains(menuCode);
    }

    /**
     * 从待授权菜单中剔除平台专属项。
     *
     * @param roleId    被授权的角色
     * @param menuCodes 待授权的菜单 code
     * @return 允许授予该角色的菜单 code
     */
    public static Collection<String> filterForRole(String roleId, Collection<String> menuCodes) {
        if (isPlatformRole(roleId)) {
            return menuCodes;
        }
        List<String> denied = menuCodes.stream().filter(PlatformMenuGuard::isPlatformOnly).toList();
        if (!denied.isEmpty()) {
            log.warn("role [{}] is not a platform role, stripped platform-only menus: {}", roleId, denied);
        }
        return menuCodes.stream().filter(code -> !isPlatformOnly(code)).toList();
    }

    /**
     * 平台角色：平台管理员本身。租户管理员（tenant-admin-*）不算。
     */
    public static boolean isPlatformRole(String roleId) {
        return roleId != null
            && !TenantConstants.isTenantAdminRole(roleId)
            && !roleId.startsWith("tenant-");
    }

    public static List<String> platformOnlyMenus() {
        return PLATFORM_ONLY_MENUS;
    }
}
