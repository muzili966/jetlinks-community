package org.jetlinks.community.tenant.service;

import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.jetlinks.community.tenant.entity.TenantPlanEntity;
import org.jetlinks.community.tenant.enums.TenantOrderStatus;
import org.jetlinks.community.tenant.service.request.TenantSubscribeRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantOrderPendingTest {

    private TenantEntity tenant(String planId) {
        TenantEntity tenant = new TenantEntity();
        tenant.setId("t1");
        tenant.setName("天马科技");
        tenant.setPlanId(planId);
        return tenant;
    }

    private TenantPlanEntity plan(String id, Long monthlyPrice) {
        TenantPlanEntity plan = new TenantPlanEntity();
        plan.setId(id);
        plan.setName("标准版");
        plan.setMonthlyPrice(monthlyPrice);
        return plan;
    }

    private TenantSubscribeRequest months(int months) {
        TenantSubscribeRequest request = new TenantSubscribeRequest();
        request.setTenantId("t1");
        request.setPlanId("p1");
        request.setMonths(months);
        request.setPayChannel(null);
        return request;
    }

    @Test
    void pendingOrderDoesNotTouchSubscription() {
        TenantOrderEntity order = TenantOrderService.buildPendingOrder(tenant("p1"), plan("p1", 1800L), months(3));
        assertNotNull(order.getId(), "支付单要用订单号做业务单号, 必须先生成");
        assertEquals(TenantOrderStatus.pending, order.getStatus());
        assertEquals(5400L, order.getTotalAmount());
        assertEquals("天马科技", order.getTenantName());
        assertNull(order.getPayTime());
        assertNull(order.getPayChannel());
        assertNull(order.getExpireTimeAfter(), "到账前不能写到期时间, 否则没付钱也显示已续上");
    }

    @Test
    void orderTypeReflectsPlanChange() {
        assertEquals(TenantOrderService.ORDER_TYPE_RENEW,
                     TenantOrderService.buildPendingOrder(tenant("p1"), plan("p1", 1800L), months(1)).getOrderType());
        assertEquals(TenantOrderService.ORDER_TYPE_CHANGE,
                     TenantOrderService.buildPendingOrder(tenant("p1"), plan("p2", 3600L), months(1)).getOrderType());
        assertEquals(TenantOrderService.ORDER_TYPE_SUBSCRIBE,
                     TenantOrderService.buildPendingOrder(tenant(null), plan("p1", 1800L), months(1)).getOrderType());
    }

    @Test
    void freePlanNeedsNoPayment() {
        assertTrue(TenantOrderService.isFreePlan(plan("free", null)));
        assertTrue(TenantOrderService.isFreePlan(plan("free", 0L)));
        assertFalse(TenantOrderService.isFreePlan(plan("p1", 1800L)));
    }
}
