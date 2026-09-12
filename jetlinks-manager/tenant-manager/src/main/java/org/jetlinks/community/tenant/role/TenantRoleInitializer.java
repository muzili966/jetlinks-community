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
        // 必须经 repository 的实体工厂创建: new RoleEntity() 拿到的是原始类而非
        // 租户子类, instanceof TenantAware 恒为 false, tenantId 永远落不上——
        // 真机现象: 管理员角色无租户归属, 租户端角色列表为空。
        RoleEntity role = roleRepository.newInstanceNow();
        role.setId(TenantConstants.tenantAdminRoleId(tenant.getId()));
        role.setName("租户管理员");
        role.setDescription("租户[" + tenant.getName() + "]的管理员，可管理本租户用户与角色");
        if (role instanceof TenantAware) {
            ((TenantAware) role).setTenantId(tenant.getId());
        } else {
            log.warn("role entity is not tenant-aware, tenant [{}] admin role has no tenant owner",
                     tenant.getId());
        }
        return role;
    }
}
