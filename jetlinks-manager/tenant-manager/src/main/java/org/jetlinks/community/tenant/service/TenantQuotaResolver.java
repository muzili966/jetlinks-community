package org.jetlinks.community.tenant.service;

import lombok.AllArgsConstructor;
import org.jetlinks.community.tenant.TenantPlanConstants;
import org.jetlinks.community.tenant.entity.TenantEntity;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * 租户生效配额解析, 优先级:
 * <ol>
 *     <li>租户级配额覆盖(平台对单租户特批)</li>
 *     <li>订阅套餐配额(订阅到期时降级为免费版)</li>
 *     <li>都未配置: 不限制</li>
 * </ol>
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
public class TenantQuotaResolver {

    private final TenantService tenantService;
    private final TenantPlanService planService;

    /**
     * 解析租户在某配额项上的生效限额, empty 表示不限制
     */
    public Mono<Optional<Long>> resolve(String tenantId, String quotaKey) {
        return tenantService
            .findById(tenantId)
            .flatMap(tenant -> tenant
                .getQuota(quotaKey)
                .map(own -> Mono.just(Optional.of(own)))
                .orElseGet(() -> planQuota(tenant, quotaKey)))
            .defaultIfEmpty(Optional.empty());
    }

    private Mono<Optional<Long>> planQuota(TenantEntity tenant, String quotaKey) {
        return planService
            .findById(effectivePlanId(tenant))
            .map(plan -> plan.getQuota(quotaKey))
            .defaultIfEmpty(Optional.empty());
    }

    /**
     * 生效套餐: 未订阅或订阅到期一律按免费版
     */
    private String effectivePlanId(TenantEntity tenant) {
        if (tenant.getPlanId() == null || tenant.isSubscribeExpired()) {
            return TenantPlanConstants.PLAN_FREE;
        }
        return tenant.getPlanId();
    }
}
