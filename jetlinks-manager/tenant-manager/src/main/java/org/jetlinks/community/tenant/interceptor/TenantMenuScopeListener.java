package org.jetlinks.community.tenant.interceptor;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.rdb.events.EventContext;
import org.hswebframework.ezorm.rdb.events.EventListener;
import org.hswebframework.ezorm.rdb.events.EventType;
import org.hswebframework.ezorm.rdb.mapping.EntityColumnMapping;
import org.hswebframework.ezorm.rdb.mapping.events.MappingContextKeys;
import org.hswebframework.ezorm.rdb.mapping.events.MappingEventTypes;
import org.jetlinks.community.auth.entity.MenuEntity;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.ext.TenantMenuEntity;
import org.jetlinks.community.tenant.role.PlatformMenuGuard;
import org.springframework.core.Ordered;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 菜单归属域(scope)的写入兜底：按代码清单强制回填，不依赖数据里存的值。
 * <p>
 * 起因是 scope 太容易被无意抹掉：
 * <ul>
 *     <li>{@code TenantMenuEntity.scope} 带 {@code @DefaultValue("tenant")}，
 *         请求体不带该字段时实体层会填上默认值，upsert 的 coalesce 保不住原值——
 *         在菜单管理里改个图标，platform 标记就没了；</li>
 *     <li>「菜单管理 → 同步菜单」走 {@code PATCH /menu/{owner}/_all}，
 *         先删光该 owner 的菜单再重建，连 coalesce 的机会都没有；</li>
 *     <li>菜单编辑表单和 baseMenu.json 里都没有 scope 字段，运维无从察觉。</li>
 * </ul>
 * 真机实测：提交时剥掉 scope 字段，15 个 platform 菜单全部退化成 tenant。
 * <p>
 * 因此把 scope 从「手工维护的数据」改成「代码派生的数据」：清单在代码里，
 * 任何写入路径都会被这里覆盖回来。清单之外的菜单不受影响，仍可在界面上自定义。
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
@AllArgsConstructor
public class TenantMenuScopeListener implements EventListener, Ordered {

    /** 前端据此隐藏租户专属菜单，见 store/menu.ts 的 filterTenantOnly */
    static final String OPTION_TENANT_ONLY = "tenantOnly";

    private final TenantProperties properties;

    @Override
    public String getId() {
        return "tenant-menu-scope";
    }

    @Override
    public String getName() {
        return "菜单归属域回填";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 220;
    }

    @Override
    public void onEvent(EventType type, EventContext context) {
        if (!properties.isEnabled()) {
            return;
        }
        if (type != MappingEventTypes.insert_before && type != MappingEventTypes.save_before) {
            return;
        }
        boolean isMenu = context
            .get(MappingContextKeys.columnMapping)
            .map(EntityColumnMapping::getEntityType)
            .filter(MenuEntity.class::isAssignableFrom)
            .isPresent();
        if (!isMenu) {
            return;
        }
        context.get(MappingContextKeys.instance).ifPresent(this::applyScope);
    }

    private void applyScope(Object instance) {
        if (instance instanceof Collection) {
            for (Object item : ((Collection<?>) instance)) {
                applyScope(item);
            }
            return;
        }
        if (!(instance instanceof TenantMenuEntity)) {
            return;
        }
        TenantMenuEntity menu = (TenantMenuEntity) instance;
        String code = menu.getCode();
        if (PlatformMenuGuard.isPlatformOnly(code)) {
            forceScope(menu, TenantMenuEntity.SCOPE_PLATFORM, false);
        } else if (PlatformMenuGuard.isTenantOnly(code)) {
            forceScope(menu, TenantMenuEntity.SCOPE_TENANT_ONLY, true);
        }
    }

    private void forceScope(TenantMenuEntity menu, String scope, boolean tenantOnlyOption) {
        if (!scope.equals(menu.getScope())) {
            log.debug("restore menu [{}] scope: {} -> {}", menu.getCode(), menu.getScope(), scope);
            menu.setScope(scope);
        }
        // options 同样会被全量覆盖冲掉，一并兜底
        Map<String, Object> options = menu.getOptions() == null
            ? new HashMap<>()
            : new HashMap<>(menu.getOptions());
        if (tenantOnlyOption) {
            options.put(OPTION_TENANT_ONLY, true);
        } else {
            options.remove(OPTION_TENANT_ONLY);
        }
        menu.setOptions(options);
    }
}
