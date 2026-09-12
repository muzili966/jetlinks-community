package org.jetlinks.community.tenant.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantBillingServiceTest {

    @Test
    void monthStartIsFirstDayMidnight() {
        YearMonth ym = YearMonth.of(2026, 8);
        long expected = LocalDate.of(2026, 8, 1)
                                 .atStartOfDay(ZoneId.systemDefault())
                                 .toInstant()
                                 .toEpochMilli();

        assertEquals(expected, TenantBillingService.monthStartMillis(ym));
    }

    @Test
    void monthStartExcludesPreviousMonth() {
        long augStart = TenantBillingService.monthStartMillis(YearMonth.of(2026, 8));
        long julLast = LocalDate.of(2026, 7, 31)
                                .atTime(23, 59, 59)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli();

        // 7月最后一刻不应计入8月，否则「本月收入」会把上月尾单算进来
        assertTrue(julLast < augStart);
    }

    @Test
    void refundWindowIs14Days() {
        long now = System.currentTimeMillis();
        long day = 24L * 3600 * 1000;

        // 刚支付、第13天：可退
        assertTrue(!TenantOrderService.isOutOfRefundWindow(now, now));
        assertTrue(!TenantOrderService.isOutOfRefundWindow(now - 13 * day, now));
        // 第15天：超窗
        assertTrue(TenantOrderService.isOutOfRefundWindow(now - 15 * day, now));
        // 支付时间缺失：按不可退处理，避免无据可查的退款
        assertTrue(TenantOrderService.isOutOfRefundWindow(null, now));
    }

    @Test
    void orderRemarkAppend() {
        assertEquals("退款: 客户取消", TenantOrderService.appendRemark(null, "退款: 客户取消"));
        assertEquals("退款: 客户取消", TenantOrderService.appendRemark("  ", "退款: 客户取消"));
        assertEquals("原备注 | 退款: 客户取消",
                     TenantOrderService.appendRemark("原备注", "退款: 客户取消"));
    }
}
