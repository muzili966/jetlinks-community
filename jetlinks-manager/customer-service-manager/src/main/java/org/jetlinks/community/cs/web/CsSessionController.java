package org.jetlinks.community.cs.web;

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
import org.jetlinks.community.cs.CsConstants;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.entity.CsAgentEntity;
import org.jetlinks.community.cs.entity.CsChatMessageEntity;
import org.jetlinks.community.cs.entity.CsLeadEntity;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.cs.card.CsCardKindView;
import org.jetlinks.community.cs.card.CsCardService;
import org.jetlinks.community.cs.service.CsAgentService;
import org.jetlinks.community.cs.service.CsSessionService;
import org.jetlinks.community.cs.service.request.CsAgentStatusRequest;
import org.jetlinks.community.cs.service.request.CsCardSendRequest;
import org.jetlinks.community.cs.service.request.CsChatMessageRequest;
import org.jetlinks.community.cs.service.request.CsSessionCloseRequest;
import org.jetlinks.community.cs.service.request.CsSessionLeadRequest;
import org.jetlinks.community.cs.service.request.CsSessionTransferRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 坐席工作台接口. 实时事件走 WebSocket 订阅 {@code /cs/workbench}, 这里是动作与查询.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@ConditionalOnProperty(prefix = "customer-service", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/cs/session")
@Authorize
@Resource(id = CsConstants.RESOURCE_SESSION, name = "客服在线会话")
@AllArgsConstructor
@Getter
@Tag(name = "客服在线会话(坐席)")
public class CsSessionController implements ReactiveServiceQueryController<CsSessionEntity, String> {

    private final CsProperties properties;
    private final CsSessionService service;
    private final CsAgentService agentService;
    private final CsCardService cardService;

    @Override
    public ReactiveCrudService<CsSessionEntity, String> getService() {
        return service;
    }

    // ---------- 坐席状态 ----------

    @GetMapping("/agent/_me")
    @QueryAction
    @Operation(summary = "当前用户的坐席状态")
    public Mono<CsAgentEntity> me() {
        return agentService.current();
    }

    @PostMapping("/agent/_status")
    @SaveAction
    @Operation(summary = "切换坐席状态(上线 / 忙碌 / 下线)")
    public Mono<CsAgentEntity> status(@RequestBody @Valid Mono<CsAgentStatusRequest> body) {
        return body.flatMap(agentService::updateStatus);
    }

    @GetMapping("/agent/_online")
    @QueryAction
    @Operation(summary = "在线坐席列表(供转接)")
    public Flux<CsAgentEntity> online() {
        return agentService.findOnline();
    }

    // ---------- 会话 ----------

    @GetMapping("/_queue")
    @QueryAction
    @Operation(summary = "排队中的会话(先到先接)")
    public Flux<CsSessionEntity> queue() {
        return service.queue();
    }

    @GetMapping("/_mine")
    @QueryAction
    @Operation(summary = "我正在接待的会话")
    public Flux<CsSessionEntity> mine() {
        return service.mine();
    }

    @GetMapping("/{id}/messages")
    @QueryAction
    @Operation(summary = "会话消息(按时间正序)")
    public Flux<CsChatMessageEntity> messages(@PathVariable String id) {
        return service.history(id);
    }

    @PostMapping("/{id}/_accept")
    @SaveAction
    @Operation(summary = "接入排队中的会话")
    public Mono<CsSessionEntity> accept(@PathVariable String id) {
        return service.accept(id);
    }

    @PostMapping("/{id}/message")
    @SaveAction
    @Operation(summary = "坐席发送消息")
    public Mono<CsChatMessageEntity> send(@PathVariable String id, @RequestBody @Valid Mono<CsChatMessageRequest> body) {
        return body.flatMap(request -> service.agentMessage(id, request.validated(properties.getChat().getMessageMaxLength())));
    }

    @PostMapping(value = "/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaveAction
    @Operation(summary = "坐席发送图片 / 视频 / 文件(multipart 字段 file)")
    public Mono<CsChatMessageEntity> attachment(@PathVariable String id, @RequestPart("file") Mono<FilePart> file) {
        return file.flatMap(part -> service.agentAttachment(id, part));
    }

    @GetMapping("/{id}/card-kinds")
    @QueryAction
    @Operation(summary = "这个会话可以发送的卡片种类(续费卡片只对租户会话开放)")
    public Flux<CsCardKindView> cardKinds(@PathVariable String id) {
        return cardService.kinds(id);
    }

    @PostMapping("/{id}/card")
    @SaveAction
    @Operation(summary = "发送卡片; 内容由后端按种类生成, 金额与支付链接不接受前端传入")
    public Mono<CsChatMessageEntity> sendCard(@PathVariable String id, @RequestBody @Valid Mono<CsCardSendRequest> body) {
        return body.flatMap(request -> cardService.send(id, request));
    }

    @PostMapping("/{id}/_read")
    @SaveAction
    @Operation(summary = "坐席已读")
    public Mono<Void> read(@PathVariable String id) {
        return service.agentRead(id);
    }

    @PostMapping("/{id}/_close")
    @SaveAction
    @Operation(summary = "结束会话并打标签")
    public Mono<Void> close(@PathVariable String id, @RequestBody Mono<CsSessionCloseRequest> body) {
        return body
            .defaultIfEmpty(new CsSessionCloseRequest())
            .flatMap(request -> service.agentClose(id, request.getTags()));
    }

    @PostMapping("/{id}/_transfer")
    @SaveAction
    @Operation(summary = "转接给其他在线坐席")
    public Mono<CsSessionEntity> transfer(@PathVariable String id, @RequestBody @Valid Mono<CsSessionTransferRequest> body) {
        return body.flatMap(request -> service.transfer(id, request.getAgentId()));
    }

    @PostMapping("/{id}/_lead")
    @SaveAction
    @Operation(summary = "会话转为线索")
    public Mono<CsLeadEntity> toLead(@PathVariable String id, @RequestBody Mono<CsSessionLeadRequest> body) {
        return body
            .defaultIfEmpty(new CsSessionLeadRequest())
            .flatMap(request -> service.toLead(id, request));
    }
}
