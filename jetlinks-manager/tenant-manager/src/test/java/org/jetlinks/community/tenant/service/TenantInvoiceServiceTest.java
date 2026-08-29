package org.jetlinks.community.tenant.service;

import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantInvoiceServiceTest {

    private TenantOrderEntity order(Long amount) {
        TenantOrderEntity order = new TenantOrderEntity();
        order.setTotalAmount(amount);
        return order;
    }

    @Test
    void totalSumsAllOrders() {
        assertEquals(5400L, TenantInvoiceService.computeTotal(
            Arrays.asList(order(1800L), order(3600L))));
    }

    @Test
    void nullAmountCountsAsZero() {
        assertEquals(1800L, TenantInvoiceService.computeTotal(
            Arrays.asList(order(1800L), order(null))));
    }

    @Test
    void emptyOrdersIsZero() {
        assertEquals(0L, TenantInvoiceService.computeTotal(Collections.emptyList()));
    }
}
