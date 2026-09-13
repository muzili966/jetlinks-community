package org.jetlinks.community.pay.core;

import org.hswebframework.web.authorization.Authentication;
import org.jetlinks.community.pay.PayProperties;
import org.jetlinks.community.pay.spi.PayAction;
import org.jetlinks.community.pay.spi.PayBizHandler;
import org.jetlinks.community.pay.spi.PayCapability;
import org.jetlinks.community.pay.spi.PayChannelProvider;
import org.jetlinks.community.pay.spi.PayOrderInfo;
import org.jetlinks.community.pay.spi.PayPrepareContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayRegistryTest {

    static class FakeChannel implements PayChannelProvider {
        private final String id;

        FakeChannel(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return id;
        }

        @Override
        public Set<PayCapability> getCapabilities() {
            return EnumSet.of(PayCapability.NOTIFY);
        }

        @Override
        public Mono<PayAction> prepare(PayPrepareContext context) {
            return Mono.just(PayAction.simulate());
        }
    }

    static class OtherFakeChannel extends FakeChannel {
        OtherFakeChannel(String id) {
            super(id);
        }
    }

    static class FakeHandler implements PayBizHandler {
        private final String type;

        FakeHandler(String type) {
            this.type = type;
        }

        @Override
        public String getBizType() {
            return type;
        }

        @Override
        public Mono<Void> assertPayable(PayOrderInfo order, Authentication auth) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> onPaid(PayOrderInfo order) {
            return Mono.empty();
        }
    }

    @Test
    void duplicateChannelIdFailsWithBothImplementationsNamed() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> PayChannelRegistry
            .index(List.of(new FakeChannel("wechat"), new OtherFakeChannel("wechat")), new PayProperties()));
        assertTrue(error.getMessage().contains("FakeChannel"));
        assertTrue(error.getMessage().contains("OtherFakeChannel"));
    }

    @Test
    void disabledChannelIsLeftOutAndUnconfiguredChannelIsOn() {
        PayProperties properties = new PayProperties();
        PayProperties.Channel off = new PayProperties.Channel();
        off.setEnabled(false);
        properties.setChannels(Map.of("alipay", off));

        Map<String, PayChannelProvider> index = PayChannelRegistry
            .index(List.of(new FakeChannel("alipay"), new FakeChannel("wechat")), properties);
        assertEquals(Set.of("wechat"), index.keySet());
    }

    @Test
    void duplicateBizTypeFails() {
        assertThrows(IllegalStateException.class, () -> PayBizRegistry
            .build(List.of(new FakeHandler("tenant-subscription"), new FakeHandler("tenant-subscription"))));
        assertEquals(Set.of("a", "b"), PayBizRegistry.build(List.of(new FakeHandler("a"), new FakeHandler("b"))).keySet());
    }
}
