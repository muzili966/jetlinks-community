package org.jetlinks.community.pay.core;

import org.jetlinks.community.pay.enums.PayOrderStatus;
import org.jetlinks.community.pay.spi.PayNotification;

import java.util.Objects;

/**
 * 收到一条验签通过的到账通知后该怎么处理. 拆成纯函数是为了把每种边界情况都测到:
 * 钱的事情, 分支漏一个就是资损或者收了钱不给服务.
 *
 * @author pay-manager
 * @since 2.11
 */
public enum PaySettleDecision {

    /** 金额与支付单不一致: 拒收并告警, 可能是篡改或网关配置错 */
    REJECT_AMOUNT_MISMATCH,

    /** 网关通知的是失败/未支付: 只记录, 不入账 */
    IGNORE_NOT_SUCCESS,

    /** 已入账, 同一笔交易重复通知: 直接应答成功 */
    DUPLICATE,

    /** 已入账, 却来了另一笔交易号: 用户重复付款, 应答成功避免网关无限重试, 同时告警人工退款 */
    DUPLICATE_OTHER_TRADE,

    /** 单已关闭却到账了: 保留回执、告警, 由人工退款或补单 */
    REJECT_NOT_PAYABLE,

    /** 正常入账 */
    SETTLE;

    public static PaySettleDecision decide(PayOrderStatus status,
                                           Long orderAmount,
                                           String settledTradeNo,
                                           PayNotification notification) {
        if (orderAmount == null || orderAmount != notification.getPaidAmount()) {
            return REJECT_AMOUNT_MISMATCH;
        }
        if (!notification.isSuccess()) {
            return IGNORE_NOT_SUCCESS;
        }
        if (status == PayOrderStatus.paid) {
            return Objects.equals(settledTradeNo, notification.getChannelTradeNo()) ? DUPLICATE : DUPLICATE_OTHER_TRADE;
        }
        return status == PayOrderStatus.pending ? SETTLE : REJECT_NOT_PAYABLE;
    }
}
