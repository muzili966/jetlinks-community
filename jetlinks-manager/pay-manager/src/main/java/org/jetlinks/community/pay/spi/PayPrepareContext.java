package org.jetlinks.community.pay.spi;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发起支付的上下文.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class PayPrepareContext {

    private final PayOrderInfo order;

    /** 付款人IP, 部分网关预下单要求 */
    private final String clientIp;

    /** 网关回调地址: {pay.notify-base-url}/pay/notify/{channel} */
    private final String notifyUrl;
}
