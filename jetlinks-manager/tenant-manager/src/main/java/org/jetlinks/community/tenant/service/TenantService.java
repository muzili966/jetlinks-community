package org.jetlinks.community.tenant.service;

import lombok.AllArgsConstructor;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.system.authorization.api.entity.UserEntity;
import org.apache.commons.collections4.CollectionUtils;
import org.hswebframework.web.crud.service.GenericReactiveCacheSupportCrudService;
import org.hswebframework.web.exception.BusinessException;
import org.hswebframework.web.system.authorization.defaults.service.DefaultDimensionUserService;
import org.jetlinks.community.auth.utils.DimensionUserBindUtils;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantDimensionType;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.enums.TenantState;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * 租户管理: CRUD + 用户绑定(复用 s_dimension_user 维度绑定机制).
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
public class TenantService extends GenericReactiveCacheSupportCrudService<TenantEntity, String> {

    private final DefaultDimensionUserService dimensionUserService;

    private final ReactiveRepository<UserEntity, String> userRepository;

    /**
     * 绑定用户到租户. 一个用户只属于一个租户, 因此始终全量替换旧绑定.
     */
    @Transactional
    public Mono<Void> bindUser(String tenantId, Collection<String> userIdList) {
        if (CollectionUtils.isEmpty(userIdList)) {
            return Mono.empty();
        }
        return this
            .findById(tenantId)
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_not_exist", 404, tenantId)))
            .filter(tenant -> tenant.getState() != TenantState.disabled)
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_disabled", 400, tenantId)))
            .flatMap(tenant -> DimensionUserBindUtils
                .bindUser(dimensionUserService,
                          userIdList,
                          TenantDimensionType.tenant.getId(),
                          java.util.Collections.singleton(tenantId),
                          true))
            // 归属以维度绑定为准, 同步回写 s_user.tenant_id 供行级隔离使用。
            // 不回写的话, 平台代建再绑定的用户在租户端用户列表里不可见。
            .then(syncUserTenant(userIdList, tenantId));
    }

    private Mono<Void> syncUserTenant(Collection<String> userIdList, String tenantId) {
        return userRepository
            .createUpdate()
            .set(TenantConstants.TENANT_ID_PROPERTY, tenantId)
            .where()
            .in(UserEntity::getId, userIdList)
            .execute()
            .then();
    }

    @Transactional
    public Mono<Integer> unbindUser(String tenantId, Collection<String> userIdList) {
        if (CollectionUtils.isEmpty(userIdList)) {
            return Mono.just(0);
        }
        return DimensionUserBindUtils
            .unbindUser(dimensionUserService,
                        userIdList,
                        TenantDimensionType.tenant.getId(),
                        java.util.Collections.singleton(tenantId))
            .flatMap(count -> userRepository
                .createUpdate()
                .setNull(TenantConstants.TENANT_ID_PROPERTY)
                .where()
                .in(UserEntity::getId, userIdList)
                // 防误清: 仅清除当前归属于该租户的用户
                .and(TenantConstants.TENANT_ID_PROPERTY, "eq", tenantId)
                .execute()
                .thenReturn(count));
    }
}
