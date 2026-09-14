package org.jetlinks.community.tenant.context;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.DefaultDimensionType;
import org.hswebframework.web.authorization.Dimension;
import org.hswebframework.web.authorization.Permission;
import org.hswebframework.web.authorization.events.AuthorizationInitializeEvent;
import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.hswebframework.web.authorization.simple.SimpleDimension;
import org.jetlinks.community.auth.entity.RoleEntity;
import org.jetlinks.community.auth.initialize.MenuAuthenticationInitializeService;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantDimensionType;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.entity.TenantEntity;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 平台管理员代理租户时的有效身份：降权为目标租户的管理员。
 * <p>
 * 用户仍是管理员本人（审计能追到人），维度换成「租户 + 租户管理员角色」，
 * 权限按该角色的菜单授权现算——与真实租户管理员登录时同一来源
 * （{@link MenuAuthenticationInitializeService}），菜单、按钮、接口权限、数据范围因此一次对齐，
 * 原先忽略代理的 {@code TenantContext.currentTenant(auth)} 调用点也随之按目标租户处理。
 * <p>
 * 两个刻意的取舍：
 * <ul>
 *     <li>不经 ApplicationEventPublisher 发布初始化事件——{@code AuthorizationPermissionInitializeService}
 *         会按用户名 admin 把 {@code *:*} 加回来，降权就白做了；</li>
 *     <li>不进 hsweb 的 {@code user-auth} 缓存——那份缓存以 userId 为 key 且放在 Redis，
 *         写进去会把管理员所有会话都变成租户身份。这里按租户单独短时缓存授权，
 *         管理员不在该角色下、收不到清缓存事件，角色授权变更最多延迟 {@link #GRANT_CACHE_TTL} 生效。</li>
 * </ul>
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
@RequiredArgsConstructor
public class TenantImpersonationAuthenticator {

    static final Duration GRANT_CACHE_TTL = Duration.ofSeconds(10);

    private final TenantProperties properties;

    private final ReactiveRepository<TenantEntity, String> tenantRepository;

    private final ReactiveRepository<RoleEntity, String> roleRepository;

    private final MenuAuthenticationInitializeService menuPermissionService;

    private final Map<String, Mono<TenantGrant>> grantCache = new ConcurrentHashMap<>();

    /**
     * 计算代理租户下的有效身份：只有平台管理员的代理生效，其他用户原样返回（带代理参数也无效果）
     */
    public Mono<Authentication> resolve(Authentication real, String tenantId) {
        if (!TenantContext.isPlatformAdmin(real, properties.getPlatformAdminRoleId())) {
            log.warn("user [{}] is not platform admin, impersonation of tenant [{}] ignored",
                     real.getUser().getId(), tenantId);
            return Mono.just(real);
        }
        return grantCache
            .computeIfAbsent(tenantId, this::cachedGrant)
            .map(grant -> derive(real, grant));
    }

    private Mono<TenantGrant> cachedGrant(String tenantId) {
        // 失败与空结果不缓存，下一次请求重新装配
        return loadGrant(tenantId)
            .cache(grant -> GRANT_CACHE_TTL, error -> Duration.ZERO, () -> Duration.ZERO);
    }

    private Mono<TenantGrant> loadGrant(String tenantId) {
        return Mono
            .zip(tenantDimension(tenantId), adminRoleDimensions(tenantId))
            .flatMap(tp2 -> {
                List<Dimension> dimensions = new ArrayList<>(tp2.getT2());
                dimensions.add(tp2.getT1());
                return loadPermissions(dimensions)
                    .map(permissions -> new TenantGrant(dimensions, permissions));
            })
            // 装配查询不能带任何身份，否则会被隔离监听器按当前身份注入租户条件（见 TenantAuthContextFilter 注释）
            .contextWrite(ctx -> ctx.delete(Authentication.class));
    }

    private Mono<Dimension> tenantDimension(String tenantId) {
        return tenantRepository
            .findById(tenantId)
            .map(TenantEntity::toDimension)
            // 租户不存在时仍按该租户号收口：数据查询为空，而不是退回平台视角
            .defaultIfEmpty(SimpleDimension.of(tenantId, tenantId, TenantDimensionType.tenant, Collections.emptyMap()));
    }

    private Mono<List<Dimension>> adminRoleDimensions(String tenantId) {
        String roleId = TenantConstants.tenantAdminRoleId(tenantId);
        return roleRepository
            .findById(roleId)
            .<List<Dimension>>map(role -> Collections.singletonList(
                SimpleDimension.of(role.getId(), role.getName(), DefaultDimensionType.role, Collections.emptyMap())))
            .switchIfEmpty(Mono.fromSupplier(() -> {
                log.warn("tenant [{}] has no admin role [{}], impersonation gets no menus or permissions",
                         tenantId, roleId);
                return Collections.emptyList();
            }));
    }

    /**
     * 直接调用菜单权限装配，不经事件发布器（原因见类注释）
     */
    private Mono<List<Permission>> loadPermissions(List<Dimension> dimensions) {
        SimpleAuthentication probe = new SimpleAuthentication();
        probe.setDimensions(dimensions);
        AuthorizationInitializeEvent event = new AuthorizationInitializeEvent(probe);
        menuPermissionService.refactorPermission(event);
        return event
            .getAsync()
            .then(Mono.fromSupplier(() -> event.getAuthentication().getPermissions()));
    }

    private Authentication derive(Authentication real, TenantGrant grant) {
        SimpleAuthentication derived = new SimpleAuthentication();
        derived.setUser(real.getUser());
        derived.addDimensions(grant.dimensions());
        // 权限对象可变（merge 会改 actions），每个请求复制一份，不共享缓存里的实例
        derived.setPermissions(grant.permissions().stream().map(Permission::copy).collect(Collectors.toList()));
        derived.setAttribute(TenantConstants.IMPERSONATOR_ATTRIBUTE, real.getUser().getId());
        return derived;
    }

    private record TenantGrant(List<Dimension> dimensions, List<Permission> permissions) {
    }
}
