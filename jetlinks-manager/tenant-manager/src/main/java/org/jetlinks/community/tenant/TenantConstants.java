package org.jetlinks.community.tenant;

import org.jetlinks.core.config.ConfigKey;

/**
 * 多租户相关常量.
 *
 * @author tenant-manager
 * @since 2.11
 */
public interface TenantConstants {

    /**
     * 租户实体上的属性名(对应列 tenant_id)
     */
    String TENANT_ID_PROPERTY = "tenantId";

    /**
     * fail-closed 兜底值: 用户无租户维度时注入此租户号, 保证查不到任何数据而不是查到全部
     */
    String NO_TENANT = "__NO_TENANT__";

    /**
     * 反查缓存未命中时的隔离前缀, 数据落入隔离区而不是别的租户表
     */
    String UNKNOWN_TENANT = "unknown";

    /**
     * 存量数据迁移的默认租户
     */
    String DEFAULT_TENANT_ID = "default";

    /**
     * 平台管理员跨租户操作时携带目标租户的请求头
     */
    String IMPERSONATE_HEADER = "X-Tenant-Id";

    /**
     * Reactor Context 中携带代理租户ID的key
     */
    String IMPERSONATE_CONTEXT_KEY = "tenant-impersonate";

    /**
     * 设备/产品配置中携带租户ID的key,
     * 与 {@link org.jetlinks.community.PropertyConstants#creatorId} 同机制
     */
    ConfigKey<String> tenantId = ConfigKey.of(TENANT_ID_PROPERTY, "租户ID", String.class);

    /**
     * 配额key: 最大设备数
     */
    String QUOTA_MAX_DEVICE = "maxDeviceCount";
}
