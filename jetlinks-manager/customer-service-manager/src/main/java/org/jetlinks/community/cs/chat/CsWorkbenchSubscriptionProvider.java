package org.jetlinks.community.cs.chat;

import lombok.RequiredArgsConstructor;
import org.jetlinks.community.gateway.external.Message;
import org.jetlinks.community.gateway.external.SubscribeRequest;
import org.jetlinks.community.gateway.external.SubscriptionProvider;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import reactor.core.publisher.Flux;

/**
 * 坐席工作台通过平台 WebSocket({@code /messaging}) 订阅 {@code /cs/workbench},
 * 收到的是分配给自己的会话事件与排队变化.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class CsWorkbenchSubscriptionProvider implements SubscriptionProvider {

    static final String ID = "cs-workbench";

    private final EventBus eventBus;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "客服工作台推送";
    }

    @Override
    public String[] getTopicPattern() {
        return new String[]{CsChatTopics.WORKBENCH};
    }

    @Override
    public Flux<Message> subscribe(SubscribeRequest request) {
        String userId = request.getAuthentication().getUser().getId();
        return eventBus
            .subscribe(Subscription.of(
                ID + ":" + userId,
                topicsFor(userId),
                Subscription.Feature.local, Subscription.Feature.broker, Subscription.Feature.safetySerialization
            ))
            .map(msg -> Message.success(request.getId(), msg.getTopic(), msg.bodyToJson(true)));
    }

    static String[] topicsFor(String userId) {
        return new String[]{CsChatTopics.agent(userId), CsChatTopics.QUEUE};
    }
}
