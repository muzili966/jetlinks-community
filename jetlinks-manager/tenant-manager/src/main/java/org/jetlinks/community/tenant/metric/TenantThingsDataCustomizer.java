package org.jetlinks.community.tenant.metric;

import lombok.AllArgsConstructor;
import org.jetlinks.community.tenant.cache.ProductTenantCache;
import org.jetlinks.community.things.data.ThingsDataContext;
import org.jetlinks.community.things.data.ThingsDataCustomizer;
import org.jetlinks.community.things.data.operations.MetricBuilder;

import javax.annotation.Nonnull;

/**
 * 时序数据按租户下沉(方案B): 给设备时序表名添加租户前缀,
 * 实现物理隔离 + 按租户的保留策略/配额/整租户清理.
 * <p>
 * 表名形如 {@code t{tenantId}_device_properties_{productId}}.
 * MetricBuilder 只能拿到 templateId(productId), 租户从预热缓存反查.
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
public class TenantThingsDataCustomizer implements ThingsDataCustomizer {

    private static final String DEVICE_THING_TYPE = "device";

    private final ProductTenantCache cache;

    @Override
    public void custom(ThingsDataContext context) {
        context.customMetricBuilder(DEVICE_THING_TYPE, new TenantMetricBuilder(cache));
    }

    @AllArgsConstructor
    static class TenantMetricBuilder implements MetricBuilder {

        private final ProductTenantCache cache;

        private String prefix(String templateId) {
            // 缓存未命中落入 unknown 隔离区, 而不是别的租户的表
            return "t" + cache.get(templateId) + "_";
        }

        @Override
        public String createLogMetric(@Nonnull String thingType,
                                      @Nonnull String thingTemplateId,
                                      String thingId) {
            return prefix(thingTemplateId) + MetricBuilder.DEFAULT.createLogMetric(thingType, thingTemplateId, thingId);
        }

        @Override
        public String createPropertyMetric(@Nonnull String thingType,
                                           @Nonnull String thingTemplateId,
                                           String thingId) {
            return prefix(thingTemplateId) + MetricBuilder.DEFAULT.createPropertyMetric(thingType, thingTemplateId, thingId);
        }

        @Override
        public String createPropertyMetric(@Nonnull String thingType,
                                           @Nonnull String thingTemplateId,
                                           String thingId,
                                           String group) {
            return prefix(thingTemplateId) + MetricBuilder.DEFAULT.createPropertyMetric(thingType, thingTemplateId, thingId, group);
        }

        @Override
        public String createEventAllInOneMetric(@Nonnull String thingType,
                                                @Nonnull String thingTemplateId,
                                                String thingId) {
            return prefix(thingTemplateId) + MetricBuilder.DEFAULT.createEventAllInOneMetric(thingType, thingTemplateId, thingId);
        }

        @Override
        public String createEventMetric(@Nonnull String thingType,
                                        @Nonnull String thingTemplateId,
                                        String thingId,
                                        @Nonnull String eventId) {
            return prefix(thingTemplateId) + MetricBuilder.DEFAULT.createEventMetric(thingType, thingTemplateId, thingId, eventId);
        }
    }
}
