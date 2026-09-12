package org.jetlinks.community.cs.chat;

import lombok.RequiredArgsConstructor;
import org.jetlinks.core.event.EventBus;
import reactor.core.publisher.Mono;

/**
 * 把会话事件发布到 EventBus: 会话主题总是发; 有坐席时同时发给坐席主题, 否则发到队列主题.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class CsChatEventPublisher {

    private final EventBus eventBus;

    public Mono<Void> publish(CsSessionEvent event, String agentId) {
        Mono<Long> toSession = eventBus.publish(CsChatTopics.session(event.getSessionId()), event.toPayload());
        Mono<Long> toHandler = agentId == null || agentId.isBlank()
            ? eventBus.publish(CsChatTopics.QUEUE, event.toPayload())
            : eventBus.publish(CsChatTopics.agent(agentId), event.toPayload());
        return toSession.then(toHandler).then();
    }

    /** 转接: 旧坐席与新坐席都要收到 */
    public Mono<Void> publishTransfer(CsSessionEvent event, String fromAgentId, String toAgentId) {
        return publish(event, toAgentId)
            .then(fromAgentId == null ? Mono.empty() : eventBus.publish(CsChatTopics.agent(fromAgentId), event.toPayload()).then());
    }

    /** 排队状态变化(新排队、被接入)广播给全部坐席 */
    public Mono<Void> publishQueue(CsSessionEvent event) {
        return eventBus.publish(CsChatTopics.QUEUE, event.toPayload()).then();
    }
}
