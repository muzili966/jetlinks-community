package org.jetlinks.community.tenant.timeseries;

import lombok.AllArgsConstructor;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.timeseries.TimeSeriesManager;
import org.jetlinks.community.timeseries.TimeSeriesMetadata;
import org.jetlinks.community.timeseries.TimeSeriesMetric;
import org.jetlinks.community.timeseries.TimeSeriesService;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;

/**
 * 时序数据的租户隔离入口。
 * <p>
 * 平台日志(access_logger/system_logger)不走 easy-orm, 而是经 {@link TimeSeriesManager}
 * 读写, 行级隔离监听器管不到——真机现象: 租户用户在日志页能看到全平台访问记录。
 * 因此按 metric 维度在此处套一层查询过滤:
 * <ul>
 *   <li>access_logger: 按写入时打的 tenantId 标过滤(见 AccessLoggingTranslator)</li>
 *   <li>system_logger: 平台运行日志与租户无关, 租户查询一律返回空</li>
 * </ul>
 * 设备时序数据不经此隔离——已由 ThingsDataCustomizer 按租户前缀分表。
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
public class TenantTimeSeriesManager implements TimeSeriesManager {

    /** 查询时按 tenantId 列过滤的 metric */
    static final Set<String> TENANT_TAGGED = new HashSet<>(Arrays.asList("access_logger"));

    /** 仅平台可见的 metric: 租户查询返回空 */
    static final Set<String> PLATFORM_ONLY = new HashSet<>(Arrays.asList("system_logger"));

    private final TimeSeriesManager delegate;
    private final TenantProperties properties;

    private TimeSeriesService wrap(String metricId, TimeSeriesService service) {
        if (!properties.isEnabled()) {
            return service;
        }
        if (PLATFORM_ONLY.contains(metricId)) {
            return new TenantTimeSeriesService(service, true, properties);
        }
        if (TENANT_TAGGED.contains(metricId)) {
            return new TenantTimeSeriesService(service, false, properties);
        }
        return service;
    }

    @Override
    public TimeSeriesService getService(TimeSeriesMetric metric) {
        return wrap(metric.getId(), delegate.getService(metric));
    }

    @Override
    public TimeSeriesService getService(String metric) {
        return wrap(metric, delegate.getService(metric));
    }

    @Override
    public TimeSeriesService getServices(TimeSeriesMetric... metric) {
        return wrapMulti(delegate.getServices(metric),
                         Arrays.stream(metric).map(TimeSeriesMetric::getId).toArray(String[]::new));
    }

    @Override
    public TimeSeriesService getServices(String... metric) {
        return wrapMulti(delegate.getServices(metric), metric);
    }

    /**
     * 跨 metric 联合查询: 只要包含受限 metric, 就按最严格的一档处理。
     */
    private TimeSeriesService wrapMulti(TimeSeriesService service, String[] metrics) {
        if (!properties.isEnabled()) {
            return service;
        }
        boolean platformOnly = Arrays.stream(metrics).anyMatch(PLATFORM_ONLY::contains);
        boolean tagged = Arrays.stream(metrics).anyMatch(TENANT_TAGGED::contains);
        if (platformOnly) {
            return new TenantTimeSeriesService(service, true, properties);
        }
        if (tagged) {
            return new TenantTimeSeriesService(service, false, properties);
        }
        return service;
    }

    @Override
    public Mono<Void> registerMetadata(TimeSeriesMetadata metadata) {
        return delegate.registerMetadata(metadata);
    }
}
