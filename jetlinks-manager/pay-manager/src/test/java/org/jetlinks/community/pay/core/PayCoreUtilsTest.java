package org.jetlinks.community.pay.core;

import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.pay.enums.PayOrderStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayCoreUtilsTest {

    @Test
    void yuanConvertsToFenAndOverflowFails() {
        assertEquals(180000L, PayAmounts.fenOfYuan(1800));
        assertEquals(0L, PayAmounts.fenOfYuan(0));
        assertThrows(ArithmeticException.class, () -> PayAmounts.fenOfYuan(Long.MAX_VALUE / 10));
    }

    @Test
    void amountFormatsWithTwoDecimals() {
        assertEquals("¥1,800.00", PayAmounts.format(180000));
        assertEquals("¥0.05", PayAmounts.format(5));
        assertEquals("¥0.00", PayAmounts.format(0));
    }

    private Map<String, String> fields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("orderId", "p1");
        fields.put("amount", "100");
        fields.put("empty", "");
        fields.put("sign", "ignored");
        return fields;
    }

    @Test
    void canonicalSortsKeysAndDropsSignAndEmpty() {
        assertEquals("amount=100&orderId=p1", PaySignatures.canonical(fields()));
    }

    @Test
    void signatureVerifiesOnlyWithSameSecretAndFields() {
        Map<String, String> fields = fields();
        String sign = PaySignatures.sign("s3cret", fields);
        assertTrue(PaySignatures.verify("s3cret", fields, sign));
        assertTrue(PaySignatures.verify("s3cret", fields, sign.toUpperCase()), "十六进制大小写不影响");
        assertFalse(PaySignatures.verify("other", fields, sign));
        assertFalse(PaySignatures.verify("s3cret", fields, ""));
        assertFalse(PaySignatures.verify("s3cret", fields, null));

        fields.put("amount", "1");
        assertFalse(PaySignatures.verify("s3cret", fields, sign), "改了金额签名必须失效");
    }

    @Test
    void onlyPendingOrdersWithoutReceiptCanClose() {
        assertTrue(PayOrderStateMachine.canPay(PayOrderStatus.pending));
        assertFalse(PayOrderStateMachine.canPay(PayOrderStatus.paid));
        assertTrue(PayOrderStateMachine.canClose(PayOrderStatus.pending, null));
        assertTrue(PayOrderStateMachine.canClose(PayOrderStatus.pending, " "));
        assertFalse(PayOrderStateMachine.canClose(PayOrderStatus.pending, "T1"), "有到账回执的单要人工处理, 不能关");
        assertFalse(PayOrderStateMachine.canClose(PayOrderStatus.closed, null));
        assertThrows(BusinessException.class, () -> PayOrderStateMachine.assertClosable(PayOrderStatus.paid, null));
    }
}
