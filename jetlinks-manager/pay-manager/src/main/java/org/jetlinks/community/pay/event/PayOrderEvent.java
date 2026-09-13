package org.jetlinks.community.pay.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetlinks.community.pay.entity.PayOrderEntity;

/**
 * 支付单状态变化事件, 经 EventBus 广播到集群. 字段足以让订阅方更新自己的状态, 不必回查支付单.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@Setter
@NoArgsConstructor
public class PayOrderEvent {

    private String orderId;
    private String bizType;
    private String bizId;
    private String ownerId;

    /** pending / paid / closed */
    private String status;

    /** 金额, 单位: 分 */
    private long amount;
    private String channel;
    private long time;

    public static PayOrderEvent of(PayOrderEntity order, long now) {
        PayOrderEvent event = new PayOrderEvent();
        event.setOrderId(order.getId());
        event.setBizType(order.getBizType());
        event.setBizId(order.getBizId());
        event.setOwnerId(order.getOwnerId());
        event.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        event.setAmount(order.getAmount() == null ? 0 : order.getAmount());
        event.setChannel(order.getChannel());
        event.setTime(now);
        return event;
    }
}
