package org.jetlinks.community.tenant;

/**
 * 内置订阅套餐常量. 价格与配额为初始值, 平台管理员可在套餐管理中调整.
 *
 * @author tenant-manager
 * @since 2.11
 */
public interface TenantPlanConstants {

    String PLAN_FREE = "free";
    String PLAN_STANDARD = "standard";
    String PLAN_ULTIMATE = "ultimate";

    /**
     * 月价, 单位: 元
     */
    long PRICE_FREE = 0L;
    long PRICE_STANDARD = 1800L;
    long PRICE_ULTIMATE = 3600L;

    /**
     * 配额key(与 {@link TenantConstants#QUOTA_MAX_DEVICE} 同一命名空间)
     */
    String QUOTA_MAX_PRODUCT = "maxProductCount";
    String QUOTA_DATA_RETENTION_DAYS = "dataRetentionDays";
}
