package org.jetlinks.community.tenant.messaging;

import org.hswebframework.web.authorization.exception.AccessDenyException;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.cache.ProductTenantCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class TenantTopicCheckerTest {

    private static final String TENANT_A = "t001";
    private static final String OWNED_PRODUCT = "p1";
    private static final String OTHERS_PRODUCT = "p9";

    private TenantTopicChecker checker;

    @BeforeEach
    void setup() {
        ProductTenantCache cache = Mockito.mock(ProductTenantCache.class);
        Mockito.when(cache.belongsTo(anyString(), anyString())).thenReturn(false);
        Mockito.when(cache.belongsTo(eq(OWNED_PRODUCT), eq(TENANT_A))).thenReturn(true);
        checker = new TenantTopicChecker(cache, new TenantProperties());
    }

    @Test
    void ownedProductTopicAllowed() {
        StepVerifier
            .create(checker.check("/device/" + OWNED_PRODUCT + "/dev1/message/property/report", TENANT_A))
            .verifyComplete();
    }

    @Test
    void othersProductTopicDenied() {
        StepVerifier
            .create(checker.check("/device/" + OTHERS_PRODUCT + "/dev1/message/property/report", TENANT_A))
            .verifyError(AccessDenyException.class);
    }

    @Test
    void wildcardProductDenied() {
        // 通配符订阅会跨越租户边界, 必须拒绝
        StepVerifier
            .create(checker.check("/device/*/*/message/property/report", TENANT_A))
            .verifyError(AccessDenyException.class);
    }

    @Test
    void dashboardDeviceTopicChecked() {
        StepVerifier
            .create(checker.check("/dashboard/device/" + OTHERS_PRODUCT + "/dev1/properties/realTime", TENANT_A))
            .verifyError(AccessDenyException.class);
    }

    @Test
    void nonWhitelistedTopicDenied() {
        // fail-closed: 未知topic一律拒绝
        StepVerifier
            .create(checker.check("/internal/cluster/state", TENANT_A))
            .verifyError(AccessDenyException.class);
    }

    @Test
    void whitelistedNonDeviceTopicAllowed() {
        StepVerifier
            .create(checker.check("/notifications", TENANT_A))
            .verifyComplete();
    }

    @Test
    void systemMonitorTopicDenied() {
        // 首页「综合视图」的 BasicCountCard 会订阅它拿宿主机 CPU/JVM 指标。
        // 拒绝是对的，但前端必须同步隐藏该卡片，否则租户一进首页就弹「权限不足」
        // ——真机踩过。前端保护见 ComprehensiveHome/DevOpsHome 的 isTenantUser 判断。
        StepVerifier
            .create(checker.check("/dashboard/systemMonitor/stats/info/realTime", TENANT_A))
            .verifyError(AccessDenyException.class);
    }

    @Test
    void deviceDashboardTopicStillAllowed() {
        // 设备维度仪表盘按 productId 校验归属，不能被上面那条规则误伤
        StepVerifier
            .create(checker.check("/dashboard/device/" + OWNED_PRODUCT + "/property/realTime", TENANT_A))
            .verifyComplete();
    }
}
