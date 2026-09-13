package org.jetlinks.community.pay.core;

import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.pay.enums.PayOrderStatus;

/**
 * 支付单状态规则. 真正防并发的是数据库条件更新(where status = pending), 这里只做前置判断与报错.
 *
 * @author pay-manager
 * @since 2.11
 */
public final class PayOrderStateMachine {

    private PayOrderStateMachine() {
    }

    public static boolean canPay(PayOrderStatus status) {
        return status == PayOrderStatus.pending;
    }

    /**
     * 已收到有效到账通知(有交易号)的待支付单不能关: 说明钱到了但履约失败, 要人工处理而不是关掉
     */
    public static boolean canClose(PayOrderStatus status, String channelTradeNo) {
        return status == PayOrderStatus.pending && (channelTradeNo == null || channelTradeNo.isBlank());
    }

    public static void assertPayable(PayOrderStatus status) {
        if (!canPay(status)) {
            throw new BusinessException("error.pay_order_not_payable", 400);
        }
    }

    public static void assertClosable(PayOrderStatus status, String channelTradeNo) {
        if (!canClose(status, channelTradeNo)) {
            throw new BusinessException("error.pay_order_not_closable", 400);
        }
    }
}
