package org.jetlinks.community.tenant.quota;

import lombok.AllArgsConstructor;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.crud.events.EntityPrepareCreateEvent;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.device.entity.DeviceInstanceEntity;
import org.jetlinks.community.device.entity.DeviceProductEntity;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantPlanConstants;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.context.TenantContext;
import org.jetlinks.community.tenant.service.TenantQuotaResolver;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Optional;

/**
 * 订阅配额检查(阶段6): 新建设备/产品前校验租户生效配额(套餐+租户覆盖), 超额拒绝.
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
public class TenantQuotaListener {

    private final TenantProperties properties;
    private final TenantQuotaResolver quotaResolver;
    private final ReactiveRepository<DeviceInstanceEntity, String> deviceRepository;
    private final ReactiveRepository<DeviceProductEntity, String> productRepository;

    @EventListener
    public void handleDeviceCreate(EntityPrepareCreateEvent<DeviceInstanceEntity> event) {
        if (!properties.isEnabled()
            || !DeviceInstanceEntity.class.isAssignableFrom(event.getEntityType())) {
            return;
        }
        event.async(checkQuota(
            TenantConstants.QUOTA_MAX_DEVICE,
            event.getEntity().size(),
            tenantId -> count(deviceRepository, tenantId),
            "error.tenant_device_quota_exceeded"));
    }

    @EventListener
    public void handleProductCreate(EntityPrepareCreateEvent<DeviceProductEntity> event) {
        if (!properties.isEnabled()
            || !DeviceProductEntity.class.isAssignableFrom(event.getEntityType())) {
            return;
        }
        event.async(checkQuota(
            TenantPlanConstants.QUOTA_MAX_PRODUCT,
            event.getEntity().size(),
            tenantId -> count(productRepository, tenantId),
            "error.tenant_product_quota_exceeded"));
    }

    private Mono<Void> checkQuota(String quotaKey,
                                  int creating,
                                  java.util.function.Function<String, Mono<Integer>> counter,
                                  String errorCode) {
        return Authentication
            .currentReactive()
            .flatMap(auth -> {
                TenantContext.Resolution resolution =
                    TenantContext.resolve(auth, Context.empty(), properties.getPlatformAdminRoleId());
                return resolution
                    .getTenantId()
                    // 平台管理员不受配额限制; 无租户用户由fail-closed兜底
                    .map(tenantId -> assertUnderQuota(tenantId, quotaKey, creating, counter, errorCode))
                    .orElse(Mono.empty());
            });
    }

    private Mono<Void> assertUnderQuota(String tenantId,
                                        String quotaKey,
                                        int creating,
                                        java.util.function.Function<String, Mono<Integer>> counter,
                                        String errorCode) {
        return quotaResolver
            .resolve(tenantId, quotaKey)
            .flatMap(limit -> limit
                .map(max -> counter
                    .apply(tenantId)
                    .flatMap(current -> current + creating > max
                        ? Mono.<Void>error(new BusinessException(errorCode, 400, max))
                        : Mono.<Void>empty()))
                .orElse(Mono.empty()));
    }

    private <T> Mono<Integer> count(ReactiveRepository<T, String> repository, String tenantId) {
        return repository
            .createQuery()
            .where(TenantConstants.TENANT_ID_PROPERTY, tenantId)
            .count();
    }
}
