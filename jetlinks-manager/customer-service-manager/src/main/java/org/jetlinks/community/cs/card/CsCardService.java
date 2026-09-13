package org.jetlinks.community.cs.card;

import lombok.RequiredArgsConstructor;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.exception.BusinessException;
import org.hswebframework.web.exception.NotFoundException;
import org.jetlinks.community.cs.entity.CsChatMessageEntity;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.cs.service.CsSessionService;
import org.jetlinks.community.cs.service.request.CsCardSendRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 坐席发卡片: 按种类找到扩展点, 校验这个会话能不能发, 生成内容, 作为消息发出.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class CsCardService {

    private final CsCardRegistry registry;
    private final CsSessionService sessionService;

    public Flux<CsCardKindView> kinds(String sessionId) {
        return sessionService
            .findById(sessionId)
            .switchIfEmpty(Mono.error(() -> new NotFoundException("error.cs_session_not_found")))
            .flatMapIterable(registry::available)
            .map(CsCardKindView::of);
    }

    public Mono<CsChatMessageEntity> send(String sessionId, CsCardSendRequest request) {
        return Authentication
            .currentReactive()
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.unauthorized", 401)))
            .flatMap(auth -> sessionService.agentCard(sessionId, session -> build(session, request, auth.getUser().getId())));
    }

    Mono<CsCardDraft> build(CsSessionEntity session, CsCardSendRequest request, String operatorId) {
        CsCardProvider provider = registry.required(request.getKind());
        if (!provider.supports(session)) {
            return Mono.error(new BusinessException("error.cs_card_not_supported_for_session", 400, request.getKind()));
        }
        return provider.build(new CsCardContext(session, request.getParams(), operatorId));
    }
}
