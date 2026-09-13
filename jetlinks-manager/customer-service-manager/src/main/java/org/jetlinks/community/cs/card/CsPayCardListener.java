package org.jetlinks.community.cs.card;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.cs.service.CsSessionService;
import org.jetlinks.community.gateway.annotation.Subscribe;
import org.jetlinks.community.pay.event.PayOrderEvent;
import org.jetlinks.core.event.Subscription;
import reactor.core.publisher.Mono;

/**
 * 支付单状态变化时回写续费卡片. 只订阅本节点事件: 状态变化发生在哪个节点就由哪个节点回写一次,
 * 回写后的会话事件再经 EventBus 广播到所有节点的访客与坐席连接.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Slf4j
@RequiredArgsConstructor
public class CsPayCardListener {

    private final CsSessionService sessionService;

    @Subscribe(value = "/pay/order/*/*/*", features = Subscription.Feature.local)
    public Mono<Void> onPayOrderChanged(PayOrderEvent event) {
        return sessionService
            .syncCardStatus(RenewalCardProvider.REF_TYPE_PAY_ORDER, event.getOrderId(), event.getStatus())
            .onErrorResume(err -> {
                // 卡片状态只是展示, 回写失败不影响到账; 收银台与订单页仍以支付单为准
                log.warn("sync card status failed, payOrder={}, status={}", event.getOrderId(), event.getStatus(), err);
                return Mono.empty();
            });
    }
}
