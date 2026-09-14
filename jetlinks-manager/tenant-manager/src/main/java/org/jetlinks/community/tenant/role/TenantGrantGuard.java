package org.jetlinks.community.tenant.role;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.ReactiveAuthenticationHolder;
import org.hswebframework.web.authorization.exception.AccessDenyException;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.jetlinks.community.auth.entity.RoleEntity;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * 租户可操作的角色菜单授权路径: /menu/role/{roleId}/_grant(/tree|/list)。
     * 放行前校验 roleId 归属——查询走行级隔离, 他租户角色对当前用户不可见,
     * findById 为空即拒绝; 授权内容里的平台专属菜单由持久层监听器剔除。
     */
    private static final Pattern ROLE_GRANT_PATH =
        Pattern.compile("^/menu/role/([^/]+)/_grant(/.*)?$");

    private final TenantProperties properties;

    private final ReactiveRepository<RoleEntity, String> roleRepository;

    private final UserTokenManager userTokenManager;

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
        // 先把「有没有认证」物化成值再分支。
        // 不能写成 .flatMap(auth -> chain.filter(..)).switchIfEmpty(chain.filter(..))：
        // chain.filter 返回 Mono<Void>，放行成功后同样是「空」，会把 switchIfEmpty 一并触发，
        // 于是整条过滤链跑两遍。Content-Length 的响应看不出来，chunked 流式响应则永不终止
        // ——真机表现为 /menu/_all/tree 经代理挂死，而 curl 直连正常。
        return resolveAuth(exchange)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty())
            .flatMap(maybeAuth -> {
                // 无认证信息时交给后续的认证过滤器处理，不在此处放行业务
                if (maybeAuth.isEmpty()) {
                    return chain.filter(exchange);
                }
                Authentication auth = maybeAuth.get();
                if (TenantContext.isPlatformAdmin(auth, properties.getPlatformAdminRoleId())) {
                    return chain.filter(exchange);
                }
                Matcher grant = ROLE_GRANT_PATH.matcher(path);
                if (grant.matches()) {
                    return checkRoleOwnership(grant.group(1), auth, exchange, chain, path);
                }
                log.warn("tenant user [{}] denied on platform-only path [{}] [{}]",
                         auth.getUser().getId(), exchange.getRequest().getMethod(), path);
                return Mono.error(new AccessDenyException());
            });
    }

    /**
     * 取本次请求的有效身份。
     * <p>
     * 优先读 {@link org.jetlinks.community.tenant.web.TenantAuthContextFilter}(+100) 写入上下文的认证:
     * 代理态下那是降权后的租户管理员, 平台管理员在租户视图里因此同样受租户规则约束
     * (平台专属接口拒绝, 只能给代理租户的角色配菜单)。取不到时再自行从 token 解析。
     */
    private Mono<Authentication> resolveAuth(ServerWebExchange exchange) {
        return Mono
            .deferContextual(ctx -> Mono.justOrEmpty(ctx.<Authentication>getOrEmpty(Authentication.class)))
            .switchIfEmpty(Mono.defer(() -> resolveAuthByToken(exchange)));
    }

    /**
     * 自行从 token 解析认证。
     * <p>
     * 不能用 {@code Authentication.currentReactive()}: hsweb 的 token 上下文由
     * UserTokenWebFilter 写入, 它无显式 order(排在最内层), 本过滤器在它外层,
     * currentReactive 永远为空——真机现象: 守卫全程空转, 平台接口全靠资源权限兜底,
     * 租户拿到 menu 权限后即可读写任意角色的菜单授权。
     */
    private Mono<Authentication> resolveAuthByToken(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst("X-Access-Token");
        if (token == null) {
            token = exchange.getRequest().getQueryParams().getFirst(":X_Access_Token");
        }
        if (token == null) {
            return Mono.empty();
        }
        return userTokenManager
            .getByToken(token)
            .filter(t -> t.isNormal() && t.getUserId() != null)
            .flatMap(t -> ReactiveAuthenticationHolder.get(t.getUserId()));
    }

    /**
     * 租户管理员只能给本租户的角色配菜单。
     * findById 在当前认证上下文里执行, 隔离监听器会自动追加租户条件,
     * 因此共享模板角色(tenant-user, 无租户归属)与他租户角色都查不到 => 拒绝。
     */
    private Mono<Void> checkRoleOwnership(String roleId,
                                          Authentication auth,
                                          ServerWebExchange exchange,
                                          WebFilterChain chain,
                                          String path) {
        String myTenant = TenantContext.currentTenant(auth).orElse(null);
        if (myTenant == null) {
            return Mono.error(new AccessDenyException());
        }
        // 同上：先把「角色是否归本租户」物化成布尔再分支。
        // 若写成 .flatMap(role -> chain.filter(..)).switchIfEmpty(error(..))，
        // 放行成功后 chain.filter 的空完成会触发 switchIfEmpty，
        // 变成「校验通过却仍抛拒绝异常」——租户管理员根本没法给自己的角色配菜单。
        return roleRepository
            .findById(roleId)
            // 过滤器阶段无认证上下文, 行级隔离不生效, 必须显式比对归属
            .filter(role -> role instanceof org.jetlinks.community.tenant.TenantAware
                && myTenant.equals(((org.jetlinks.community.tenant.TenantAware) role).getTenantId()))
            .hasElement()
            .flatMap(owned -> {
                if (owned) {
                    return chain.filter(exchange);
                }
                log.warn("tenant user [{}] denied grant on foreign role [{}] path [{}]",
                         auth.getUser().getId(), roleId, path);
                return Mono.error(new AccessDenyException());
            });
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
