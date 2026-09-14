package org.jetlinks.community.tenant.messaging;

import lombok.RequiredArgsConstructor;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.AuthenticationRequest;
import org.hswebframework.web.authorization.ReactiveAuthenticationManager;
import org.jetlinks.community.tenant.context.TenantImpersonationAuthenticator;
import reactor.core.publisher.Mono;

/**
 * 单条 WebSocket 连接的代理态认证：按 token 取到真实身份后，换成代理租户下的有效身份。
 * 非平台管理员由 {@link TenantImpersonationAuthenticator} 原样返回，带代理参数也不产生效果。
 *
 * @author tenant-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class ImpersonatingAuthenticationManager implements ReactiveAuthenticationManager {

    private final ReactiveAuthenticationManager delegate;

    private final TenantImpersonationAuthenticator authenticator;

    private final String tenantId;

    @Override
    public Mono<Authentication> authenticate(Mono<AuthenticationRequest> request) {
        return delegate.authenticate(request);
    }

    @Override
    public Mono<Authentication> getByUserId(String userId) {
        return delegate
            .getByUserId(userId)
            .flatMap(auth -> authenticator.resolve(auth, tenantId));
    }
}
