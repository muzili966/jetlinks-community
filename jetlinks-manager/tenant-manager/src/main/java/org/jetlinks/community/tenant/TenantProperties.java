package org.jetlinks.community.tenant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 多租户配置.
 *
 * <pre>{@code
 * tenant:
 *   enabled: true
 *   platform-admin-role-id: platform-admin
 *   time-series-prefix: true
 * }</pre>
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {

    /**
     * 总开关: 关闭时所有租户逻辑旁路, 系统行为与单租户版本完全一致(灰度与回滚开关)
     */
    private boolean enabled = false;

    /**
     * 平台管理员角色ID, 拥有此角色的用户不受租户过滤(配合审计使用)
     */
    private String platformAdminRoleId = "platform-admin";

    /**
     * 是否对时序数据表名添加租户前缀
     */
    private boolean timeSeriesPrefix = true;

    /**
     * 允许租户用户订阅的topic白名单前缀(WebSocket)
     */
    private List<String> subscribeTopicPrefixes = new ArrayList<>(Arrays.asList(
        "/device", "/dashboard/device", "/alarm", "/scene", "/notifications"
    ));

    /**
     * 订阅到期前多少天开始提醒
     */
    private int expireNotifyDays = 7;

    /**
     * 到期扫描周期
     */
    private Duration expireCheckInterval = Duration.ofHours(1);
}
