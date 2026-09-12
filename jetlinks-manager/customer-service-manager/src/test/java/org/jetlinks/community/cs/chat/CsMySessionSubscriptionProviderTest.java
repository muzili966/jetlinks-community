package org.jetlinks.community.cs.chat;

import org.jetlinks.community.gateway.external.SubscribeRequest;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CsMySessionSubscriptionProviderTest {

    private SubscribeRequest request(java.util.Map<String, Object> parameter) {
        return SubscribeRequest.builder().id("1").topic(CsChatTopics.MY_SESSION).parameter(parameter).build();
    }

    @Test
    void readsSessionIdFromParameter() {
        assertEquals("s1", CsMySessionSubscriptionProvider.sessionIdOf(request(Collections.singletonMap("sessionId", "s1"))));
    }

    @Test
    void missingParameterDoesNotThrow() {
        assertNull(CsMySessionSubscriptionProvider.sessionIdOf(request(null)));
        assertEquals("", CsMySessionSubscriptionProvider.sessionIdOf(request(Collections.emptyMap())));
    }

    @Test
    void ownTopicIsSeparateFromWorkbench() {
        assertEquals("/cs/my-session", CsChatTopics.MY_SESSION);
        assertEquals("/cs/workbench", CsChatTopics.WORKBENCH);
    }
}
