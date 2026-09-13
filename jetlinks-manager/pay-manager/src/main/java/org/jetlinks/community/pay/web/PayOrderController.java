package org.jetlinks.community.pay.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.hswebframework.web.crud.service.ReactiveCrudService;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceQueryController;
import org.jetlinks.community.pay.PayConstants;
import org.jetlinks.community.pay.core.PayChannelRegistry;
import org.jetlinks.community.pay.entity.PayOrderEntity;
import org.jetlinks.community.pay.service.PayOrderService;
import org.jetlinks.community.pay.web.request.PayCloseRequest;
import org.jetlinks.community.pay.web.request.PayConfirmOfflineRequest;
import org.jetlinks.community.pay.web.response.PayChannelView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 支付订单管理(平台). 支付单是财务记录, 只查询与状态操作, 不提供修改删除.
 *
 * @author pay-manager
 * @since 2.11
 */
@ConditionalOnProperty(prefix = "pay", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/pay/order")
@Authorize
@Resource(id = PayConstants.RESOURCE_ORDER, name = "支付订单")
@AllArgsConstructor
@Getter
@Tag(name = "支付订单")
public class PayOrderController implements ReactiveServiceQueryController<PayOrderEntity, String> {

    private final PayOrderService service;
    private final PayChannelRegistry channels;

    @Override
    public ReactiveCrudService<PayOrderEntity, String> getService() {
        return service;
    }

    @GetMapping("/_channels")
    @QueryAction
    @Operation(summary = "已启用的支付渠道")
    public Flux<PayChannelView> enabledChannels() {
        return Flux.fromIterable(channels.all()).map(PayChannelView::of);
    }

    @PostMapping("/{id}/_confirm-offline")
    @SaveAction
    @Operation(summary = "确认线下到账(与网关回调走同一条入账与履约流程)")
    public Mono<Void> confirmOffline(@PathVariable String id, @RequestBody @Valid Mono<PayConfirmOfflineRequest> body) {
        return body
            .defaultIfEmpty(new PayConfirmOfflineRequest())
            .flatMap(request -> service.confirmOffline(id, request.getChannel(), request.getVoucher()));
    }

    @PostMapping("/{id}/_close")
    @SaveAction
    @Operation(summary = "关闭待支付的支付单(已有到账回执的单不能关)")
    public Mono<Void> close(@PathVariable String id, @RequestBody @Valid Mono<PayCloseRequest> body) {
        return body
            .defaultIfEmpty(new PayCloseRequest())
            .flatMap(request -> service.close(id, request.getReason() == null ? "平台关闭" : request.getReason()));
    }
}
