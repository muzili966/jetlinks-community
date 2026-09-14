package org.jetlinks.community.tenant.web;

import lombok.AllArgsConstructor;
import org.jetlinks.community.tenant.context.TenantImpersonation;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;

/**
 * 平台管理员租户代理入口(D5): 捕获代理租户(请求头 X-Tenant-Id 或查询参数 :X_Tenant_Id)写入 Reactor Context.
 * <p>
 * 代理仅对平台管理员生效(校验在 {@code TenantContext.resolve}),
 * 普通用户携带不产生任何效果, 因此这里无需鉴权.
 * 审计在 {@link TenantAuthContextFilter} 记录: 只有那一层同时知道操作人与目标租户.
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
public class TenantImpersonationFilter implements WebFilter, Ordered {

    @Override
    @Nonnull
    public Mono<Void> filter(@Nonnull ServerWebExchange exchange, WebFilterChain chain) {
        return TenantImpersonation
            .fromRequest(exchange.getRequest())
            .map(tenantId -> chain
                .filter(exchange)
                .contextWrite(ctx -> TenantImpersonation.write(ctx, tenantId)))
            .orElseGet(() -> chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
