package org.jetlinks.community.tenant.context;

import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.hswebframework.web.authorization.simple.SimpleUser;
import org.jetlinks.community.tenant.TenantConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class TenantImpersonationTest {

    @Test
    void headerTakesPrecedenceOverQueryParam() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/device-instance/_query")
            .queryParam(TenantConstants.IMPERSONATE_QUERY_PARAM, "t-query")
            .header(TenantConstants.IMPERSONATE_HEADER, "t-header")
            .build();

        assertEquals("t-header", TenantImpersonation.fromRequest(request).orElse(null));
    }

    @Test
    void queryParamUsedWhenHeaderAbsent() {
        // 导出下载链接、EventSource 只能走查询参数
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/device-instance/export.xlsx")
            .queryParam(TenantConstants.IMPERSONATE_QUERY_PARAM, "7")
            .build();

        assertEquals("7", TenantImpersonation.fromRequest(request).orElse(null));
    }

    @Test
    void blankValuesAreIgnored() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/device-instance/_query")
            .queryParam(TenantConstants.IMPERSONATE_QUERY_PARAM, "")
            .header(TenantConstants.IMPERSONATE_HEADER, "  ")
            .build();

        assertTrue(TenantImpersonation.fromRequest(request).isEmpty());
    }

    @Test
    void readsDecodedTenantFromWebSocketHandshakeUri() {
        URI uri = URI.create("ws://localhost/api/messaging/tk?:X_Access_Token=tk&:X_Tenant_Id=%E7%A7%9F%E6%88%B7A");

        assertEquals("租户A", TenantImpersonation.fromUri(uri).orElse(null));
    }

    @Test
    void webSocketUriWithoutTenantIsEmpty() {
        URI uri = URI.create("ws://localhost/api/messaging/tk?:X_Access_Token=tk");

        assertTrue(TenantImpersonation.fromUri(uri).isEmpty());
    }

    @Test
    void impersonatedMarkerOnlyOnDerivedAuthentication() {
        SimpleAuthentication plain = new SimpleAuthentication();
        plain.setUser(SimpleUser.builder().id("admin").name("admin").build());
        SimpleAuthentication derived = new SimpleAuthentication();
        derived.setUser(SimpleUser.builder().id("admin").name("admin").build());
        derived.setAttribute(TenantConstants.IMPERSONATOR_ATTRIBUTE, "admin");

        assertFalse(TenantImpersonation.isImpersonated(plain));
        assertTrue(TenantImpersonation.isImpersonated(derived));
        assertFalse(TenantImpersonation.isImpersonated(null));
    }
}
