package org.jetlinks.community.tenant.timeseries;

import lombok.AllArgsConstructor;
import org.hswebframework.ezorm.core.param.QueryParam;
import org.hswebframework.web.api.crud.entity.PagerResult;
import org.hswebframework.web.authorization.Authentication;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.context.TenantContext;
import org.jetlinks.community.timeseries.TimeSeriesData;
import org.jetlinks.community.timeseries.TimeSeriesService;
import org.jetlinks.community.timeseries.query.AggregationData;
import org.jetlinks.community.timeseries.query.AggregationQueryParam;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * 受租户隔离约束的时序查询包装。
 * <p>
 * 写入(save/commit)原样放行——日志写入链路无认证上下文, 租户标已在
 * 事件转换器落到数据里; 查询按当前认证注入过滤:
 * 平台管理员与系统内部链路(无认证)不受限。
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
class TenantTimeSeriesService implements TimeSeriesService {

    private final TimeSeriesService delegate;
    /** true: 平台专属, 租户查询一律为空; false: 按 tenantId 列过滤 */
    private final boolean platformOnly;
    private final TenantProperties properties;

    /**
     * 解析当前查询应注入的租户: empty=不过滤(平台/系统链路), NO_TENANT=租户查平台专属数据。
     */
    private Mono<String> resolveTenant() {
        // deferContextual: 让平台管理员的「以租户身份查看」(X-Tenant-Id)对日志同样生效
        return Mono.deferContextual(ctxView -> Authentication
            .currentReactive()
            .flatMap(auth -> {
                TenantContext.Resolution resolution =
                    TenantContext.resolve(auth, ctxView,
                                          properties.getPlatformAdminRoleId());
                if (resolution.isPlatformBypass()) {
                    return Mono.empty();
                }
                if (platformOnly) {
                    // 平台专属数据: 不能靠注入列条件兜底——目标表未必有 tenant_id 列,
                    // ezorm 对未知列的条件会静默丢弃(真机踩坑: 注入了条件仍返回全量)。
                    // 用哨兵值让各查询方法直接返回空, 不发 SQL。
                    return Mono.just(DENY_ALL);
                }
                return Mono.just(resolution.getTenantId().orElse(TenantConstants.NO_TENANT));
            }));
    }

    /** 哨兵: 当前用户对该 metric 无任何可见数据 */
    private static final String DENY_ALL = "__TENANT_DENY_ALL__";

    /**
     * 把「当前是否需要按租户过滤」物化成 Optional 再分支。
     * <p>
     * 绝不能写成 {@code resolveTenant().flatMapMany(..).switchIfEmpty(无过滤查询)}：
     * 租户过滤后结果为空（租户本来就没数据）、或命中平台专属的 DENY_ALL 分支时，
     * 上游都是空流，会把 switchIfEmpty 一并触发，落到<strong>无过滤查询</strong>上
     * ——租户直接看到全平台数据，是数据越权而不只是逻辑瑕疵。
     * <p>
     * empty 表示平台管理员或系统内部链路（无认证），此时才真的不过滤。
     */
    private Mono<Optional<String>> resolveTenantOptional() {
        return resolveTenant()
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Flux<TimeSeriesData> query(QueryParam queryParam) {
        return resolveTenantOptional()
            .flatMapMany(opt -> {
                if (opt.isEmpty()) {
                    return delegate.query(queryParam);
                }
                String tenantId = opt.get();
                return DENY_ALL.equals(tenantId)
                    ? Flux.<TimeSeriesData>empty()
                    : delegate.query(inject(queryParam, tenantId));
            });
    }

    @Override
    public Flux<TimeSeriesData> multiQuery(Collection<QueryParam> query) {
        return resolveTenantOptional()
            .flatMapMany(opt -> {
                if (opt.isEmpty()) {
                    return delegate.multiQuery(query);
                }
                String tenantId = opt.get();
                if (DENY_ALL.equals(tenantId)) {
                    return Flux.<TimeSeriesData>empty();
                }
                query.forEach(p -> inject(p, tenantId));
                return delegate.multiQuery(query);
            });
    }

    @Override
    public Mono<Integer> count(QueryParam queryParam) {
        return resolveTenantOptional()
            .flatMap(opt -> {
                if (opt.isEmpty()) {
                    return delegate.count(queryParam);
                }
                String tenantId = opt.get();
                return DENY_ALL.equals(tenantId)
                    ? Mono.just(0)
                    : delegate.count(inject(queryParam, tenantId));
            });
    }

    @Override
    public <T> Mono<PagerResult<T>> queryPager(QueryParam queryParam, Function<TimeSeriesData, T> mapper) {
        return resolveTenantOptional()
            .flatMap(opt -> {
                if (opt.isEmpty()) {
                    return delegate.queryPager(queryParam, mapper);
                }
                String tenantId = opt.get();
                return DENY_ALL.equals(tenantId)
                    ? Mono.just(PagerResult.<T>empty())
                    : delegate.queryPager(inject(queryParam, tenantId), mapper);
            });
    }

    @Override
    public Flux<AggregationData> aggregation(AggregationQueryParam queryParam) {
        return resolveTenantOptional()
            .flatMapMany(opt -> {
                if (opt.isEmpty()) {
                    return delegate.aggregation(queryParam);
                }
                String tenantId = opt.get();
                if (DENY_ALL.equals(tenantId)) {
                    return Flux.<AggregationData>empty();
                }
                queryParam.getQueryParam().and(TenantConstants.TENANT_ID_PROPERTY, "eq", tenantId);
                return delegate.aggregation(queryParam);
            });
    }

    private QueryParam inject(QueryParam param, String tenantId) {
        param.and(TenantConstants.TENANT_ID_PROPERTY, "eq", tenantId);
        return param;
    }

    @Override
    public Mono<Void> commit(Publisher<TimeSeriesData> data) {
        return delegate.commit(data);
    }

    @Override
    public Mono<Void> commit(TimeSeriesData data) {
        return delegate.commit(data);
    }

    @Override
    public Mono<Void> save(Publisher<TimeSeriesData> data) {
        return delegate.save(data);
    }
}
