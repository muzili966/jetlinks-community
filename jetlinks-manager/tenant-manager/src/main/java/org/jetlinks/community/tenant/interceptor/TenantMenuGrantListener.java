package org.jetlinks.community.tenant.interceptor;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.rdb.events.EventContext;
import org.hswebframework.ezorm.rdb.events.EventListener;
import org.hswebframework.ezorm.rdb.events.EventType;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.ezorm.rdb.mapping.events.MappingContextKeys;
import org.hswebframework.ezorm.rdb.mapping.events.MappingEventTypes;
import org.hswebframework.web.authorization.Authentication;
import org.jetlinks.community.auth.entity.MenuBindEntity;
import org.jetlinks.community.auth.entity.MenuEntity;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.context.TenantContext;
import org.jetlinks.community.tenant.ext.TenantMenuEntity;
import org.jetlinks.community.tenant.role.PlatformMenuGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单授权的持久层防线: 租户提交的授权里剔除平台专属菜单。
 * <p>
 * {@link org.jetlinks.community.tenant.role.TenantGrantGuard} 在 HTTP 层校验
 * 角色归属后放行 /menu/role/{id}/_grant, 但授权内容(菜单清单)在请求体里,
 * WebFilter 不便解析; 在 s_menu_bind 落库前把关, 无论走哪个入口都逃不掉。
 * <p>
 * 判定依据: 菜单表的 scope 字段(=platform) 或 {@link PlatformMenuGuard} 兜底清单。
 * 平台管理员不受限。
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
@AllArgsConstructor
public class TenantMenuGrantListener implements EventListener, Ordered {

    private final TenantProperties properties;

    /**
     * ezorm EventListener 在 databaseMetadata 构建期就被收集, 此时 Repository
     * 尚未就绪, 直接注入会形成循环依赖(真机启动失败)。改为事件触发时惰性获取。
     */
    private final ObjectProvider<ReactiveRepository<MenuEntity, String>> menuRepository;

    @Override
    public String getId() {
        return "tenant-menu-grant-guard";
    }

    @Override
    public String getName() {
        return "租户菜单授权防线";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 210;
    }

    @Override
    public void onEvent(EventType type, EventContext context) {
        if (!properties.isEnabled()) {
            return;
        }
        if (type != MappingEventTypes.insert_before && type != MappingEventTypes.save_before) {
            return;
        }
        // getEntityType() 返回原始实体类(工厂映射前), 直接与 MenuBindEntity 比对
        boolean isMenuBind = context
            .get(MappingContextKeys.columnMapping)
            .map(m -> MenuBindEntity.class.isAssignableFrom(m.getEntityType()))
            .orElse(false);
        if (!isMenuBind) {
            return;
        }
        context
            .get(MappingContextKeys.reactiveResultHolder)
            .ifPresent(holder -> holder.before(
                Mono.deferContextual(ctxView -> Authentication
                    .currentReactive()
                    .flatMap(auth -> {
                        TenantContext.Resolution resolution = TenantContext
                            .resolve(auth, ctxView, properties.getPlatformAdminRoleId());
                        if (resolution.isPlatformBypass()) {
                            return Mono.empty();
                        }
                        return stripPlatformMenus(context, auth);
                    }))
            ));
    }

    private Mono<Void> stripPlatformMenus(EventContext context, Authentication auth) {
        return context
            .get(MappingContextKeys.instance)
            .map(instance -> {
                Collection<MenuBindEntity> binds = asBinds(instance);
                if (binds == null || binds.isEmpty()) {
                    return Mono.<Void>empty();
                }
                Set<String> menuIds = binds.stream()
                                           .map(MenuBindEntity::getMenuId)
                                           .collect(Collectors.toSet());
                return menuRepository.getObject()
                    .findById(menuIds)
                    .filter(this::isPlatformOnly)
                    .map(MenuEntity::getId)
                    .collect(Collectors.toSet())
                    .doOnNext(denied -> {
                        if (!denied.isEmpty()) {
                            List<String> removed = binds.stream()
                                                        .map(MenuBindEntity::getMenuId)
                                                        .filter(denied::contains)
                                                        .collect(Collectors.toList());
                            binds.removeIf(bind -> denied.contains(bind.getMenuId()));
                            log.warn("tenant user [{}] tried to grant platform-only menus, stripped: {}",
                                     auth.getUser().getId(), removed);
                        }
                    })
                    .then();
            })
            .orElse(Mono.empty());
    }

    @SuppressWarnings("unchecked")
    private Collection<MenuBindEntity> asBinds(Object instance) {
        if (instance instanceof Collection) {
            return (Collection<MenuBindEntity>) instance;
        }
        return null;
    }

    private boolean isPlatformOnly(MenuEntity menu) {
        if (menu instanceof TenantMenuEntity
            && TenantMenuEntity.SCOPE_PLATFORM.equals(((TenantMenuEntity) menu).getScope())) {
            return true;
        }
        return PlatformMenuGuard.isPlatformOnly(menu.getCode());
    }
}
