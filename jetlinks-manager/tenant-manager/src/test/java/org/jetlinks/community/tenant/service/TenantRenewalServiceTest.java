package org.jetlinks.community.tenant.service;

import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.pay.service.request.PayCreateRequest;
import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.jetlinks.community.tenant.service.request.TenantRenewalRequest;
import org.jetlinks.community.tenant.service.request.TenantSubscribeRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantRenewalServiceTest {

    private TenantOrderEntity order(String orderType) {
        TenantOrderEntity order = new TenantOrderEntity();
        order.setId("o1");
        order.setTenantId("t1");
        order.setPlanName("标准版");
        order.setMonths(3);
        order.setTotalAmount(5400L);
        order.setOrderType(orderType);
        return order;
    }

    private TenantRenewalRequest request(String tenantId, int months) {
        TenantRenewalRequest request = new TenantRenewalRequest();
        request.setTenantId(tenantId);
        request.setMonths(months);
        return request;
    }

    @Test
    void subjectFollowsOrderType() {
        assertEquals("标准版 续费 3 个月", TenantRenewalService.subjectOf(order(TenantOrderService.ORDER_TYPE_RENEW)));
        assertEquals("标准版 变更套餐 3 个月", TenantRenewalService.subjectOf(order(TenantOrderService.ORDER_TYPE_CHANGE)));
        assertEquals("标准版 开通 3 个月", TenantRenewalService.subjectOf(order(TenantOrderService.ORDER_TYPE_SUBSCRIBE)));
    }

    @Test
    void payRequestConvertsYuanToFenAndPointsBackToOrder() {
        PayCreateRequest pay = TenantRenewalService.payRequestOf(order(TenantOrderService.ORDER_TYPE_RENEW), "u1");
        assertEquals(TenantRenewalService.BIZ_TYPE, pay.getBizType());
        assertEquals("o1", pay.getBizId());
        assertEquals(540000L, pay.getAmount(), "租户订单是元, 支付单是分");
        assertEquals("t1", pay.getOwnerId(), "付款方是订单所属租户, 决定谁能在收银台看到这张单");
        assertEquals("u1", pay.getCreatorId());
        pay.validate();
    }

    @Test
    void planDefaultsToCurrentPlan() {
        assertEquals("p1", TenantRenewalService.resolvePlanId(null, "p1"));
        assertEquals("p1", TenantRenewalService.resolvePlanId("  ", "p1"));
        assertEquals("p2", TenantRenewalService.resolvePlanId("p2", "p1"));
        assertNull(TenantRenewalService.resolvePlanId(null, null));
    }

    @Test
    void pendingOrderHasNoChannelUntilPaid() {
        TenantRenewalRequest renewal = request("t1", 12);
        renewal.setRemark("客服发起");
        TenantSubscribeRequest subscribe = TenantRenewalService.subscribeRequestOf(renewal, "p1");
        assertNull(subscribe.getPayChannel());
        assertEquals(12, subscribe.getMonths());
        assertEquals("p1", subscribe.getPlanId());
        assertEquals("客服发起", subscribe.getRemark());
    }

    @Test
    void renewalRequestBoundsMonthsAndRequiresTenant() {
        request("t1", 1).validate();
        request("t1", 36).validate();
        assertThrows(ValidationException.class, () -> request("t1", 0).validate());
        assertThrows(ValidationException.class, () -> request("t1", 37).validate());
        assertThrows(ValidationException.class, () -> request(" ", 3).validate());
    }
}
