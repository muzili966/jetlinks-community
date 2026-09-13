package org.jetlinks.community.pay.spi;

import org.hswebframework.web.exception.ValidationException;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * 支付渠道扩展点. 一个实现对接一个收款渠道(线下转账、微信支付、支付宝、聚合支付...),
 * 以 Spring Bean 注册, 由 {@code PayChannelRegistry} 按 {@link #getId()} 汇总.
 * <p>
 * 契约:
 * <ul>
 *     <li>{@link #getId()} 会写进支付单的 channel 字段、回调地址 {@code /pay/notify/{id}} 和前端, 是稳定契约, 上线后不改</li>
 *     <li>{@link #parseNotify} 必须先验签再返回结果; 验签失败返回 error, 不能返回一个"未支付"的通知了事</li>
 *     <li>金额一律以「分」为单位, 渠道自己负责与网关单位互转</li>
 *     <li>渠道凭证(密钥、证书)从部署配置读取, 不写进源码与日志</li>
 * </ul>
 *
 * @author pay-manager
 * @since 2.11
 */
public interface PayChannelProvider {

    /**
     * 渠道ID, 稳定契约
     */
    String getId();

    /**
     * 展示名称
     */
    String getName();

    /**
     * 渠道能力, 核心据此决定开放哪些操作(是否接受回调、是否允许人工确认收款...)
     */
    Set<PayCapability> getCapabilities();

    default boolean supports(PayCapability capability) {
        return getCapabilities().contains(capability);
    }

    /**
     * 发起支付: 返回前端下一步该做什么(跳转收银台 / 展示二维码 / 展示线下转账信息).
     * 可以调用网关预下单, 但不修改支付单状态.
     */
    Mono<PayAction> prepare(PayPrepareContext context);

    /**
     * 解析并验签网关回调. 只有具备 {@link PayCapability#NOTIFY} 的渠道会被调用.
     */
    default Mono<PayNotification> parseNotify(PayNotifyRequest request) {
        return Mono.error(new ValidationException.NoStackTrace("error.pay_channel_notify_unsupported"));
    }

    /**
     * 支付单关闭时通知网关撤销预下单; 不支持的渠道忽略即可
     */
    default Mono<Void> close(PayOrderInfo order) {
        return Mono.empty();
    }

    /**
     * 回调处理完后回给网关的响应体; 各网关要求不同(支付宝要 "success", 微信 v3 要 JSON)
     */
    default String notifyAck(boolean handled) {
        return handled ? "success" : "fail";
    }

    default String notifyAckContentType() {
        return "text/plain;charset=UTF-8";
    }
}
