package org.jetlinks.community.tenant.metric;

import org.jetlinks.community.tenant.cache.ProductTenantCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;

class TenantMetricBuilderTest {

    private static final String TENANT_A = "t001";
    private static final String PRODUCT = "p1";
    private static final String THING_TYPE = "device";

    private TenantThingsDataCustomizer.TenantMetricBuilder builder;

    @BeforeEach
    void setup() {
        ProductTenantCache cache = Mockito.mock(ProductTenantCache.class);
        Mockito.when(cache.get(anyString())).thenReturn("unknown");
        Mockito.when(cache.get(PRODUCT)).thenReturn(TENANT_A);
        builder = new TenantThingsDataCustomizer.TenantMetricBuilder(cache);
    }

    @Test
    void propertyMetricPrefixed() {
        assertEquals("t" + TENANT_A + "_device_properties_" + PRODUCT,
                     builder.createPropertyMetric(THING_TYPE, PRODUCT, "dev1"));
    }

    @Test
    void logMetricPrefixed() {
        assertEquals("t" + TENANT_A + "_device_log_" + PRODUCT,
                     builder.createLogMetric(THING_TYPE, PRODUCT, "dev1"));
    }

    @Test
    void eventMetricPrefixed() {
        assertEquals("t" + TENANT_A + "_device_event_" + PRODUCT + "_fire",
                     builder.createEventMetric(THING_TYPE, PRODUCT, "dev1", "fire"));
    }

    @Test
    void unknownProductFallsIntoQuarantine() {
        // 缓存未命中落入 unknown 隔离区, 而不是别的租户的表
        assertEquals("tunknown_device_properties_p404",
                     builder.createPropertyMetric(THING_TYPE, "p404", "dev1"));
    }
}
