package org.jetlinks.community.tenant.entity;

import java.util.Map;
import java.util.Optional;

/**
 * 配额持有者: 租户与套餐共用的配额解析逻辑.
 *
 * @author tenant-manager
 * @since 2.11
 */
public interface QuotaHolder {

    Map<String, Object> getQuota();

    default Optional<Long> getQuota(String key) {
        Map<String, Object> quota = getQuota();
        if (quota == null) {
            return Optional.empty();
        }
        return Optional
            .ofNullable(quota.get(key))
            .map(v -> v instanceof Number ? ((Number) v).longValue() : Long.parseLong(String.valueOf(v)));
    }
}
