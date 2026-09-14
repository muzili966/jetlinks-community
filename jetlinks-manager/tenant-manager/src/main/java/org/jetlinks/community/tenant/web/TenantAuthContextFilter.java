package org.jetlinks.community.tenant.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.ReactiveAuthenticationHolder;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.jetlinks.community.tenant.context.TenantImpersonation;
import org.jetlinks.community.tenant.context.TenantImpersonationAuthenticator;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * 把当前请求的认证放进 Reactor Context，供隔离监听器读取。
 * <p>
 * 为什么必须有这一层：隔离监听器原本自己调 {@code Authentication.currentReactive()} 取认证，
 * 而 hsweb 在认证缓存为空时会就地<strong>装配</strong>认证——装配过程要查
 * {@code s_role}/{@code s_organization} 这些维度表，这些查询同样会进监听器。
 * 于是装配期的角色查询被注入了 {@code tenant_id = 当前租户}，而
 * {@code tenant-user} 这类<strong>全租户共享的模板角色</strong> tenant_id 为 NULL，
 * 直接被过滤掉 → role 维度丢失 → 租户用户菜单全空、所有接口 403。
 * <p>
 * 真机表现极具迷惑性：认证缓存命中时一切正常，只有在改菜单/改授权
 * 触发缓存失效后才复现，看起来像是「改授权把权限改没了」。
 * <p>
 * 有了这一层，监听器只读上下文、不再主动触发装配：
 * <ul>
 *     <li>HTTP 业务请求——本过滤器已放入认证，正常注入租户条件</li>
 *     <li>认证装配链路——尚未经过本过滤器，读不到认证，不注入（正是所需）</li>
 *     <li>设备上行/定时任务等系统链路——无认证，不注入（与改造前一致）</li>
 * </ul>
 * <p>
 * 平台管理员代理租户时，放入的是降权后的租户管理员身份（见 {@link TenantImpersonationAuthenticator}）。
 * hsweb 的 {@code ReactiveAuthenticationHolder.get()} 优先返回上下文里的认证，
 * 所以接口鉴权、菜单、{@code /authorize/me} 与隔离监听器都按这份身份执行。
 * 代理审计也记在这里：只有这一层同时知道操作人与目标租户。
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
@RequiredArgsConstructor
public class TenantAuthContextFilter implements WebFilter, Ordered {

    private static final String TOKEN_HEADER = "X-Access-Token";
    private static final String TOKEN_QUERY = ":X_Access_Token";

    private final UserTokenManager userTokenManager;

    private final TenantImpersonationAuthenticator impersonationAuthenticator;

    @Override
    @Nonnull
    public Mono<Void> filter(@Nonnull ServerWebExchange exchange, @Nonnull WebFilterChain chain) {
        String token = exchange.getRequest().getHeaders().getFirst(TOKEN_HEADER);
        if (token == null) {
            token = exchange.getRequest().getQueryParams().getFirst(TOKEN_QUERY);
        }
        if (token == null) {
            return chain.filter(exchange);
        }
        return userTokenManager
            .getByToken(token)
            .filter(t -> t.isNormal() && t.getUserId() != null)
            // 这一步可能触发认证装配；装配自身的查询看不到下面写入的上下文，因此不会被注入
            .flatMap(t -> ReactiveAuthenticationHolder.get(t.getUserId()))
            // 代理身份同理要在写入上下文之前算好：它要查角色与菜单授权
            .flatMap(auth -> effectiveAuthentication(exchange, auth))
            // 先把「有没有认证」物化成值再分支。
            // 不能写成 .flatMap(auth -> chain.filter(..)).switchIfEmpty(chain.filter(..))：
            // chain.filter 返回 Mono<Void>，正常跑完也是「空」，会把 switchIfEmpty 一并触发，
            // 于是过滤链被执行两遍。小响应先写完看不出来，菜单树这种大的流式响应会卡住不终止
            // ——真机表现为前端 15s 超时白屏，而 curl 直连正常。
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty())
            .flatMap(auth -> auth
                .map(a -> chain
                    .filter(exchange)
                    .contextWrite(ctx -> ctx.put(Authentication.class, a)))
                // 认证解析不了(游客/登录接口/token 失效)时不阻断，交给后续认证过滤器处理
                .orElseGet(() -> chain.filter(exchange)));
    }

    /**
     * 平台管理员代理租户时换成降权身份，其他情况原样返回
     */
    private Mono<Authentication> effectiveAuthentication(ServerWebExchange exchange, Authentication auth) {
        return TenantImpersonation
            .fromRequest(exchange.getRequest())
            .map(tenantId -> impersonationAuthenticator
                .resolve(auth, tenantId)
                .doOnNext(effective -> auditImpersonation(exchange, effective, tenantId)))
            .orElseGet(() -> Mono.just(auth));
    }

    private void auditImpersonation(ServerWebExchange exchange, Authentication effective, String tenantId) {
        if (!TenantImpersonation.isImpersonated(effective)) {
            return;
        }
        log.info("tenant impersonation: operator=[{}] tenant=[{}] method=[{}] path=[{}]",
                 effective.getUser().getId(),
                 tenantId,
                 exchange.getRequest().getMethod(),
                 exchange.getRequest().getPath().value());
    }

    @Override
    public int getOrder() {
        // 必须早于所有会触发实体查询的处理；租户守卫(+200)也依赖它写入的上下文
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
