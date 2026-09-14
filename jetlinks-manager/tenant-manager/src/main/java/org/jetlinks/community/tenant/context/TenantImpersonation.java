package org.jetlinks.community.tenant.context;

import org.hswebframework.web.authorization.Authentication;
import org.jetlinks.community.tenant.TenantConstants;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 平台管理员租户代理(D5): 通过请求头(或查询参数)显式声明"以某租户身份操作",
 * 写入 Reactor Context 供数据隔离层读取, 并全程审计.
 *
 * @author tenant-manager
 * @since 2.11
 */
public final class TenantImpersonation {

    private TenantImpersonation() {
    }

    public static Context write(Context context, String tenantId) {
        return context.put(TenantConstants.IMPERSONATE_CONTEXT_KEY, tenantId);
    }

    public static Optional<String> from(ContextView contextView) {
        return contextView.getOrEmpty(TenantConstants.IMPERSONATE_CONTEXT_KEY);
    }

    /**
     * 从请求读取代理租户：请求头优先，其次查询参数
     */
    public static Optional<String> fromRequest(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(TenantConstants.IMPERSONATE_HEADER);
        if (StringUtils.hasText(header)) {
            return Optional.of(header);
        }
        return Optional
            .ofNullable(request.getQueryParams().getFirst(TenantConstants.IMPERSONATE_QUERY_PARAM))
            .filter(StringUtils::hasText);
    }

    /**
     * 从 WebSocket 握手地址读取代理租户（浏览器 WebSocket 不能带请求头）
     */
    public static Optional<String> fromUri(URI uri) {
        String raw = UriComponentsBuilder
            .fromUri(uri)
            .build()
            .getQueryParams()
            .getFirst(TenantConstants.IMPERSONATE_QUERY_PARAM);
        return Optional
            .ofNullable(raw)
            .map(value -> UriUtils.decode(value, StandardCharsets.UTF_8))
            .filter(StringUtils::hasText);
    }

    /**
     * 是否为平台管理员代理租户时派生出的降权身份
     */
    public static boolean isImpersonated(Authentication auth) {
        return auth != null && auth.getAttribute(TenantConstants.IMPERSONATOR_ATTRIBUTE).isPresent();
    }
}
