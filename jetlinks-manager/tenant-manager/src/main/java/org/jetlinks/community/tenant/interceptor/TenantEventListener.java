package org.jetlinks.community.tenant.interceptor;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.rdb.events.EventContext;
import org.hswebframework.ezorm.rdb.events.EventListener;
import org.hswebframework.ezorm.rdb.events.EventType;
import org.hswebframework.ezorm.rdb.mapping.EntityColumnMapping;
import org.hswebframework.ezorm.rdb.mapping.events.MappingContextKeys;
import org.hswebframework.ezorm.rdb.mapping.events.MappingEventTypes;
import org.hswebframework.web.authorization.Authentication;
import org.jetlinks.community.tenant.TenantAware;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.context.TenantContext;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 租户数据隔离核心: 挂载到 easy-orm 全局事件, 对实现 {@link TenantAware} 的实体
 * 自动注入查询/更新/删除条件与写入租户ID.
 * <p>
 * 与 {@code CreatorEventListener} 同机制. 无认证信息的系统内部链路(设备上行/规则引擎)
 * 不做注入, 由链路自身显式携带租户(见文档 §4.4).
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
@AllArgsConstructor
public class TenantEventListener implements EventListener, Ordered {

    private final TenantProperties properties;

    @Override
    public String getId() {
        return "tenant-isolation";
    }

    @Override
    public String getName() {
        return "租户数据隔离";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }

    @Override
    public void onEvent(EventType type, EventContext context) {
        if (!properties.isEnabled() || !isSupported(type) || !isTenantAwareEntity(context)) {
            return;
        }
        // 同步场景: ThreadLocal 已有认证
        Optional<Authentication> current = Authentication.current();
        if (current.isPresent()) {
            apply(type, context, current.get(), Context.empty());
            return;
        }
        // Reactive 场景: 挂前置 Mono, 从 Reactor Context 取认证与代理租户
        context
            .get(MappingContextKeys.reactiveResultHolder)
            .ifPresent(holder -> holder.before(
                Mono.deferContextual(ctxView -> Authentication
                    .currentReactive()
                    .doOnNext(auth -> apply(type, context, auth, ctxView))
                    .then())
            ));
    }

    private boolean isSupported(EventType type) {
        return type == MappingEventTypes.select_before
            || type == MappingEventTypes.insert_before
            || type == MappingEventTypes.save_before
            || type == MappingEventTypes.update_before
            || type == MappingEventTypes.delete_before;
    }

    private boolean isTenantAwareEntity(EventContext context) {
        return context
            .get(MappingContextKeys.columnMapping)
            .map(EntityColumnMapping::getEntityType)
            .map(TenantAware.class::isAssignableFrom)
            .orElse(false);
    }

    private void apply(EventType type,
                       EventContext ctx,
                       Authentication auth,
                       ContextView contextView) {
        TenantContext.Resolution resolution =
            TenantContext.resolve(auth, contextView, properties.getPlatformAdminRoleId());
        if (resolution.isPlatformBypass()) {
            return;
        }
        // fail-closed: 无租户维度时注入不存在的租户号, 查不到任何数据
        String tenantId = resolution.getTenantId().orElse(TenantConstants.NO_TENANT);
        if (resolution.isMissing()) {
            log.warn("user [{}] has no tenant dimension, apply fail-closed filter", auth.getUser().getId());
        }
        applyTenant(type, ctx, tenantId);
    }

    private void applyTenant(EventType type, EventContext ctx, String tenantId) {
        if (type == MappingEventTypes.select_before) {
            ctx.get(MappingContextKeys.query)
               .ifPresent(q -> q.where(c -> c.is(TenantConstants.TENANT_ID_PROPERTY, tenantId)));
        } else if (type == MappingEventTypes.update_before) {
            ctx.get(MappingContextKeys.update)
               .ifPresent(u -> u.where(c -> c.is(TenantConstants.TENANT_ID_PROPERTY, tenantId)));
            // 防篡改: 更新语句不允许修改租户归属
            ctx.get(MappingContextKeys.updateColumnInstance)
               .ifPresent(columns -> columns.remove(TenantConstants.TENANT_ID_PROPERTY));
        } else if (type == MappingEventTypes.delete_before) {
            ctx.get(MappingContextKeys.delete)
               .ifPresent(d -> d.where(c -> c.is(TenantConstants.TENANT_ID_PROPERTY, tenantId)));
        } else {
            // insert_before / save_before
            ctx.get(MappingContextKeys.instance)
               .ifPresent(instance -> fillTenantId(instance, tenantId));
        }
    }

    private void fillTenantId(Object instance, String tenantId) {
        if (instance instanceof Collection) {
            for (Object item : ((Collection<?>) instance)) {
                fillTenantId(item, tenantId);
            }
            return;
        }
        if (instance instanceof TenantAware) {
            ((TenantAware) instance).setTenantId(tenantId);
            return;
        }
        if (instance instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) instance;
            map.put(TenantConstants.TENANT_ID_PROPERTY, tenantId);
        }
    }
}
