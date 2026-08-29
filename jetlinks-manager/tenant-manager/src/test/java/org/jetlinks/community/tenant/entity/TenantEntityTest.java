package org.jetlinks.community.tenant.entity;

import org.jetlinks.community.tenant.TenantConstants;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TenantEntityTest {

    @Test
    void quotaAbsentWhenNotConfigured() {
        TenantEntity tenant = new TenantEntity();

        assertTrue(tenant.getQuota(TenantConstants.QUOTA_MAX_DEVICE).isEmpty());

        tenant.setQuota(Collections.emptyMap());
        assertTrue(tenant.getQuota(TenantConstants.QUOTA_MAX_DEVICE).isEmpty());
    }

    @Test
    void quotaParsesNumberAndString() {
        TenantEntity tenant = new TenantEntity();
        Map<String, Object> quota = new HashMap<>();
        quota.put(TenantConstants.QUOTA_MAX_DEVICE, 1000);
        tenant.setQuota(quota);
        assertEquals(1000L, tenant.getQuota(TenantConstants.QUOTA_MAX_DEVICE).orElse(0L));

        // JSON反序列化可能得到字符串
        quota.put(TenantConstants.QUOTA_MAX_DEVICE, "2000");
        assertEquals(2000L, tenant.getQuota(TenantConstants.QUOTA_MAX_DEVICE).orElse(0L));
    }

    @Test
    void invalidQuotaValueThrows() {
        TenantEntity tenant = new TenantEntity();
        tenant.setQuota(Collections.singletonMap(TenantConstants.QUOTA_MAX_DEVICE, "abc"));

        assertThrows(NumberFormatException.class,
                     () -> tenant.getQuota(TenantConstants.QUOTA_MAX_DEVICE));
    }

    @Test
    void subscribeExpiration() {
        TenantEntity tenant = new TenantEntity();
        // 未设置到期时间 = 不限期
        assertFalse(tenant.isSubscribeExpired());

        tenant.setSubscribeExpireTime(System.currentTimeMillis() + 60_000);
        assertFalse(tenant.isSubscribeExpired());

        tenant.setSubscribeExpireTime(System.currentTimeMillis() - 1);
        assertTrue(tenant.isSubscribeExpired());
    }
}
