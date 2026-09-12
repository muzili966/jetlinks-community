package org.jetlinks.community.tenant.ext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.ezorm.rdb.mapping.annotation.DefaultValue;
import org.jetlinks.community.auth.entity.MenuEntity;

import javax.persistence.Column;

/**
 * 菜单的租户扩展：新增 scope 字段区分平台菜单与租户菜单。
 * <p>
 * 菜单表本身<strong>不做租户隔离</strong>（平台与租户共用同一套菜单定义），
 * 这里加的是「归属域」标记，用于：
 * <ul>
 *     <li>授权时校验：平台菜单不得授予租户角色</li>
 *     <li>平台端菜单管理：按域筛选、维护</li>
 * </ul>
 * 取代此前 {@code PlatformMenuGuard} 里的硬编码 code 清单——新增平台功能时
 * 只需在菜单管理里勾选归属，无需改代码。
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@Setter
public class TenantMenuEntity extends MenuEntity {

    /**
     * 菜单归属域。
     * <ul>
     *     <li>{@code tenant}（默认）：租户功能，平台与租户均可见</li>
     *     <li>{@code platform}：平台专属，仅平台管理员可授权与访问</li>
     *     <li>{@code tenant-only}：租户专属，平台管理员不展示</li>
     * </ul>
     * <p>
     * 本字段易被无意覆盖（表单不含该字段 + {@code @DefaultValue} + 全量同步先删后建），
     * 因此 platform / tenant-only 两类由 {@code TenantMenuScopeListener} 按代码清单
     * 强制回填，不依赖库里存的值。
     * <ul>
     * </ul>
     */
    @Column(length = 16)
    @DefaultValue("tenant")
    @Schema(description = "菜单归属域: tenant(租户功能, 默认) / platform(平台专属)")
    private String scope;

    public boolean isPlatformScope() {
        return SCOPE_PLATFORM.equals(scope);
    }

    public static final String SCOPE_PLATFORM = "platform";
    public static final String SCOPE_TENANT = "tenant";
    /** 租户专属：平台管理员不展示（平台账号点进去后端会 404） */
    public static final String SCOPE_TENANT_ONLY = "tenant-only";
}
