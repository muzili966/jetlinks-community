package org.jetlinks.community.cs.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.cs.CsConstants;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.chat.CsSessionView;
import org.jetlinks.community.cs.chat.CsVisitorEventStream;
import org.jetlinks.community.cs.entity.CsChatMessageEntity;
import org.jetlinks.community.cs.service.CsAgentService;
import org.jetlinks.community.cs.service.CsChatRateLimiters;
import org.jetlinks.community.cs.service.CsSessionService;
import org.jetlinks.community.cs.service.request.CsChatMessageRequest;
import org.jetlinks.community.cs.service.request.CsSessionContactRequest;
import org.jetlinks.community.cs.service.request.CsSessionOpenRequest;
import org.jetlinks.community.cs.service.request.CsSessionRateRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

/**
 * 官网访客的在线会话接口(匿名). 凭证是创建会话时一次性下发的 token, 之后每个请求以 query 参数携带
 * (EventSource 不能设置请求头). 防护: 按 IP 限制发起会话次数, 按会话限制消息频率.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Slf4j
@ConditionalOnProperty(prefix = "customer-service", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping(CsConstants.PUBLIC_PATH + "/session")
@Authorize(ignore = true)
@RequiredArgsConstructor
@Tag(name = "客服在线会话(访客)")
public class CsPublicSessionController {

    private static final int TOO_MANY_REQUESTS = 429;

    private final CsProperties properties;
    private final CsSessionService sessionService;
    private final CsAgentService agentService;
    private final CsChatRateLimiters rateLimiters;
    private final CsVisitorEventStream eventStream;

    @GetMapping("/_status")
    @Operation(summary = "是否有坐席在线")
    public Mono<Map<String, Boolean>> status() {
        return agentService.hasOnline().map(online -> Collections.singletonMap("agentsOnline", online));
    }

    @PostMapping
    @Operation(summary = "发起会话, 返回会话与访客凭证")
    public Mono<CsSessionView> open(@RequestBody @Valid Mono<CsSessionOpenRequest> body, ServerWebExchange exchange) {
        ClientInfo client = ClientInfo.from(exchange.getRequest());
        return body.flatMap(request -> limit(rateLimiters.getSessions(), client.getIp(), "session")
            .then(sessionService.open(request, client)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "恢复会话(刷新页面后)")
    public Mono<CsSessionView> resume(@PathVariable String id, @RequestParam String token) {
        return sessionService.resume(id, token);
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "历史消息(按时间正序)")
    public Flux<CsChatMessageEntity> messages(@PathVariable String id, @RequestParam String token) {
        return sessionService.visitorHistory(id, token);
    }

    @PostMapping("/{id}/message")
    @Operation(summary = "访客发送消息")
    public Mono<CsChatMessageEntity> send(@PathVariable String id,
                                          @RequestParam String token,
                                          @RequestBody @Valid Mono<CsChatMessageRequest> body) {
        return body.flatMap(request -> limit(rateLimiters.getMessages(), id, "message")
            .then(sessionService.visitorMessage(id, token, request.validated(properties.getChat().getMessageMaxLength()))));
    }

    @PostMapping(value = "/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "访客发送图片 / 视频 / 文件(multipart 字段 file)")
    public Mono<CsChatMessageEntity> attachment(@PathVariable String id,
                                                @RequestParam String token,
                                                @RequestPart("file") Mono<FilePart> file) {
        return file.flatMap(part -> limit(rateLimiters.getMessages(), id, "attachment")
            .then(sessionService.visitorAttachment(id, token, part)));
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅会话事件(SSE)")
    public Flux<ServerSentEvent<Object>> events(@PathVariable String id,
                                                @Parameter(description = "访客凭证") @RequestParam String token) {
        return sessionService
            .findForVisitor(id, token)
            .flatMapMany(session -> eventStream.stream(id, properties.getChat().getHeartbeatInterval()));
    }

    @PostMapping("/{id}/_read")
    @Operation(summary = "访客已读")
    public Mono<Void> read(@PathVariable String id, @RequestParam String token) {
        return sessionService.visitorRead(id, token);
    }

    @PostMapping("/{id}/_contact")
    @Operation(summary = "访客补充称呼与联系方式")
    public Mono<Void> contact(@PathVariable String id,
                              @RequestParam String token,
                              @RequestBody @Valid Mono<CsSessionContactRequest> body) {
        return body.flatMap(request -> sessionService.updateContact(id, token, request));
    }

    @PostMapping("/{id}/_close")
    @Operation(summary = "访客结束会话")
    public Mono<Void> close(@PathVariable String id, @RequestParam String token) {
        return sessionService.visitorClose(id, token);
    }

    @PostMapping("/{id}/_rate")
    @Operation(summary = "访客评价")
    public Mono<Void> rate(@PathVariable String id,
                           @RequestParam String token,
                           @RequestBody @Valid Mono<CsSessionRateRequest> body) {
        return body.flatMap(request -> sessionService.rate(id, token, request));
    }

    private Mono<Void> limit(org.jetlinks.community.cs.service.CsInboxRateLimiter limiter, String key, String what) {
        if (limiter.tryAcquire(key)) {
            return Mono.empty();
        }
        log.warn("customer-service chat {} rate limited, key={}", what, key);
        return Mono.error(new BusinessException("error.cs_chat_rate_limited", TOO_MANY_REQUESTS));
    }
}
