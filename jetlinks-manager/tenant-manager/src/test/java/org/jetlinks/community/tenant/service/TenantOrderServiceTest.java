package org.jetlinks.community.tenant.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantOrderServiceTest {

    private static final long NOW = ZonedDateTime
        .of(2026, 8, 29, 12, 0, 0, 0, ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli();

    private long plusMonths(long base, int months) {
        return ZonedDateTime
            .ofInstant(Instant.ofEpochMilli(base), ZoneId.systemDefault())
            .plusMonths(months)
            .toInstant()
            .toEpochMilli();
    }

    @Test
    void renewBeforeExpireExtendsFromExpireTime() {
        // 未到期续费: 余期不损失, 从原到期时间顺延
        long currentExpire = NOW + 86400_000L * 10;

        assertEquals(plusMonths(currentExpire, 3),
                     TenantOrderService.computeExpireAfter(currentExpire, 3, NOW));
    }

    @Test
    void renewAfterExpireExtendsFromNow() {
        // 已到期续费: 从当前时间起算, 不为过期时段补时
        long expired = NOW - 1;

        assertEquals(plusMonths(NOW, 1),
                     TenantOrderService.computeExpireAfter(expired, 1, NOW));
    }

    @Test
    void firstSubscribeExtendsFromNow() {
        assertEquals(plusMonths(NOW, 12),
                     TenantOrderService.computeExpireAfter(null, 12, NOW));
    }

    @Test
    void amountIsMonthlyPriceTimesMonths() {
        assertEquals(5400L, TenantOrderService.computeAmount(1800L, 3));
        assertEquals(0L, TenantOrderService.computeAmount(0L, 12));
        assertEquals(0L, TenantOrderService.computeAmount(null, 5));
    }

    @Test
    void orderTypeResolution() {
        assertEquals(TenantOrderService.ORDER_TYPE_SUBSCRIBE,
                     TenantOrderService.resolveOrderType(null, "standard"));
        assertEquals(TenantOrderService.ORDER_TYPE_RENEW,
                     TenantOrderService.resolveOrderType("standard", "standard"));
        assertEquals(TenantOrderService.ORDER_TYPE_CHANGE,
                     TenantOrderService.resolveOrderType("standard", "ultimate"));
    }
}
