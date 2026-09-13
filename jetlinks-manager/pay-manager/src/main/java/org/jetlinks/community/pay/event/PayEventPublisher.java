package org.jetlinks.community.pay.event;

import lombok.RequiredArgsConstructor;
import org.jetlinks.community.pay.entity.PayOrderEntity;
import org.jetlinks.core.event.EventBus;
import reactor.core.publisher.Mono;

/**
 * 支付单事件主题: /pay/order/{bizType}/{orderId}/{status}.
 * 订阅方按需收窄, 例如 /pay/order/tenant-subscription/&#42;/paid 只看租户订阅的到账.
 *
 * @author pay-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class PayEventPublisher {

    public static final String TOPIC_PREFIX = "/pay/order/";

    private final EventBus eventBus;

    public static String topic(PayOrderEvent event) {
        return TOPIC_PREFIX + event.getBizType() + "/" + event.getOrderId() + "/" + event.getStatus();
    }

    public Mono<Void> publish(PayOrderEntity order) {
        PayOrderEvent event = PayOrderEvent.of(order, System.currentTimeMillis());
        return eventBus.publish(topic(event), event).then();
    }
}
