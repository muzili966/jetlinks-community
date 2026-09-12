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
     * 平台专属菜单 code 的兜底清单。
     * <p>
     * 判定标准是「租户操作它会不会影响别的租户或平台本身」，而非功能重要性：
     * 全局唯一配置、全租户共享的字典/分类、以及能触及宿主机资源(端口)或
     * 在平台内执行代码(协议包/插件)的功能，都不能交给租户。
     * <p>
     * 注意这里管的是<strong>管理入口</strong>：租户仍可读取这些数据
     * (建产品时选分类、选协议)，只是不能增删改。
     * <p>
     * 首选依据是菜单表的 {@code scope} 字段（见 {@code TenantMenuEntity}）——
     * 新增平台功能时在菜单管理里勾选归属即可，无需改代码。
     * 本清单仅用于 scope 尚未设置（历史数据、导入的菜单）时的兜底判定。
     */
    private static final List<String> PLATFORM_ONLY_MENUS = Arrays.asList(
        // 租户与计费
        "tenant-ops",            // 租户运营(分组，下辖以下四项)
        "system/Tenant",         // 租户管理
        "system/TenantPlan",     // 订阅套餐
        "system/TenantOrder",    // 订单流水
        "system/TenantInvoice",  // 发票管理
        // 授权体系(授予租户即构成提权)
        "system/Menu",           // 菜单管理
        "system/Permission",     // 权限管理
        "system/Platforms",      // 平台接入配置
        // 全局配置: 单实例唯一或全租户共享, 租户修改会影响其他租户
        "system/Basis",          // 基础配置(系统名称/LOGO/前端地址)
        "system/Dictionary",     // 数据字典(全局枚举)
        "system/Relationship",   // 关系配置(全局元数据定义)
        "device/Category",       // 产品分类(全局分类树)
        // 平台基础设施: 涉及宿主机资源或平台代码执行
        "link/Protocol",         // 协议管理(上传 jar/脚本 = 平台内执行任意代码)
        "link/Type",             // 网络组件(占用平台端口)
        "link/plugin",           // 插件管理(平台级扩展)
        "link/DashBoard"         // 运维仪表盘(整页为宿主机 CPU/内存/JVM 指标)
    );

    /**
     * 租户专属菜单：平台管理员不该看到（它们对平台账号无意义，点进去后端会 404）。
     * <p>
     * 与 {@link #PLATFORM_ONLY_MENUS} 互为镜像，同样是代码托管——原因见
     * {@code TenantMenuScopeListener}。
     */
    private static final List<String> TENANT_ONLY_MENUS = Arrays.asList(
        "account/Subscription"   // 我的订阅(租户自助)
    );

    private PlatformMenuGuard() {
    }

    public static boolean isTenantOnly(String menuCode) {
        return menuCode != null && TENANT_ONLY_MENUS.contains(menuCode);
    }

    public static List<String> tenantOnlyMenus() {
        return TENANT_ONLY_MENUS;
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
