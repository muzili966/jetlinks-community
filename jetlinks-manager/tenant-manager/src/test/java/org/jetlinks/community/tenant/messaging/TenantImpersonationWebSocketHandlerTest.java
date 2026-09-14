package org.jetlinks.community.tenant.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantImpersonationWebSocketHandlerTest {

    private final AtomicBoolean delegateUsed = new AtomicBoolean();

    private final AtomicReference<String> impersonatedTenant = new AtomicReference<>();

    private final TenantImpersonationWebSocketHandler handler = new TenantImpersonationWebSocketHandler(
        session -> Mono.fromRunnable(() -> delegateUsed.set(true)),
        tenantId -> {
            impersonatedTenant.set(tenantId);
            return session -> Mono.empty();
        });

    @Test
    void connectionWithTenantParamUsesImpersonatedHandler() {
        StepVerifier
            .create(handler.handle(session("ws://localhost/messaging/tk?:X_Access_Token=tk&:X_Tenant_Id=7")))
            .verifyComplete();

        assertEquals("7", impersonatedTenant.get());
        assertFalse(delegateUsed.get());
    }

    @Test
    void connectionWithoutTenantParamUsesOriginalHandler() {
        StepVerifier
            .create(handler.handle(session("ws://localhost/messaging/tk?:X_Access_Token=tk")))
            .verifyComplete();

        assertNull(impersonatedTenant.get());
        assertTrue(delegateUsed.get());
    }

    private WebSocketSession session(String uri) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getHandshakeInfo())
            .thenReturn(new HandshakeInfo(URI.create(uri), new HttpHeaders(), Mono.empty(), null));
        return session;
    }

    @SuppressWarnings("unused")
    private static WebSocketHandler noop() {
        return session -> Mono.empty();
    }
}
