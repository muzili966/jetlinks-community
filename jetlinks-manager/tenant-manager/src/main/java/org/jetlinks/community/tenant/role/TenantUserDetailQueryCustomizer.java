package org.jetlinks.community.tenant.role;

import lombok.AllArgsConstructor;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.authorization.Authentication;
import org.jetlinks.community.auth.service.UserDetailQueryCustomizer;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.context.TenantContext;
import reactor.core.publisher.Mono;

/**
 * 用户详情查询的租户约束。
 * <p>
 * /user/detail/_query 走 QueryHelper 原生联表, 实体事件挂不上——
 * 真机现象: /user/_query 已隔离但 detail 列表仍能看到 admin。
 * 查询条件作用在 s_user 表(已有 tenant_id 列), 平台管理员不受限。
 */
@AllArgsConstructor
public class TenantUserDetailQueryCustomizer implements UserDetailQueryCustomizer {

    private final TenantProperties properties;

    @Override
    public Mono<QueryParamEntity> customize(QueryParamEntity param) {
        if (!properties.isEnabled()) {
            return Mono.just(param);
        }
        return Mono.deferContextual(ctxView -> Authentication
            .currentReactive()
            .map(auth -> {
                TenantContext.Resolution resolution =
                    TenantContext.resolve(auth, ctxView, properties.getPlatformAdminRoleId());
                if (resolution.isPlatformBypass()) {
                    return param;
                }
                String tenantId = resolution.getTenantId().orElse(TenantConstants.NO_TENANT);
                param.and(TenantConstants.TENANT_ID_PROPERTY, "eq", tenantId);
                return param;
            })
            .defaultIfEmpty(param));
    }
}
