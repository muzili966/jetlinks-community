package org.jetlinks.community.tenant.role;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.crud.events.EntityCreatedEvent;
import org.jetlinks.community.auth.entity.RoleEntity;
import org.jetlinks.community.tenant.TenantAware;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * 租户开通时自动创建「租户管理员」角色。
 * <p>
 * 不做这一步，租户建好后没有任何人能管理它内部的用户与角色。
 * 该角色归属租户（tenant_id），因此天然只对本租户可见。
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
@RequiredArgsConstructor
public class TenantRoleInitializer {

    private final TenantProperties properties;
    private final ReactiveRepository<RoleEntity, String> roleRepository;

    @EventListener
    public void handleTenantCreated(EntityCreatedEvent<TenantEntity> event) {
        if (!properties.isEnabled()) {
            return;
        }
        event.async(createAdminRoles(event.getEntity()));
    }

    private Mono<Void> createAdminRoles(Collection<TenantEntity> tenants) {
        return Flux
            .fromIterable(tenants)
            .map(this::buildAdminRole)
            .as(roleRepository::save)
            .doOnNext(r -> log.info("created tenant admin roles: {}", r.getTotal()))
            .then();
    }

    private RoleEntity buildAdminRole(TenantEntity tenant) {
        RoleEntity role = new RoleEntity();
        role.setId(TenantConstants.tenantAdminRoleId(tenant.getId()));
        role.setName("租户管理员");
        role.setDescription("租户[" + tenant.getName() + "]的管理员，可管理本租户用户与角色");
        // 角色实体已被替换为租户子类，直接写入归属，避免依赖登录态
        if (role instanceof TenantAware) {
            ((TenantAware) role).setTenantId(tenant.getId());
        }
        return role;
    }
}
