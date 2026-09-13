package org.jetlinks.community.pay.channel;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.pay.PayProperties;
import org.jetlinks.community.pay.enums.PayOrderStatus;
import org.jetlinks.community.pay.spi.PayAction;
import org.jetlinks.community.pay.spi.PayNotifyRequest;
import org.jetlinks.community.pay.spi.PayOrderInfo;
import org.jetlinks.community.pay.spi.PayPrepareContext;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayChannelsTest {

    private static final Instant NOW = Instant.parse("2026-09-13T08:00:00Z");

    private PayOrderInfo order() {
        return PayOrderInfo.builder().id("p1").amount(180000).status(PayOrderStatus.pending).bizType("t").bizId("b").build();
    }

    private PayProperties sandboxProps() {
        PayProperties properties = new PayProperties();
        properties.getSandbox().setEnabled(true);
        properties.getSandbox().setSecret("unit-test-secret");
        return properties;
    }

    private PayNotifyRequest notify(String body) {
        return new PayNotifyRequest("sandbox", Collections.emptyMap(), Collections.emptyMap(), body, "127.0.0.1");
    }

    @Test
    void sandboxRequiresSecret() {
        PayProperties properties = new PayProperties();
        properties.getSandbox().setEnabled(true);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                                                   () -> new SandboxPayChannel(properties, Clock.systemUTC()));
        assertTrue(error.getMessage().contains("PAY_SANDBOX_SECRET"));
    }

    @Test
    void sandboxAcceptsItsOwnSignedNotify() {
        SandboxPayChannel channel = new SandboxPayChannel(sandboxProps(), Clock.fixed(NOW, ZoneOffset.UTC));
        StepVerifier
            .create(channel.parseNotify(notify(channel.signedNotifyBody(order()))))
            .assertNext(result -> {
                assertEquals("p1", result.getPayOrderId());
                assertEquals(180000, result.getPaidAmount());
                assertTrue(result.getChannelTradeNo().startsWith(SandboxPayChannel.TRADE_NO_PREFIX));
                assertTrue(result.isSuccess());
                assertEquals(NOW.toEpochMilli(), result.getPaidAt());
            })
            .verifyComplete();
    }

    @Test
    void sandboxRejectsTamperedAmount() {
        SandboxPayChannel channel = new SandboxPayChannel(sandboxProps(), Clock.fixed(NOW, ZoneOffset.UTC));
        JSONObject body = JSON.parseObject(channel.signedNotifyBody(order()));
        body.put("amount", "1");
        StepVerifier
            .create(channel.parseNotify(notify(body.toJSONString())))
            .expectErrorMatches(err -> err instanceof ValidationException && err.getMessage().contains("sign_invalid"))
            .verify();
    }

    @Test
    void sandboxRejectsReplayedStaleNotify() {
        PayProperties properties = sandboxProps();
        String body = new SandboxPayChannel(properties, Clock.fixed(NOW, ZoneOffset.UTC)).signedNotifyBody(order());
        Clock later = Clock.fixed(NOW.plus(Duration.ofMinutes(6)), ZoneOffset.UTC);
        StepVerifier
            .create(new SandboxPayChannel(properties, later).parseNotify(notify(body)))
            .expectErrorMatches(err -> err.getMessage().contains("expired"))
            .verify();
    }

    @Test
    void sandboxRejectsEmptyOrMalformedBody() {
        SandboxPayChannel channel = new SandboxPayChannel(sandboxProps(), Clock.fixed(NOW, ZoneOffset.UTC));
        StepVerifier.create(channel.parseNotify(notify(""))).expectError(ValidationException.class).verify();
        StepVerifier.create(channel.parseNotify(notify("not json"))).expectError(ValidationException.class).verify();
    }

    @Test
    void offlineShowsAmountAndOrderNoFirst() {
        PayProperties properties = new PayProperties();
        OfflinePayChannel channel = new OfflinePayChannel(properties);
        StepVerifier
            .create(channel.prepare(new PayPrepareContext(order(), "127.0.0.1", null)))
            .assertNext(action -> {
                assertEquals(PayAction.TYPE_OFFLINE, action.getType());
                List<String> keys = new ArrayList<>(action.getInstructions().keySet());
                assertEquals(List.of("应付金额", "转账备注"), keys.subList(0, 2));
                assertEquals("¥1,800.00", action.getInstructions().get("应付金额"));
                assertEquals("p1", action.getInstructions().get("转账备注"));
                assertEquals(List.of("应付金额", "转账备注", "付款方式", "说明"), keys, "配置项按声明顺序展示");
                assertThrows(UnsupportedOperationException.class, () -> action.getInstructions().clear(),
                             "动作对外只读, 渠道之外的代码改不了付款说明");
            })
            .verifyComplete();
    }
}
