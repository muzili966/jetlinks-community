package org.jetlinks.community.cs.service;

import org.jetlinks.community.cs.entity.CsAgentEntity;
import org.jetlinks.community.cs.enums.CsAgentStatus;
import org.jetlinks.community.cs.service.request.CsAgentStatusRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsAgentServiceTest {

    @Test
    void statusChangeKeepsMaxConcurrentWhenNotGiven() {
        CsAgentEntity agent = new CsAgentEntity();
        agent.setId("u1");
        agent.setMaxConcurrent(3);
        CsAgentStatusRequest request = new CsAgentStatusRequest();
        request.setStatus(CsAgentStatus.online);

        CsAgentService.applyStatus(agent, request, "小王", 100L);
        assertEquals(CsAgentStatus.online, agent.getStatus());
        assertEquals(3, agent.getMaxConcurrent());
        assertEquals("小王", agent.getName());
        assertEquals(100L, agent.getLastActiveAt());
        assertTrue(agent.isOnline());

        request.setMaxConcurrent(8);
        CsAgentService.applyStatus(agent, request, "小王", 200L);
        assertEquals(8, agent.getMaxConcurrent());
    }

    @Test
    void maxConcurrentFallsBackWhenUnsetOrInvalid() {
        CsAgentEntity agent = new CsAgentEntity();
        assertEquals(5, agent.maxConcurrentOrDefault(5));
        agent.setMaxConcurrent(0);
        assertEquals(5, agent.maxConcurrentOrDefault(5));
        agent.setMaxConcurrent(2);
        assertEquals(2, agent.maxConcurrentOrDefault(5));
    }

    @Test
    void idleCutoffSubtractsTimeout() {
        assertEquals(1_000_000L - Duration.ofMinutes(30).toMillis(),
                     CsSessionIdleCloser.cutoffTime(1_000_000L, Duration.ofMinutes(30)));
    }
}
