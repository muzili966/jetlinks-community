package org.jetlinks.community.cs.chat;

import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.cs.enums.CsSessionState;
import org.jetlinks.core.event.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CsChatEventPublisherTest {

    private EventBus eventBus;
    private CsChatEventPublisher publisher;

    @BeforeEach
    void setUp() {
        eventBus = mock(EventBus.class);
        when(eventBus.publish(anyString(), any(Object.class))).thenReturn(Mono.just(1L));
        publisher = new CsChatEventPublisher(eventBus);
    }

    private CsSessionEvent event() {
        CsSessionEntity session = new CsSessionEntity();
        session.setId("s1");
        session.setState(CsSessionState.queued);
        return CsSessionEvent.of(CsSessionEvent.TYPE_QUEUED, session);
    }

    private List<String> publishedTopics(int expected) {
        ArgumentCaptor<String> topics = ArgumentCaptor.forClass(String.class);
        verify(eventBus, times(expected)).publish(topics.capture(), any(Object.class));
        return topics.getAllValues();
    }

    @Test
    void unassignedSessionGoesToSessionAndQueueTopics() {
        publisher.publish(event(), null).block();
        assertEquals(List.of("/cs/session/s1", "/cs/queue"), publishedTopics(2));
    }

    @Test
    void assignedSessionGoesToSessionAndAgentTopics() {
        publisher.publish(event(), "u1").block();
        assertEquals(List.of("/cs/session/s1", "/cs/agent/u1"), publishedTopics(2));
    }

    @Test
    void transferNotifiesBothAgents() {
        publisher.publishTransfer(event(), "u1", "u2").block();
        assertEquals(List.of("/cs/session/s1", "/cs/agent/u2", "/cs/agent/u1"), publishedTopics(3));
    }

    @Test
    void payloadCarriesTypeAndSessionView() {
        CsSessionEvent event = event();
        assertEquals("queued", event.toPayload().get("type"));
        assertEquals("s1", event.toPayload().get("sessionId"));
        assertEquals(CsSessionState.queued, ((CsSessionView) event.toPayload().get("session")).getState());
    }
}
