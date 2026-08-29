package org.jetlinks.community.tenant.context;

import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.DefaultDimensionType;
import org.hswebframework.web.authorization.Dimension;
import org.jetlinks.community.tenant.TenantDimensionType;
import reactor.util.context.ContextView;

import java.util.Optional;

/**
 * 租户上下文工具: 从认证信息与 Reactor Context 中解析当前生效的租户.
 *
 * @author tenant-manager
 * @since 2.11
 */
public final class TenantContext {

    private TenantContext() {
    }

    /**
     * 用户绑定的租户(取第一个绑定, 一个用户只属于一个租户)
     */
    public static Optional<String> currentTenant(Authentication auth) {
        return auth
            .getDimensions(TenantDimensionType.tenant)
            .stream()
            .findFirst()
            .map(Dimension::getId);
    }

    /**
     * 平台管理员判定。两条独立依据，满足其一即可：
     * <ol>
     *     <li>绑定了平台管理员角色维度</li>
     *     <li>拥有全量权限（{@code *:*}）——JetLinks 的 admin 由
     *         {@code hsweb.authentication.defaults.user.admin} 配置直接授权，
     *         其 Authentication 中并不带角色维度，只靠第 1 条会被误判为
     *         「无租户」而套上 fail-closed（真机实测日志已确认）</li>
     * </ol>
     */
    public static boolean isPlatformAdmin(Authentication auth, String platformAdminRoleId) {
        if (auth.hasDimension(DefaultDimensionType.role, platformAdminRoleId)) {
            return true;
        }
        return auth.hasPermission("*", "*");
    }

    /**
     * 解析实际生效的租户:
     * <ul>
     *     <li>普通用户: 自己绑定的租户, 未绑定时返回empty(由调用方fail-closed)</li>
     *     <li>平台管理员: 显式代理(X-Tenant-Id)时为目标租户, 否则empty表示跨租户放行</li>
     * </ul>
     */
    public static Resolution resolve(Authentication auth,
                                     ContextView contextView,
                                     String platformAdminRoleId) {
        if (isPlatformAdmin(auth, platformAdminRoleId)) {
            return TenantImpersonation
                .from(contextView)
                .map(Resolution::ofTenant)
                .orElse(Resolution.PLATFORM);
        }
        return currentTenant(auth)
            .map(Resolution::ofTenant)
            .orElse(Resolution.MISSING);
    }

    /**
     * 租户解析结果: 三种互斥状态, 由调用方决定各状态的处理策略
     */
    public static final class Resolution {
        static final Resolution PLATFORM = new Resolution(null, true);
        static final Resolution MISSING = new Resolution(null, false);

        private final String tenantId;
        private final boolean platform;

        private Resolution(String tenantId, boolean platform) {
            this.tenantId = tenantId;
            this.platform = platform;
        }

        static Resolution ofTenant(String tenantId) {
            return new Resolution(tenantId, false);
        }

        /**
         * 平台管理员未代理任何租户: 跨租户放行
         */
        public boolean isPlatformBypass() {
            return platform;
        }

        /**
         * 普通用户未绑定租户: 需fail-closed
         */
        public boolean isMissing() {
            return !platform && tenantId == null;
        }

        public Optional<String> getTenantId() {
            return Optional.ofNullable(tenantId);
        }
    }
}
