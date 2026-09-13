package org.jetlinks.community.pay.service;

import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.pay.enums.PayOrderStatus;
import org.jetlinks.community.pay.entity.PayOrderEntity;
import org.jetlinks.community.pay.event.PayEventPublisher;
import org.jetlinks.community.pay.event.PayOrderEvent;
import org.jetlinks.community.pay.service.request.PayCreateRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayOrderServiceTest {

    private static final long NOW = 1_800_000_000_000L;

    private PayCreateRequest.PayCreateRequestBuilder request() {
        return PayCreateRequest.builder().bizType("tenant-subscription").bizId("o1").subject("标准版 续费 3 个月")
            .amount(540000).ownerId("t1").creatorId("u1");
    }

    @Test
    void newOrderIsPendingWithDefaultTtl() {
        PayOrderEntity order = PayOrderService.buildOrder(request().build(), NOW, Duration.ofHours(24));
        assertNotNull(order.getId());
        assertEquals(PayOrderStatus.pending, order.getStatus());
        assertEquals(540000L, order.getAmount());
        assertEquals("CNY", order.getCurrency());
        assertEquals("t1", order.getOwnerId());
        assertEquals(NOW + Duration.ofHours(24).toMillis(), order.getExpireAt());
        assertEquals(0, order.getNotifyCount());
    }

    @Test
    void requestTtlOverridesDefault() {
        PayOrderEntity order = PayOrderService.buildOrder(request().ttl(Duration.ofMinutes(15)).build(), NOW, Duration.ofHours(24));
        assertEquals(NOW + Duration.ofMinutes(15).toMillis(), order.getExpireAt());
    }

    @Test
    void createRequestRejectsBadInput() {
        assertThrows(ValidationException.class, () -> request().amount(0).build().validate());
        assertThrows(ValidationException.class, () -> request().amount(-1).build().validate());
        assertThrows(ValidationException.class, () -> request().bizId(" ").build().validate());
        assertThrows(ValidationException.class, () -> request().subject("x".repeat(257)).build().validate());
        assertThrows(ValidationException.class, () -> request().ttl(Duration.ZERO).build().validate());
        request().build().validate();
    }

    @Test
    void notifyUrlNeedsBaseAndNormalizesSlashes() {
        assertNull(PayOrderService.notifyUrlOf(null, "wechat"));
        assertNull(PayOrderService.notifyUrlOf(" ", "wechat"));
        assertEquals("https://api.example.com/pay/notify/wechat", PayOrderService.notifyUrlOf("https://api.example.com//", "wechat"));
    }

    @Test
    void offlineVoucherBecomesTradeNo() {
        assertEquals("OFFLINE-p1", PayOrderService.offlineTradeNo(null, "p1"));
        assertEquals("OFFLINE-p1", PayOrderService.offlineTradeNo("  ", "p1"));
        assertEquals("BANK20260913", PayOrderService.offlineTradeNo(" BANK20260913 ", "p1"));
        assertEquals(PayOrderService.TRADE_NO_MAX_LENGTH, PayOrderService.offlineTradeNo("x".repeat(300), "p1").length());
    }

    @Test
    void closeResultCountsEachOutcomeSeparately() {
        PayCloseResult result = PayCloseResult.empty()
            .plus(PayCloseResult.ofClosed(true))
            .plus(PayCloseResult.ofClosed(false))
            .plus(PayCloseResult.ofFailed())
            .plus(PayCloseResult.ofClosed(true));
        assertEquals(2, result.getClosed());
        assertEquals(1, result.getSkipped());
        assertEquals(1, result.getFailed());
    }

    @Test
    void eventTopicCarriesBizTypeOrderAndStatus() {
        PayOrderEntity order = PayOrderService.buildOrder(request().build(), NOW, Duration.ofHours(1));
        order.setStatus(PayOrderStatus.paid);
        PayOrderEvent event = PayOrderEvent.of(order, NOW);
        assertEquals("/pay/order/tenant-subscription/" + order.getId() + "/paid", PayEventPublisher.topic(event));
        assertEquals(540000L, event.getAmount());
    }
}
