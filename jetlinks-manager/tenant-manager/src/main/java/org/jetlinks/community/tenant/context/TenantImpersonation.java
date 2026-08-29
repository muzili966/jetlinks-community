package org.jetlinks.community.tenant.context;

import org.jetlinks.community.tenant.TenantConstants;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Optional;

/**
 * 平台管理员租户代理(D5): 通过请求头显式声明"以某租户身份操作",
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
}
