package org.jetlinks.community.cs;

/**
 * 客服模块常量.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public interface CsConstants {

    /**
     * 线索资源权限ID(前端权限码 cs-lead:query / cs-lead:save / cs-lead:delete)
     */
    String RESOURCE_LEAD = "cs-lead";

    /**
     * 留言资源权限ID
     */
    String RESOURCE_INBOX = "cs-inbox";

    /**
     * 在线会话资源权限ID(坐席工作台)
     */
    String RESOURCE_SESSION = "cs-session";

    /**
     * 站内通知的 topicProvider, 通知中心据此分组
     */
    String NOTIFY_TOPIC_PROVIDER = "cs-inbox";

    String NOTIFY_TOPIC_NAME = "官网新留言";

    /**
     * 匿名留言接口前缀, 网关 / Nginx 侧可据此单独限流
     */
    String PUBLIC_PATH = "/cs/public";
}
