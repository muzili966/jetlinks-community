package org.jetlinks.community.tenant.messaging;

import org.hswebframework.web.authorization.ReactiveAuthenticationManager;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.jetlinks.community.gateway.external.MessagingManager;
import org.jetlinks.community.gateway.external.socket.WebSocketMessagingHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TenantMessagingHandlerMappingPostProcessorTest {

    private final GenericApplicationContext context = new GenericApplicationContext();

    private final TenantMessagingHandlerMappingPostProcessor processor =
        new TenantMessagingHandlerMappingPostProcessor(tenantId -> session -> Mono.empty());

    TenantMessagingHandlerMappingPostProcessorTest() {
        context.refresh();
        processor.setApplicationContext(context);
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void messagingMappingIsReplacedAndRegistered() {
        WebSocketMessagingHandler original = new WebSocketMessagingHandler(
            mock(MessagingManager.class), mock(UserTokenManager.class), mock(ReactiveAuthenticationManager.class));
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping(
            Collections.singletonMap(TenantMessagingHandlerMappingPostProcessor.MESSAGING_PATH, original),
            Ordered.HIGHEST_PRECEDENCE);

        Object result = processor.postProcessAfterInitialization(mapping, "webSocketMessagingHandlerMapping");

        SimpleUrlHandlerMapping replaced = assertInstanceOf(SimpleUrlHandlerMapping.class, result);
        assertNotSame(mapping, replaced);
        assertEquals(Ordered.HIGHEST_PRECEDENCE, replaced.getOrder());
        assertInstanceOf(TenantImpersonationWebSocketHandler.class,
                         replaced.getUrlMap().get(TenantMessagingHandlerMappingPostProcessor.MESSAGING_PATH));
        assertFalse(replaced.getHandlerMap().isEmpty(), "替换后的 mapping 必须完成 handler 注册");
    }

    @Test
    void unrelatedBeansAreUntouched() {
        SimpleUrlHandlerMapping other = new SimpleUrlHandlerMapping(
            Collections.singletonMap("/other/**", new Object()), 0);
        Object plain = new Object();

        assertSame(other, processor.postProcessAfterInitialization(other, "other"));
        assertSame(plain, processor.postProcessAfterInitialization(plain, "plain"));
    }
}
