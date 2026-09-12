package org.jetlinks.community.cs.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.auth.entity.UserDetailEntity;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.chat.CsSessionView;
import org.jetlinks.community.cs.chat.CsSupportIdentity;
import org.jetlinks.community.cs.entity.CsChatMessageEntity;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.cs.service.CsAgentService;
import org.jetlinks.community.cs.service.CsSessionService;
import org.jetlinks.community.cs.service.request.CsChatMessageRequest;
import org.jetlinks.community.cs.service.request.CsSessionRateRequest;
import org.jetlinks.community.cs.service.request.CsSupportOpenRequest;
import org.jetlinks.community.tenant.context.TenantContext;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.service.TenantService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 控制台里「联系客服」用的接口: 登录即可用, 不需要额外授权(每个租户用户都该能找到客服).
 * 身份、联系方式、所属租户由后端按当前登录用户自动登记; 实时消息走平台 WebSocket 订阅 {@code /cs/my-session}.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@ConditionalOnProperty(prefix = "customer-service", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/cs/support")
@Authorize
@RequiredArgsConstructor
@Tag(name = "联系客服(控制台用户)")
public class CsSupportController {

    private static final int HISTORY_SESSION_LIMIT = 10;

    private final CsProperties properties;
    private final CsSessionService sessionService;
    private final CsAgentService agentService;
    private final ReactiveRepository<UserDetailEntity, String> userDetailRepository;
    private final ObjectProvider<TenantService> tenantService;

    @GetMapping("/_status")
    @Operation(summary = "是否有坐席在线, 以及我当前进行中的会话")
    public Mono<Map<String, Object>> status() {
        return currentUserId()
            .flatMap(userId -> Mono
                .zip(agentService.hasOnline(),
                     sessionService
                         .findOpenForUser(userId)
                         .map(session -> Optional.of(CsSessionView.of(session)))
                         .defaultIfEmpty(Optional.empty()))
                .map(tuple -> statusOf(tuple.getT1(), tuple.getT2().orElse(null))));
    }

    private static Map<String, Object> statusOf(boolean agentsOnline, CsSessionView session) {
        Map<String, Object> result = new HashMap<>();
        result.put("agentsOnline", agentsOnline);
        result.put("session", session);
        return result;
    }

    @PostMapping("/_open")
    @Operation(summary = "发起会话; 已有进行中的会话直接返回它")
    public Mono<CsSessionView> open(@RequestBody @Valid Mono<CsSupportOpenRequest> body, ServerWebExchange exchange) {
        ClientInfo client = ClientInfo.from(exchange.getRequest());
        return body.flatMap(request -> identity()
            .flatMap(who -> sessionService.openForUser(who, request, client)));
    }

    @GetMapping("/_sessions")
    @Operation(summary = "我最近的会话(含已结束), 用于展示历史对话")
    public Flux<CsSessionEntity> sessions() {
        return currentUserId().flatMapMany(userId -> sessionService.recentForUser(userId, HISTORY_SESSION_LIMIT));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "某次会话的消息(按时间正序)")
    public Flux<CsChatMessageEntity> messages(@PathVariable String id) {
        return currentUserId()
            .flatMap(userId -> sessionService.findForUser(id, userId))
            .flatMapMany(session -> sessionService.history(id));
    }

    @PostMapping("/{id}/message")
    @Operation(summary = "发送消息")
    public Mono<CsChatMessageEntity> send(@PathVariable String id, @RequestBody @Valid Mono<CsChatMessageRequest> body) {
        return Mono
            .zip(currentUserId(), body)
            .flatMap(tuple -> sessionService
                .userMessage(id, tuple.getT1(), tuple.getT2().validated(properties.getChat().getMessageMaxLength())));
    }

    @PostMapping(value = "/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "发送图片 / 视频 / 文件(multipart 字段 file)")
    public Mono<CsChatMessageEntity> attachment(@PathVariable String id, @RequestPart("file") Mono<FilePart> file) {
        return Mono
            .zip(currentUserId(), file)
            .flatMap(tuple -> sessionService.userAttachment(id, tuple.getT1(), tuple.getT2()));
    }

    @PostMapping("/{id}/_read")
    @Operation(summary = "标记已读")
    public Mono<Void> read(@PathVariable String id) {
        return currentUserId().flatMap(userId -> sessionService.userRead(id, userId));
    }

    @PostMapping("/{id}/_close")
    @Operation(summary = "结束会话")
    public Mono<Void> close(@PathVariable String id) {
        return currentUserId().flatMap(userId -> sessionService.userClose(id, userId));
    }

    @PostMapping("/{id}/_rate")
    @Operation(summary = "评价")
    public Mono<Void> rate(@PathVariable String id, @RequestBody @Valid Mono<CsSessionRateRequest> body) {
        return Mono
            .zip(currentUserId(), body)
            .flatMap(tuple -> sessionService.userRate(id, tuple.getT1(), tuple.getT2()));
    }

    /**
     * 自动登记: 账号姓名 + 用户详情里的手机号 + 绑定的租户.
     * 手机号或租户缺失都不影响发起会话, 只是坐席少一点上下文.
     */
    private Mono<CsSupportIdentity> identity() {
        return currentAuth()
            .flatMap(auth -> {
                CsSupportIdentity base = CsSupportIdentity.of(auth.getUser().getId(), auth.getUser().getName());
                return userDetailRepository
                    .findById(auth.getUser().getId())
                    .map(detail -> base.withTelephone(detail.getTelephone()))
                    .defaultIfEmpty(base)
                    .flatMap(who -> withTenant(who, auth));
            });
    }

    private Mono<CsSupportIdentity> withTenant(CsSupportIdentity who, Authentication auth) {
        TenantService tenants = tenantService.getIfAvailable();
        String tenantId = TenantContext.currentTenant(auth).orElse(null);
        if (tenants == null || tenantId == null) {
            return Mono.just(who);
        }
        return tenants
            .findById(tenantId)
            .map(TenantEntity::getName)
            .defaultIfEmpty(tenantId)
            .map(name -> who.withTenant(tenantId, name));
    }

    private Mono<Authentication> currentAuth() {
        return Authentication
            .currentReactive()
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.unauthorized", 401)));
    }

    private Mono<String> currentUserId() {
        return currentAuth().map(auth -> auth.getUser().getId());
    }
}
