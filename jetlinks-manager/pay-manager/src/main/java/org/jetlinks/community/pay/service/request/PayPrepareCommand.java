package org.jetlinks.community.pay.service.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.authorization.Authentication;

/**
 * 发起支付: 谁、用哪个渠道、付哪张单.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class PayPrepareCommand {

    private final String orderId;
    private final String channelId;
    private final Authentication auth;
    private final String clientIp;
}
