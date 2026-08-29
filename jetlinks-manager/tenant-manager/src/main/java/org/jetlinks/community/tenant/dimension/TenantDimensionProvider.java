package org.jetlinks.community.tenant.dimension;

import org.hswebframework.web.authorization.Dimension;
import org.hswebframework.web.authorization.DimensionType;
import org.hswebframework.web.system.authorization.defaults.service.DefaultDimensionUserService;
import org.jetlinks.community.auth.dimension.BaseDimensionProvider;
import org.jetlinks.community.tenant.TenantDimensionType;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.service.TenantService;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

/**
 * 登录时把用户绑定的租户注入 Authentication 维度,
 * 与 {@link org.jetlinks.community.auth.dimension.OrganizationDimensionProvider} 同机制.
 *
 * @author tenant-manager
 * @since 2.11
 */
public class TenantDimensionProvider extends BaseDimensionProvider<TenantEntity> {

    public TenantDimensionProvider(TenantService tenantService,
                                   DefaultDimensionUserService dimensionUserService,
                                   ApplicationEventPublisher eventPublisher) {
        super(tenantService.getRepository(), eventPublisher, dimensionUserService);
    }

    @Override
    protected DimensionType getDimensionType() {
        return TenantDimensionType.tenant;
    }

    @Override
    protected Mono<Dimension> convertToDimension(TenantEntity entity) {
        return Mono.just(entity.toDimension());
    }
}
