package org.jetlinks.community.pay.channel;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.hswebframework.web.exception.ValidationException;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.community.pay.PayProperties;
import org.jetlinks.community.pay.core.PaySignatures;
import org.jetlinks.community.pay.spi.PayAction;
import org.jetlinks.community.pay.spi.PayCapability;
import org.jetlinks.community.pay.spi.PayChannelProvider;
import org.jetlinks.community.pay.spi.PayNotification;
import org.jetlinks.community.pay.spi.PayNotifyRequest;
import org.jetlinks.community.pay.spi.PayOrderInfo;
import org.jetlinks.community.pay.spi.PayPrepareContext;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 沙箱渠道: 开发联调时模拟一个"会回调的网关".
 * <p>
 * 模拟支付不走捷径: 服务端按网关格式拼好回调报文、用密钥签名, 再交给和真实回调完全相同的验签与到账流程,
 * 这样联调通过就说明回调链路本身是通的. 密钥只在服务端, 浏览器拿不到, 所以前端没法伪造到账.
 *
 * @author pay-manager
 * @since 2.11
 */
public class SandboxPayChannel implements PayChannelProvider {

    public static final String ID = "sandbox";
    static final String TRADE_NO_PREFIX = "SBX";

    private final PayProperties.Sandbox config;
    private final Clock clock;

    public SandboxPayChannel(PayProperties properties, Clock clock) {
        this.config = properties.getSandbox();
        this.clock = clock;
        if (config.getSecret() == null || config.getSecret().isBlank()) {
            throw new IllegalStateException("pay.sandbox.enabled=true requires pay.sandbox.secret (set PAY_SANDBOX_SECRET)");
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "沙箱支付(联调)";
    }

    @Override
    public Set<PayCapability> getCapabilities() {
        return EnumSet.of(PayCapability.NOTIFY, PayCapability.SIMULATE);
    }

    @Override
    public Mono<PayAction> prepare(PayPrepareContext context) {
        return Mono.just(PayAction.simulate());
    }

    /**
     * 模拟网关: 生成一份已签名的到账回调报文
     */
    public String signedNotifyBody(PayOrderInfo order) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("orderId", order.getId());
        fields.put("tradeNo", TRADE_NO_PREFIX + IDGenerator.SNOW_FLAKE_STRING.generate());
        fields.put("amount", String.valueOf(order.getAmount()));
        fields.put("status", "SUCCESS");
        fields.put("timestamp", String.valueOf(clock.millis()));
        fields.put(PaySignatures.SIGN_FIELD, PaySignatures.sign(config.getSecret(), fields));
        return JSON.toJSONString(fields);
    }

    @Override
    public Mono<PayNotification> parseNotify(PayNotifyRequest request) {
        return Mono.fromCallable(() -> verify(request.getBody()));
    }

    PayNotification verify(String body) {
        Map<String, String> fields = fieldsOf(body);
        if (!PaySignatures.verify(config.getSecret(), fields, fields.get(PaySignatures.SIGN_FIELD))) {
            throw new ValidationException.NoStackTrace("error.pay_notify_sign_invalid");
        }
        long timestamp = parseLong(fields.get("timestamp"));
        if (Math.abs(clock.millis() - timestamp) > config.getNotifyMaxAge().toMillis()) {
            throw new ValidationException.NoStackTrace("error.pay_notify_expired");
        }
        return PayNotification
            .builder()
            .payOrderId(required(fields, "orderId"))
            .channelTradeNo(required(fields, "tradeNo"))
            .paidAmount(parseLong(fields.get("amount")))
            .paidAt(timestamp)
            .success("SUCCESS".equals(fields.get("status")))
            .build();
    }

    private static Map<String, String> fieldsOf(String body) {
        if (body == null || body.isBlank()) {
            throw new ValidationException.NoStackTrace("error.pay_notify_body_empty");
        }
        JSONObject json;
        try {
            json = JSON.parseObject(body);
        } catch (RuntimeException e) {
            throw new ValidationException.NoStackTrace("error.pay_notify_body_invalid");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        json.forEach((key, value) -> fields.put(key, value == null ? null : String.valueOf(value)));
        return fields;
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new ValidationException.NoStackTrace("error.pay_notify_field_missing", name);
        }
        return value;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException | NullPointerException e) {
            throw new ValidationException.NoStackTrace("error.pay_notify_body_invalid");
        }
    }
}
