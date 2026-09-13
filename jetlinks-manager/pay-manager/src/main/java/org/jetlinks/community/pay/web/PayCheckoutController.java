package org.jetlinks.community.pay.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.pay.channel.SandboxPayChannel;
import org.jetlinks.community.pay.core.PayChannelRegistry;
import org.jetlinks.community.pay.core.PayOrderStateMachine;
import org.jetlinks.community.pay.entity.PayOrderEntity;
import org.jetlinks.community.pay.service.PayOrderService;
import org.jetlinks.community.pay.service.request.PayPrepareCommand;
import org.jetlinks.community.pay.spi.PayAction;
import org.jetlinks.community.pay.spi.PayBizSummary;
import org.jetlinks.community.pay.spi.PayNotifyRequest;
import org.jetlinks.community.pay.web.request.PayPrepareRequest;
import org.jetlinks.community.pay.web.response.PayChannelView;
import org.jetlinks.community.pay.web.response.PayCheckoutView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 收银台. 登录即可访问, 但每次都由业务处理器校验"这张单是不是你该付的"(租户只能付自己租户的单),
 * 所以不设额外的权限资源.
 *
 * @author pay-manager
 * @since 2.11
 */
@ConditionalOnProperty(prefix = "pay", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/pay/checkout")
@Authorize
@RequiredArgsConstructor
@Tag(name = "收银台")
public class PayCheckoutController {

    private final PayOrderService service;
    private final PayChannelRegistry channels;

    @GetMapping("/{id}")
    @Operation(summary = "支付单详情与可选支付方式")
    public Mono<PayCheckoutView> detail(@PathVariable String id) {
        return currentAuth()
            .flatMap(auth -> service.findPayable(id, auth))
            .flatMap(order -> service
                .describe(order)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .map(biz -> view(order, biz.orElse(null))));
    }

    private PayCheckoutView view(PayOrderEntity order, PayBizSummary biz) {
        List<PayChannelView> options = PayOrderStateMachine.canPay(order.getStatus())
            ? channels.all().stream().map(PayChannelView::of).toList()
            : Collections.emptyList();
        return PayCheckoutView.of(order, biz, options);
    }

    @PostMapping("/{id}/_prepare")
    @Operation(summary = "选择支付方式并发起支付, 返回下一步动作")
    public Mono<PayAction> prepare(@PathVariable String id,
                                   @RequestBody @Valid Mono<PayPrepareRequest> body,
                                   ServerWebExchange exchange) {
        String clientIp = PayClientIp.of(exchange.getRequest());
        return Mono
            .zip(currentAuth(), body)
            .flatMap(tuple -> service.prepare(
                new PayPrepareCommand(id, tuple.getT2().getChannel(), tuple.getT1(), clientIp)));
    }

    /**
     * 沙箱模拟到账. 服务端生成签名回调, 并去掉当前登录态再交给回调流程,
     * 让它和网关匿名回调完全一致(包括不带租户隔离条件).
     */
    @PostMapping("/{id}/_simulate")
    @Operation(summary = "模拟到账(仅沙箱渠道开启时可用)")
    public Mono<PayCheckoutView> simulate(@PathVariable String id, ServerWebExchange exchange) {
        SandboxPayChannel sandbox = channels
            .find(SandboxPayChannel.ID)
            .filter(SandboxPayChannel.class::isInstance)
            .map(SandboxPayChannel.class::cast)
            .orElse(null);
        if (sandbox == null) {
            return Mono.error(new BusinessException("error.pay_simulate_disabled", 404));
        }
        String clientIp = PayClientIp.of(exchange.getRequest());
        return currentAuth()
            .flatMap(auth -> service.findPayable(id, auth))
            .flatMap(order -> {
                if (!PayOrderStateMachine.canPay(order.getStatus())) {
                    return Mono.error(new BusinessException("error.pay_order_not_payable", 400));
                }
                PayNotifyRequest notify = new PayNotifyRequest(
                    SandboxPayChannel.ID, Collections.emptyMap(), Collections.emptyMap(),
                    sandbox.signedNotifyBody(order.toInfo()), clientIp);
                return service
                    .handleNotify(notify)
                    .contextWrite(ctx -> ctx.delete(Authentication.class));
            })
            .then(detail(id));
    }

    private Mono<Authentication> currentAuth() {
        return Authentication
            .currentReactive()
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.unauthorized", 401)));
    }
}
