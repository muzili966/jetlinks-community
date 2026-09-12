package org.jetlinks.community.cs.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.auth.captcha.impl.ImageCaptchaProvider;
import org.jetlinks.community.cs.CsConstants;
import org.jetlinks.community.cs.service.CsInboxRateLimiter;
import org.jetlinks.community.cs.service.CsInboxService;
import org.jetlinks.community.cs.service.request.CsInboxSubmitRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

/**
 * 官网调用的匿名接口. 公开原因: 访客没有账号. 防护: 图片验证码(复用登录验证码) + 按 IP 限流 + 长度限制.
 * 验证码图片沿用 {@code GET /authorize/captcha/image}.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Slf4j
@ConditionalOnProperty(prefix = "customer-service", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping(CsConstants.PUBLIC_PATH)
@Authorize(ignore = true)
@RequiredArgsConstructor
@Tag(name = "客服公开接口")
public class CsPublicController {

    private static final int TOO_MANY_REQUESTS = 429;

    private final CsInboxService inboxService;
    private final CsInboxRateLimiter rateLimiter;
    private final ObjectProvider<ImageCaptchaProvider> captchaProvider;

    @PostMapping("/inbox")
    @Operation(summary = "官网提交留言(匿名, 需验证码)")
    public Mono<Map<String, String>> submit(@RequestBody @Valid Mono<CsInboxSubmitRequest> body,
                                            ServerWebExchange exchange) {
        ClientInfo client = ClientInfo.from(exchange.getRequest());
        return body
            .flatMap(request -> checkRateLimit(client)
                .then(validateCaptcha(request))
                .then(inboxService.submit(request, client)))
            .map(message -> Collections.singletonMap("id", message.getId()));
    }

    private Mono<Void> checkRateLimit(ClientInfo client) {
        if (rateLimiter.tryAcquire(client.getIp())) {
            return Mono.empty();
        }
        log.warn("customer-service inbox rate limited, ip={}", client.getIp());
        return Mono.error(new BusinessException("error.cs_inbox_rate_limited", TOO_MANY_REQUESTS));
    }

    private Mono<Void> validateCaptcha(CsInboxSubmitRequest request) {
        ImageCaptchaProvider provider = captchaProvider.getIfAvailable();
        if (provider == null) {
            log.warn("image captcha provider disabled, customer-service inbox accepts submissions without captcha");
            return Mono.empty();
        }
        return provider.validate(request::captchaParameter);
    }
}
