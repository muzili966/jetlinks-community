package org.jetlinks.community.cs.chat;

import lombok.RequiredArgsConstructor;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * 访客侧 SSE: 订阅自己会话的主题, 混入心跳注释帧防止代理超时断开.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class CsVisitorEventStream {

    static final String HEARTBEAT_COMMENT = "ping";

    private final EventBus eventBus;

    public Flux<ServerSentEvent<Object>> stream(String sessionId, Duration heartbeat) {
        Flux<ServerSentEvent<Object>> events = eventBus
            .subscribe(Subscription.of(
                "cs-visitor:" + sessionId,
                CsChatTopics.session(sessionId),
                Subscription.Feature.local, Subscription.Feature.broker, Subscription.Feature.safetySerialization
            ))
            .map(msg -> ServerSentEvent.builder().data(msg.bodyToJson(true)).build());
        Flux<ServerSentEvent<Object>> pings = Flux
            .interval(heartbeat, heartbeat)
            .map(ignore -> ServerSentEvent.builder().comment(HEARTBEAT_COMMENT).build());
        return Flux.merge(events, pings);
    }
}
