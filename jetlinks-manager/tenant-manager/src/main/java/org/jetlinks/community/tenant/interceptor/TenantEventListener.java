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

    /**
     * 认证从哪里来：只认 {@link org.jetlinks.community.tenant.web.TenantAuthContextFilter}
     * 预先写进 Reactor Context 的那一份，本监听器绝不自己去解析。
     * <p>
     * 早先的实现调 {@code Authentication.currentReactive()} 现取，踩了两个坑：
     * <ol>
     *     <li>认证缓存为空时它会就地装配认证，而装配又要查角色/组织——那些查询回到本监听器，
     *         再次触发装配，指数级放大直到 r2dbc 请求队列打满、服务假死；</li>
     *     <li>加了防重入标记挡住递归之后，装配期<strong>最外层</strong>那次角色查询依然被注入了
     *         租户条件，把 {@code tenant-user} 这类 tenant_id 为 NULL 的全租户共享角色过滤掉，
     *         导致 role 维度丢失、租户端菜单全空且所有接口 403。</li>
     * </ol>
     * 两个坑同源：<strong>不该在查询拦截器里触发认证装配</strong>。
     * 改为只读上下文后，装配链路天然读不到认证，也就不会被注入。
     */


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
        // Reactive 场景: 只读 Reactor Context 里已解析好的认证, 绝不主动触发装配
        context
            .get(MappingContextKeys.reactiveResultHolder)
            .ifPresent(holder -> holder.before(
                Mono.deferContextual(ctxView -> {
                    Authentication auth = ctxView.getOrDefault(Authentication.class, null);
                    if (auth == null) {
                        // 认证装配链路与系统内部链路(设备上行/定时任务)走这里: 不注入
                        return Mono.empty();
                    }
                    apply(type, context, auth, ctxView);
                    return Mono.empty();
                })
            ));
    }

    private boolean isSupported(EventType type) {
        return type == MappingEventTypes.select_before
            || type == MappingEventTypes.insert_before
            || type == MappingEventTypes.save_before
            || type == MappingEventTypes.update_before
            || type == MappingEventTypes.delete_before;
    }

    /**
     * 判断该实体是否参与租户隔离。
     * <p>
     * 依据是<strong>表里是否存在 tenant_id 列</strong>，而不是实体类是否实现
     * {@link TenantAware}。真机诊断发现 {@code columnMapping.getEntityType()}
     * 返回的是<strong>原始实体类</strong>（如 DeviceInstanceEntity），而非实体工厂
     * 映射后的租户子类，导致按类型判断永远为 false，监听器在第一道关卡就 return，
     * 隔离全面失效。
     * <p>
     * 列元数据由 AutoDDL 依据租户子类生成，是确定的事实，不受工厂映射解析时机影响。
     */
    private boolean isTenantAwareEntity(EventContext context) {
        Optional<EntityColumnMapping> mapping = context.get(MappingContextKeys.columnMapping);
        boolean hasTenantColumn = mapping
            .flatMap(m -> m.getColumnByProperty(TenantConstants.TENANT_ID_PROPERTY))
            .isPresent();
        if (log.isDebugEnabled()) {
            log.debug("tenant-diag entity={} hasTenantColumn={}",
                      mapping.map(m -> m.getEntityType().getSimpleName()).orElse("(无 columnMapping)"),
                      hasTenantColumn);
        }
        return hasTenantColumn;
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
            // 必须改 queryOaram(QueryParam)：hsweb 自身的 EntityEventListener 也是操作它。
            // 早前只改 MappingContextKeys.query(QueryOperator) 无效——在 reactiveResultHolder
            // 的 before 回调里 QueryOperator 已构建完毕，条件进不了最终 SQL（真机实测：
            // 对 admin 注入 __NO_TENANT__ 仍能查到全部数据）。
            boolean byParam = ctx.get(MappingContextKeys.queryOaram).isPresent();
            ctx.get(MappingContextKeys.queryOaram)
               .ifPresent(param -> param.and(TenantConstants.TENANT_ID_PROPERTY, "eq", tenantId));
            // 兜底：部分路径不经 QueryParam，仍尝试 QueryOperator
            boolean byOperator = ctx.get(MappingContextKeys.query).isPresent();
            ctx.get(MappingContextKeys.query)
               .ifPresent(q -> q.where(c -> c.is(TenantConstants.TENANT_ID_PROPERTY, tenantId)));
            if (log.isDebugEnabled()) {
                log.debug("tenant-diag select tenant={} queryParam={} queryOperator={}",
                          tenantId, byParam, byOperator);
            }
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
