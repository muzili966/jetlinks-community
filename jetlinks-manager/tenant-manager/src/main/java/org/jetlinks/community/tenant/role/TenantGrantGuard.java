package org.jetlinks.community.tenant.role;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.exception.AccessDenyException;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.context.TenantContext;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

/**
 * 防提权：租户用户不得触碰平台级的授权与菜单接口。
 * <p>
 * 没有这道防线，租户管理员可以给自己的角色授予 {@code tenant:query} 等平台权限，
 * 从而看到所有租户——租户内分权反而成了提权入口。
 * <p>
 * 平台管理员不受限制。
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
@RequiredArgsConstructor
public class TenantGrantGuard implements WebFilter, Ordered {

    /**
     * 仅平台可调用的路径前缀（授权设置、菜单管理、权限定义、租户与计费）
     */
    private static final List<String> PLATFORM_ONLY_PREFIXES = Arrays.asList(
        "/autz-setting",
        "/menu",
        "/permission",
        "/tenant"
    );

    /**
     * 上述前缀下仍允许租户读取的路径（读自己的菜单、查自己的租户信息）
     */
    private static final List<String> TENANT_READABLE = Arrays.asList(
        "/menu/user-own",
        "/tenant/_current",
        "/tenant/plan/_enabled"
    );

    private final TenantProperties properties;

    @Override
    @Nonnull
    public Mono<Void> filter(@Nonnull ServerWebExchange exchange, @Nonnull WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        if (!isPlatformOnly(path)) {
            return chain.filter(exchange);
        }
        return Authentication
            .currentReactive()
            .flatMap(auth -> {
                if (TenantContext.isPlatformAdmin(auth, properties.getPlatformAdminRoleId())) {
                    return chain.filter(exchange);
                }
                log.warn("tenant user [{}] denied on platform-only path [{}] [{}]",
                         auth.getUser().getId(), exchange.getRequest().getMethod(), path);
                return Mono.error(new AccessDenyException());
            })
            // 无认证信息时交给后续的认证过滤器处理，不在此处放行业务
            .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
    }

    private boolean isPlatformOnly(String path) {
        if (TENANT_READABLE.stream().anyMatch(path::startsWith)) {
            return false;
        }
        return PLATFORM_ONLY_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        // 需在认证之后执行，故排在 TenantImpersonationFilter 之后
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
}
