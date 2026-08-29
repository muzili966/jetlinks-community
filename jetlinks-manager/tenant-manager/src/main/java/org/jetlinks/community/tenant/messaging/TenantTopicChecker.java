package org.jetlinks.community.tenant.messaging;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.exception.AccessDenyException;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.cache.ProductTenantCache;
import reactor.core.publisher.Mono;

/**
 * 订阅topic的租户归属校验.
 * <ul>
 *     <li>topic 必须命中白名单前缀, 否则拒绝(fail-closed)</li>
 *     <li>设备类topic({@code /device/{productId}/...}, {@code /dashboard/device/{productId}/...}):
 *         productId 必须归属当前租户, 且不允许通配符跨产品订阅</li>
 *     <li>其他白名单topic(告警/场景/通知): 由各 SubscriptionProvider 按创建人过滤, 放行</li>
 * </ul>
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
@AllArgsConstructor
public class TenantTopicChecker {

    private static final String DEVICE_TOPIC_PREFIX = "/device/";
    private static final String DASHBOARD_DEVICE_TOPIC_PREFIX = "/dashboard/device/";
    private static final String WILDCARD = "*";

    private final ProductTenantCache cache;
    private final TenantProperties properties;

    public Mono<Void> check(String topic, String tenantId) {
        if (!isWhitelisted(topic)) {
            log.warn("tenant [{}] subscribe topic [{}] denied: not whitelisted", tenantId, topic);
            return Mono.error(new AccessDenyException());
        }
        String productId = extractProductId(topic);
        if (productId == null) {
            return Mono.empty();
        }
        if (WILDCARD.equals(productId) || !cache.belongsTo(productId, tenantId)) {
            log.warn("tenant [{}] subscribe topic [{}] denied: product not owned", tenantId, topic);
            return Mono.error(new AccessDenyException());
        }
        return Mono.empty();
    }

    private boolean isWhitelisted(String topic) {
        return properties
            .getSubscribeTopicPrefixes()
            .stream()
            .anyMatch(topic::startsWith);
    }

    private String extractProductId(String topic) {
        if (topic.startsWith(DASHBOARD_DEVICE_TOPIC_PREFIX)) {
            return segmentAfter(topic, DASHBOARD_DEVICE_TOPIC_PREFIX);
        }
        if (topic.startsWith(DEVICE_TOPIC_PREFIX)) {
            return segmentAfter(topic, DEVICE_TOPIC_PREFIX);
        }
        return null;
    }

    private String segmentAfter(String topic, String prefix) {
        String rest = topic.substring(prefix.length());
        int idx = rest.indexOf('/');
        return idx < 0 ? rest : rest.substring(0, idx);
    }
}
