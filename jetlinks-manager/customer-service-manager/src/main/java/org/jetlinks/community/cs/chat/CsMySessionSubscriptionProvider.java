package org.jetlinks.community.cs.chat;

import lombok.RequiredArgsConstructor;
import org.jetlinks.community.cs.service.CsSessionService;
import org.jetlinks.community.gateway.external.Message;
import org.jetlinks.community.gateway.external.SubscribeRequest;
import org.jetlinks.community.gateway.external.SubscriptionProvider;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import reactor.core.publisher.Flux;

/**
 * 控制台用户订阅自己那条会话的事件. 订阅参数 {@code sessionId}, 订阅前校验会话确实属于当前登录用户,
 * 否则任何人都能订阅别人的会话.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class CsMySessionSubscriptionProvider implements SubscriptionProvider {

    static final String ID = "cs-my-session";
    static final String PARAMETER_SESSION_ID = "sessionId";

    private final EventBus eventBus;
    private final CsSessionService sessionService;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "客服会话推送(用户)";
    }

    @Override
    public String[] getTopicPattern() {
        return new String[]{CsChatTopics.MY_SESSION};
    }

    /** parameter 可能为空, 不能直接用 ValueObject#getString */
    static String sessionIdOf(SubscribeRequest request) {
        return request.getParameter() == null
            ? null
            : String.valueOf(request.getParameter().getOrDefault(PARAMETER_SESSION_ID, ""));
    }

    @Override
    public Flux<Message> subscribe(SubscribeRequest request) {
        String userId = request.getAuthentication().getUser().getId();
        String sessionId = sessionIdOf(request);
        if (sessionId == null || sessionId.isBlank()) {
            return Flux.just(Message.error(request.getId(), CsChatTopics.MY_SESSION, "缺少参数 sessionId"));
        }
        return sessionService
            .findForUser(sessionId, userId)
            .flatMapMany(session -> eventBus
                .subscribe(Subscription.of(
                    ID + ":" + userId + ":" + sessionId,
                    CsChatTopics.session(sessionId),
                    Subscription.Feature.local, Subscription.Feature.broker, Subscription.Feature.safetySerialization
                ))
                .map(msg -> Message.success(request.getId(), msg.getTopic(), msg.bodyToJson(true))));
    }
}
