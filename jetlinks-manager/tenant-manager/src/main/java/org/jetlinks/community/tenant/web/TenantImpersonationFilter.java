package org.jetlinks.community.tenant.web;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.context.TenantImpersonation;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;

/**
 * 平台管理员租户代理入口(D5): 捕获 X-Tenant-Id 请求头写入 Reactor Context 并审计.
 * <p>
 * 头信息仅对平台管理员生效(校验在 {@code TenantContext.resolve}),
 * 普通用户携带此头不产生任何效果, 因此这里无需鉴权.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
@AllArgsConstructor
public class TenantImpersonationFilter implements WebFilter, Ordered {

    @Override
    @Nonnull
    public Mono<Void> filter(@Nonnull ServerWebExchange exchange, WebFilterChain chain) {
        String tenantId = exchange
            .getRequest()
            .getHeaders()
            .getFirst(TenantConstants.IMPERSONATE_HEADER);
        if (!StringUtils.hasText(tenantId)) {
            return chain.filter(exchange);
        }
        // 审计: 记录所有代理访问(包括无效尝试)
        log.info("tenant impersonation: tenant=[{}] path=[{}] method=[{}]",
                 tenantId,
                 exchange.getRequest().getPath().value(),
                 exchange.getRequest().getMethod());
        return chain
            .filter(exchange)
            .contextWrite(ctx -> TenantImpersonation.write(ctx, tenantId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
