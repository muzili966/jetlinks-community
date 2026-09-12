package org.jetlinks.community.cs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 客服模块配置.
 *
 * <pre>{@code
 * customer-service:
 *   enabled: true
 *   agent-role-id: cs-agent
 *   supervisor-role-id: cs-supervisor
 *   inbox:
 *     rate-limit-per-hour: 5
 *     retention-days: 365
 *   notify:
 *     in-app: true
 *     type: dingTalk        # 可选: 额外推送到外部渠道
 *     notifier-id: xxx
 *     template-id: xxx
 * }</pre>
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "customer-service")
public class CsProperties {

    /**
     * 总开关: 关闭后模块所有接口与后台任务停用, 表保留(回滚开关)
     */
    private boolean enabled = true;

    /**
     * 客服坐席角色ID, 拥有此角色的用户收到新留言的站内通知
     */
    private String agentRoleId = "cs-agent";

    /**
     * 客服主管角色ID, 可指派线索
     */
    private String supervisorRoleId = "cs-supervisor";

    private Inbox inbox = new Inbox();

    private Notify notify = new Notify();

    @Getter
    @Setter
    public static class Inbox {

        /**
         * 同一 IP 每小时最多提交的留言数(匿名接口防刷)
         */
        private int rateLimitPerHour = 5;

        /**
         * 留言正文最大长度
         */
        private int contentMaxLength = 2000;

        /**
         * 留言保留天数, 超期的留言记录自动清理; 线索作为业务记录不清理
         */
        private int retentionDays = 365;

        /**
         * 清理任务执行周期
         */
        private Duration cleanInterval = Duration.ofHours(6);
    }

    @Getter
    @Setter
    public static class Notify {

        /**
         * 新留言是否发送站内通知(通知中心铃铛)给全部客服坐席与主管
         */
        private boolean inApp = true;

        /**
         * 额外推送的外部通知类型, 如 dingTalk / weixin / sms; 留空不推送
         */
        private String type;

        /**
         * 外部通知配置ID(通知管理中配置)
         */
        private String notifierId;

        /**
         * 外部通知模板ID, 模板变量: name, contact, company, content, sourcePage, time
         */
        private String templateId;

        public boolean isExternalConfigured() {
            return type != null && !type.isBlank()
                && notifierId != null && !notifierId.isBlank()
                && templateId != null && !templateId.isBlank();
        }
    }
}
