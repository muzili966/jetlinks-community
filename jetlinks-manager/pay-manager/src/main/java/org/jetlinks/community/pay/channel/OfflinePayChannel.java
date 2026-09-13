package org.jetlinks.community.pay.channel;

import lombok.RequiredArgsConstructor;
import org.jetlinks.community.pay.PayProperties;
import org.jetlinks.community.pay.core.PayAmounts;
import org.jetlinks.community.pay.spi.PayAction;
import org.jetlinks.community.pay.spi.PayCapability;
import org.jetlinks.community.pay.spi.PayChannelProvider;
import org.jetlinks.community.pay.spi.PayPrepareContext;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 线下转账: 付款人按说明转账, 平台在支付订单页人工确认到账. 没有网关回调.
 *
 * @author pay-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class OfflinePayChannel implements PayChannelProvider {

    public static final String ID = "offline";

    private final PayProperties properties;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "线下转账";
    }

    @Override
    public Set<PayCapability> getCapabilities() {
        return EnumSet.of(PayCapability.OFFLINE_CONFIRM);
    }

    @Override
    public Mono<PayAction> prepare(PayPrepareContext context) {
        return Mono.just(PayAction.offline(instructions(properties.getOffline().getInstructions(), context)));
    }

    /**
     * 金额与订单号放在最前面, 付款人转账时最容易填错的就是这两项
     */
    static Map<String, String> instructions(Map<String, String> configured, PayPrepareContext context) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("应付金额", PayAmounts.format(context.getOrder().getAmount()));
        result.put("转账备注", context.getOrder().getId());
        result.putAll(configured);
        return result;
    }
}
