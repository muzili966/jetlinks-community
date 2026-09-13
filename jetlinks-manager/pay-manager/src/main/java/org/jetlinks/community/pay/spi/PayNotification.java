package org.jetlinks.community.pay.spi;

import lombok.Builder;
import lombok.Getter;

/**
 * 验签通过后的支付结果. 核心会再核对金额与支付单是否一致, 渠道不必重复校验业务.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@Builder
public class PayNotification {

    private final String payOrderId;

    /** 网关交易号, 对账依据 */
    private final String channelTradeNo;

    /** 实付金额, 单位: 分 */
    private final long paidAmount;

    /** 网关确认的支付时间; 为空时取收到通知的时间 */
    private final Long paidAt;

    /** false 表示网关通知的是失败/未支付, 核心只记录不置为已支付 */
    private final boolean success;
}
