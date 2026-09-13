package org.jetlinks.community.pay.core;

import org.jetlinks.community.pay.enums.PayOrderStatus;
import org.jetlinks.community.pay.spi.PayNotification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaySettleDecisionTest {

    private PayNotification notice(long amount, String tradeNo, boolean success) {
        return PayNotification.builder().payOrderId("p1").channelTradeNo(tradeNo).paidAmount(amount).success(success).build();
    }

    @Test
    void pendingWithMatchingAmountSettles() {
        assertEquals(PaySettleDecision.SETTLE,
                     PaySettleDecision.decide(PayOrderStatus.pending, 180000L, null, notice(180000, "T1", true)));
    }

    @Test
    void amountMismatchIsRejectedBeforeAnythingElse() {
        assertEquals(PaySettleDecision.REJECT_AMOUNT_MISMATCH,
                     PaySettleDecision.decide(PayOrderStatus.pending, 180000L, null, notice(1, "T1", true)));
        // 已入账的单也要先核金额: 不能因为是"重复通知"就放过被改过金额的报文
        assertEquals(PaySettleDecision.REJECT_AMOUNT_MISMATCH,
                     PaySettleDecision.decide(PayOrderStatus.paid, 180000L, "T1", notice(179999, "T1", true)));
        assertEquals(PaySettleDecision.REJECT_AMOUNT_MISMATCH,
                     PaySettleDecision.decide(PayOrderStatus.pending, null, null, notice(0, "T1", true)));
    }

    @Test
    void failedNotificationIsOnlyRecorded() {
        assertEquals(PaySettleDecision.IGNORE_NOT_SUCCESS,
                     PaySettleDecision.decide(PayOrderStatus.pending, 100L, null, notice(100, "T1", false)));
    }

    @Test
    void repeatedNotificationOfSameTradeIsDuplicate() {
        assertEquals(PaySettleDecision.DUPLICATE,
                     PaySettleDecision.decide(PayOrderStatus.paid, 100L, "T1", notice(100, "T1", true)));
    }

    @Test
    void secondTradeOnPaidOrderIsFlaggedForRefund() {
        assertEquals(PaySettleDecision.DUPLICATE_OTHER_TRADE,
                     PaySettleDecision.decide(PayOrderStatus.paid, 100L, "T1", notice(100, "T2", true)));
    }

    @Test
    void paymentForClosedOrderIsRejected() {
        assertEquals(PaySettleDecision.REJECT_NOT_PAYABLE,
                     PaySettleDecision.decide(PayOrderStatus.closed, 100L, null, notice(100, "T1", true)));
    }
}
