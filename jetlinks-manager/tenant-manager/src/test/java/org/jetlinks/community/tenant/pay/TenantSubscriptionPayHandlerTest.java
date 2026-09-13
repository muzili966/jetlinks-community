package org.jetlinks.community.tenant.pay;

import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.DefaultDimensionType;
import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.hswebframework.web.authorization.simple.SimpleDimension;
import org.hswebframework.web.authorization.simple.SimpleUser;
import org.hswebframework.web.exception.NotFoundException;
import org.jetlinks.community.pay.enums.PayOrderStatus;
import org.jetlinks.community.pay.spi.PayBizSummary;
import org.jetlinks.community.pay.spi.PayOrderInfo;
import org.jetlinks.community.tenant.TenantDimensionType;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.jetlinks.community.tenant.enums.TenantOrderStatus;
import org.jetlinks.community.tenant.service.TenantOrderService;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantSubscriptionPayHandlerTest {

    private static final String ADMIN_ROLE = "platform-admin";
    private static final long NOW = 1_800_000_000_000L;

    private Authentication tenantUser(String tenantId) {
        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(SimpleUser.builder().id("u1").name("user").build());
        auth.setDimensions(Collections.singletonList(
            SimpleDimension.of(tenantId, "租户", TenantDimensionType.tenant, Collections.emptyMap())));
        return auth;
    }

    private Authentication platformAdmin() {
        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(SimpleUser.builder().id("admin").name("admin").build());
        auth.setDimensions(Collections.singletonList(
            SimpleDimension.of(ADMIN_ROLE, "平台管理员", DefaultDimensionType.role, Collections.emptyMap())));
        return auth;
    }

    private Authentication staffWithoutTenant() {
        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(SimpleUser.builder().id("cs1").name("客服").build());
        auth.setDimensions(Collections.emptyList());
        return auth;
    }

    @Test
    void onlyOwnerTenantOrPlatformAdminCanPay() {
        assertTrue(TenantSubscriptionPayHandler.canPay(tenantUser("t1"), "t1", ADMIN_ROLE));
        assertFalse(TenantSubscriptionPayHandler.canPay(tenantUser("t2"), "t1", ADMIN_ROLE), "别的租户不能付");
        assertTrue(TenantSubscriptionPayHandler.canPay(platformAdmin(), "t1", ADMIN_ROLE), "平台管理员可以代付");
        assertFalse(TenantSubscriptionPayHandler.canPay(staffWithoutTenant(), "t1", ADMIN_ROLE));
        assertFalse(TenantSubscriptionPayHandler.canPay(null, "t1", ADMIN_ROLE));
        assertFalse(TenantSubscriptionPayHandler.canPay(tenantUser("t1"), null, ADMIN_ROLE), "没有归属的单只有平台能看");
    }

    @Test
    void foreignOrderLooksLikeNotFound() {
        TenantProperties properties = new TenantProperties();
        properties.setPlatformAdminRoleId(ADMIN_ROLE);
        TenantSubscriptionPayHandler handler = new TenantSubscriptionPayHandler(properties, null, null);
        PayOrderInfo order = PayOrderInfo.builder().id("p1").ownerId("t1").status(PayOrderStatus.pending).build();

        StepVerifier.create(handler.assertPayable(order, tenantUser("t2"))).expectError(NotFoundException.class).verify();
        StepVerifier.create(handler.assertPayable(order, tenantUser("t1"))).verifyComplete();
    }

    private TenantOrderEntity order(TenantOrderStatus status, Long expireAfter) {
        TenantOrderEntity order = new TenantOrderEntity();
        order.setPlanName("标准版");
        order.setMonths(3);
        order.setStatus(status);
        order.setExpireTimeAfter(expireAfter);
        return order;
    }

    private TenantEntity tenant(Long expire) {
        TenantEntity tenant = new TenantEntity();
        tenant.setName("天马科技");
        tenant.setSubscribeExpireTime(expire);
        return tenant;
    }

    @Test
    void pendingSummaryShowsEstimatedExpiry() {
        PayBizSummary summary = TenantSubscriptionPayHandler.summaryOf(order(TenantOrderStatus.pending, null), tenant(null), NOW);
        List<PayBizSummary.Field> fields = summary.getFields();
        assertEquals("标准版 订阅", summary.getTitle());
        assertEquals("天马科技", fields.get(0).getValue());
        assertEquals("3 个月", fields.get(2).getValue());
        assertEquals(PayBizSummary.Field.TYPE_DATETIME, fields.get(3).getType());
        assertNull(fields.get(3).getValue(), "未订阅时当前到期为空, 由前端显示为未订阅");
        assertEquals("付款后到期(预估)", fields.get(4).getLabel());
        assertEquals(String.valueOf(TenantOrderService.computeExpireAfter(null, 3, NOW)), fields.get(4).getValue());
    }

    @Test
    void paidSummaryShowsActualExpiry() {
        long actual = NOW + 123L;
        PayBizSummary summary = TenantSubscriptionPayHandler.summaryOf(order(TenantOrderStatus.paid, actual), tenant(NOW), NOW);
        PayBizSummary.Field last = summary.getFields().get(4);
        assertEquals("续费后到期", last.getLabel());
        assertEquals(String.valueOf(actual), last.getValue());
    }
}
