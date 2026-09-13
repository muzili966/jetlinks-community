package org.jetlinks.community.pay.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.jetlinks.community.pay.PayConstants;
import org.jetlinks.community.pay.core.PayChannelRegistry;
import org.jetlinks.community.pay.service.PayOrderService;
import org.jetlinks.community.pay.spi.PayChannelProvider;
import org.jetlinks.community.pay.spi.PayNotifyRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 网关回调入口. 公开原因: 调用方是支付网关, 没有平台账号.
 * 防护: 渠道必须具备 NOTIFY 能力; 验签由渠道完成且失败即拒; 核心再核对金额与状态; 请求体限制 64KB.
 * 失败只记录错误码与来源 IP, 不记录报文(可能含商户信息).
 *
 * @author pay-manager
 * @since 2.11
 */
@Slf4j
@ConditionalOnProperty(prefix = "pay", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping(PayConstants.NOTIFY_PATH)
@Authorize(ignore = true)
@RequiredArgsConstructor
@Tag(name = "支付回调")
public class PayNotifyController {

    static final int MAX_BODY_BYTES = 64 * 1024;
    private static final String DEFAULT_ACK_TYPE = "text/plain;charset=UTF-8";

    private final PayOrderService service;
    private final PayChannelRegistry channels;

    @PostMapping("/{channel}")
    @Operation(summary = "支付网关异步通知")
    public Mono<Void> notify(@PathVariable String channel, ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = PayClientIp.of(request);
        return DataBufferUtils
            .join(request.getBody(), MAX_BODY_BYTES)
            .map(buffer -> {
                String text = buffer.toString(StandardCharsets.UTF_8);
                DataBufferUtils.release(buffer);
                return text;
            })
            .defaultIfEmpty("")
            .flatMap(body -> service
                .handleNotify(new PayNotifyRequest(channel, headersOf(request), request.getQueryParams().toSingleValueMap(),
                                                   body, clientIp))
                .thenReturn(true))
            .onErrorResume(err -> {
                log.warn("pay notify rejected, channel={}, ip={}, reason={}", channel, clientIp, err.getMessage());
                return Mono.just(false);
            })
            .flatMap(handled -> writeAck(exchange.getResponse(), channel, handled));
    }

    private static Map<String, String> headersOf(ServerHttpRequest request) {
        Map<String, String> headers = new HashMap<>();
        request.getHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name.toLowerCase(Locale.ROOT), values.get(0));
            }
        });
        return headers;
    }

    private Mono<Void> writeAck(ServerHttpResponse response, String channelId, boolean handled) {
        Optional<PayChannelProvider> channel = channels.find(channelId);
        String body = channel.map(provider -> provider.notifyAck(handled)).orElse("fail");
        String contentType = channel.map(PayChannelProvider::notifyAckContentType).orElse(DEFAULT_ACK_TYPE);
        response.setStatusCode(handled ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, contentType);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
