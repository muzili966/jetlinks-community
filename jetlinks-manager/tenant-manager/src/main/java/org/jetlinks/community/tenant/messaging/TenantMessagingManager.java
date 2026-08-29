package org.jetlinks.community.tenant.messaging;

import lombok.AllArgsConstructor;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.exception.AccessDenyException;
import org.jetlinks.community.gateway.external.DefaultMessagingManager;
import org.jetlinks.community.gateway.external.Message;
import org.jetlinks.community.gateway.external.MessagingManager;
import org.jetlinks.community.gateway.external.SubscribeRequest;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.context.TenantContext;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

/**
 * 订阅鉴权装饰器: 堵住 WebSocket 订阅不校验 topic 归属的越权口.
 * 平台管理员直通; 租户用户经 {@link TenantTopicChecker} 校验后放行.
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
public class TenantMessagingManager implements MessagingManager {

    private final DefaultMessagingManager delegate;
    private final TenantTopicChecker checker;
    private final TenantProperties properties;

    @Override
    public Flux<Message> subscribe(SubscribeRequest request) {
        if (!properties.isEnabled()) {
            return delegate.subscribe(request);
        }
        Authentication auth = request.getAuthentication();
        if (auth == null) {
            return Flux.error(new AccessDenyException());
        }
        TenantContext.Resolution resolution =
            TenantContext.resolve(auth, Context.empty(), properties.getPlatformAdminRoleId());
        if (resolution.isPlatformBypass()) {
            return delegate.subscribe(request);
        }
        // fail-closed: 未绑定租户的用户不允许订阅任何topic
        return resolution
            .getTenantId()
            .map(tenantId -> checker
                .check(request.getTopic(), tenantId)
                .thenMany(delegate.subscribe(request)))
            .orElseGet(() -> Flux.error(new AccessDenyException()));
    }
}
