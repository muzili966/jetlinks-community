package org.jetlinks.community.pay.spi;

import lombok.Builder;
import lombok.Getter;
import org.jetlinks.community.pay.enums.PayOrderStatus;

/**
 * 交给渠道与业务扩展点的支付单只读视图, 扩展点拿不到实体, 也就改不了状态.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@Builder
public class PayOrderInfo {

    private final String id;
    private final String bizType;
    private final String bizId;
    private final String subject;

    /** 金额, 单位: 分 */
    private final long amount;
    private final String currency;
    private final PayOrderStatus status;
    private final String channel;
    private final String channelTradeNo;

    /** 应付款方, 由业务定义(租户订阅为租户ID) */
    private final String ownerId;
    private final Long expireAt;
    private final Long paidAt;
}
