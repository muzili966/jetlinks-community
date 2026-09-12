package org.jetlinks.community.cs.chat;

/**
 * 会话事件在 EventBus 上的主题.
 * <pre>
 *   /cs/session/{sessionId}   某个会话的消息与状态变化(访客 SSE 与坐席工作台都订阅)
 *   /cs/agent/{agentId}       分配给某坐席的事件(新会话分配、转接进入)
 *   /cs/queue                 排队变化(新排队会话、被接入), 全部坐席可见
 * </pre>
 *
 * @author customer-service-manager
 * @since 2.11
 */
public final class CsChatTopics {

    public static final String SESSION_PREFIX = "/cs/session/";
    public static final String AGENT_PREFIX = "/cs/agent/";
    public static final String QUEUE = "/cs/queue";
    /** 坐席工作台通过 WebSocket 订阅的入口主题 */
    public static final String WORKBENCH = "/cs/workbench";
    /** 控制台用户订阅自己会话的入口主题, 订阅参数带 sessionId */
    public static final String MY_SESSION = "/cs/my-session";

    private CsChatTopics() {
    }

    public static String session(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    public static String agent(String agentId) {
        return AGENT_PREFIX + agentId;
    }
}
