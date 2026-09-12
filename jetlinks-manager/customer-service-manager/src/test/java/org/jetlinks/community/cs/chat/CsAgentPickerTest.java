package org.jetlinks.community.cs.chat;

import org.jetlinks.community.cs.entity.CsAgentEntity;
import org.jetlinks.community.cs.enums.CsAgentStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsAgentPickerTest {

    private static final int DEFAULT_MAX = 5;

    private CsAgentEntity agent(String id, CsAgentStatus status, Integer max, Long lastActive) {
        CsAgentEntity agent = new CsAgentEntity();
        agent.setId(id);
        agent.setName(id);
        agent.setStatus(status);
        agent.setMaxConcurrent(max);
        agent.setLastActiveAt(lastActive);
        return agent;
    }

    private Map<String, Integer> counts(Object... pairs) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void picksLeastLoadedOnlineAgent() {
        Optional<CsAgentEntity> picked = CsAgentPicker.pick(
            Arrays.asList(agent("a", CsAgentStatus.online, 5, 1L), agent("b", CsAgentStatus.online, 5, 1L)),
            counts("a", 3, "b", 1),
            DEFAULT_MAX);
        assertEquals("b", picked.map(CsAgentEntity::getId).orElse(null));
    }

    @Test
    void skipsBusyOfflineAndFullAgents() {
        Optional<CsAgentEntity> picked = CsAgentPicker.pick(
            Arrays.asList(agent("busy", CsAgentStatus.busy, 5, 9L),
                          agent("off", CsAgentStatus.offline, 5, 9L),
                          agent("full", CsAgentStatus.online, 2, 9L),
                          agent("ok", CsAgentStatus.online, 5, 1L)),
            counts("full", 2, "ok", 4),
            DEFAULT_MAX);
        assertEquals("ok", picked.map(CsAgentEntity::getId).orElse(null));
    }

    @Test
    void tieGoesToMostRecentlyActive() {
        Optional<CsAgentEntity> picked = CsAgentPicker.pick(
            Arrays.asList(agent("old", CsAgentStatus.online, 5, 10L),
                          agent("fresh", CsAgentStatus.online, 5, 20L),
                          agent("never", CsAgentStatus.online, 5, null)),
            Collections.emptyMap(),
            DEFAULT_MAX);
        assertEquals("fresh", picked.map(CsAgentEntity::getId).orElse(null));
    }

    @Test
    void nullMaxConcurrentFallsBackToDefault() {
        CsAgentEntity agent = agent("a", CsAgentStatus.online, null, 1L);
        assertTrue(CsAgentPicker.pick(Collections.singletonList(agent), counts("a", 4), DEFAULT_MAX).isPresent());
        assertFalse(CsAgentPicker.pick(Collections.singletonList(agent), counts("a", 5), DEFAULT_MAX).isPresent());
    }

    @Test
    void emptyWhenNobodyAvailable() {
        assertFalse(CsAgentPicker.pick(Collections.emptyList(), Collections.emptyMap(), DEFAULT_MAX).isPresent());
    }
}
