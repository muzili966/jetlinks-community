package org.jetlinks.community.tenant.messaging;

import lombok.RequiredArgsConstructor;
import org.jetlinks.community.tenant.context.TenantImpersonation;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.function.Function;

/**
 * WebSocket 订阅的代理态身份。
 * <p>
 * 浏览器 WebSocket 不能自定义请求头，代理租户只能放在握手地址的查询参数里；
 * 而 {@code WebSocketMessagingHandler} 按路径 token 自己取认证，每个订阅又是独立 subscribe，
 * HTTP 过滤器写进 Reactor Context 的身份传不进去。
 * 所以对带代理参数的连接，换用「会返回降权身份的认证管理器」构造处理器，
 * 订阅请求里拿到的就是租户管理员身份，再由 {@link TenantMessagingManager} 按租户校验 topic。
 *
 * @author tenant-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class TenantImpersonationWebSocketHandler implements WebSocketHandler {

    private final WebSocketHandler delegate;

    /**
     * 按代理租户构造处理器
     */
    private final Function<String, WebSocketHandler> impersonatedHandlerFactory;

    @Override
    @Nonnull
    public Mono<Void> handle(@Nonnull WebSocketSession session) {
        return TenantImpersonation
            .fromUri(session.getHandshakeInfo().getUri())
            .map(tenantId -> impersonatedHandlerFactory.apply(tenantId).handle(session))
            .orElseGet(() -> delegate.handle(session));
    }
}
