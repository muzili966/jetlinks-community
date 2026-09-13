package org.jetlinks.community.cs.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetlinks.community.cs.entity.CsChatMessageEntity;
import org.jetlinks.community.cs.entity.CsSessionEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * 推给访客与坐席的会话事件. 通过 EventBus 传递时序列化为 Map, 两端按 type 分发.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CsSessionEvent {

    public static final String TYPE_MESSAGE = "message";
    public static final String TYPE_QUEUED = "queued";
    public static final String TYPE_ACCEPTED = "accepted";
    public static final String TYPE_TRANSFERRED = "transferred";
    public static final String TYPE_CLOSED = "closed";
    public static final String TYPE_CONTACT = "contact";
    /** 卡片状态变化(如续费卡片已支付), message 为更新后的卡片消息 */
    public static final String TYPE_CARD = "card";

    private String type;
    private String sessionId;
    private CsSessionView session;
    private CsChatMessageEntity message;
    private long time;

    public static CsSessionEvent of(String type, CsSessionEntity session) {
        return new CsSessionEvent(type, session.getId(), CsSessionView.of(session), null, System.currentTimeMillis());
    }

    public static CsSessionEvent message(CsSessionEntity session, CsChatMessageEntity message) {
        return new CsSessionEvent(TYPE_MESSAGE, session.getId(), CsSessionView.of(session), message, System.currentTimeMillis());
    }

    /**
     * EventBus 载荷用 Map, 避免消费方反序列化依赖本模块的类
     */
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("sessionId", sessionId);
        payload.put("session", session);
        payload.put("message", message);
        payload.put("time", time);
        return payload;
    }
}
