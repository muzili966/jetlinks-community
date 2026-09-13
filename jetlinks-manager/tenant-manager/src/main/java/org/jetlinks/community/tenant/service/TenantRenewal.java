package org.jetlinks.community.tenant.service;

import lombok.Builder;
import lombok.Getter;

/**
 * 一笔已生成、等待付款的续费: 待支付订单 + 支付单.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@Builder
public class TenantRenewal {

    private final String tenantId;
    private final String tenantName;
    private final String planName;
    private final int months;

    /** 订单金额, 单位: 元(与租户订单表一致) */
    private final long totalAmount;

    /** 当前到期时间; 未订阅为空 */
    private final Long currentExpireTime;

    /** 按"现在付款"预估的续费后到期时间; 实际以到账时刻重算为准 */
    private final long expireTimeAfterPreview;

    private final String tenantOrderId;
    private final String payOrderId;
    private final String subject;

    /** 支付单金额, 单位: 分 */
    private final long payAmount;
    private final Long payExpireAt;
}
