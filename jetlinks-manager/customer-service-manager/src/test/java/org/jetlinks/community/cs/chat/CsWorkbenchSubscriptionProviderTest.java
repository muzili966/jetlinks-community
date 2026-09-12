package org.jetlinks.community.cs.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CsWorkbenchSubscriptionProviderTest {

    @Test
    void agentSubscribesOwnTopicAndQueue() {
        assertArrayEquals(new String[]{"/cs/agent/u1", "/cs/queue"}, CsWorkbenchSubscriptionProvider.topicsFor("u1"));
    }

    @Test
    void topicHelpersBuildConsistentPaths() {
        assertEquals("/cs/session/s1", CsChatTopics.session("s1"));
        assertEquals("/cs/agent/u1", CsChatTopics.agent("u1"));
        assertEquals("/cs/workbench", new CsWorkbenchSubscriptionProvider(null).getTopicPattern()[0]);
    }
}
