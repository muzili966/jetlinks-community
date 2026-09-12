package org.jetlinks.community.cs.service;

import org.jetlinks.community.cs.entity.CsAgentEntity;
import org.jetlinks.community.cs.entity.CsChatMessageEntity;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.cs.enums.CsChatSender;
import org.jetlinks.community.cs.enums.CsSessionState;
import org.jetlinks.community.cs.service.request.CsSessionOpenRequest;
import org.jetlinks.community.cs.web.ClientInfo;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsSessionServiceTest {

    private static final long NOW = 1_800_000_000_000L;

    private CsSessionOpenRequest request() {
        CsSessionOpenRequest request = new CsSessionOpenRequest();
        request.setVisitorId("v1");
        request.setVisitorName("  ");
        request.setSourcePage("https://site/#pricing");
        request.setUtm(Collections.singletonMap("utm_source", "baidu"));
        return request;
    }

    private CsSessionEntity session() {
        return CsSessionService.buildSession(request(), new ClientInfo("1.2.3.4", "ua"), NOW);
    }

    @Test
    void newSessionStartsQueuedWithTokenAndZeroCounters() {
        CsSessionEntity session = session();
        assertNotNull(session.getId());
        assertNotNull(session.getVisitorToken());
        assertEquals(CsSessionState.queued, session.getState());
        assertNull(session.getVisitorName());
        assertEquals("1.2.3.4", session.getClientIp());
        assertEquals(NOW, session.getQueuedAt());
        assertEquals(NOW, session.getLastMessageAt());
        assertEquals(0, session.getMessageCount());
        assertEquals(0, session.getAgentUnread());
        assertEquals(0, session.getVisitorUnread());
    }

    @Test
    void assignMovesSessionToActive() {
        CsAgentEntity agent = new CsAgentEntity();
        agent.setId("u1");
        agent.setName("小王");
        CsSessionEntity session = CsSessionService.assignTo(session(), agent, NOW + 1);
        assertEquals(CsSessionState.active, session.getState());
        assertEquals("u1", session.getAgentId());
        assertEquals("小王", session.getAgentName());
        assertEquals(NOW + 1, session.getAcceptedAt());
    }

    @Test
    void visitorMessageBumpsAgentUnreadAndAgentMessageBumpsVisitorUnread() {
        CsSessionEntity session = session();
        CsChatMessageEntity fromVisitor = CsSessionService.buildMessage(session, CsChatSender.visitor, "你好\n\n在吗", NOW + 5);
        CsSessionService.applyMessage(session, fromVisitor);
        assertEquals("你好 在吗", session.getLastMessage());
        assertEquals(NOW + 5, session.getLastMessageAt());
        assertEquals(1, session.getMessageCount());
        assertEquals(1, session.getAgentUnread());
        assertEquals(0, session.getVisitorUnread());
        assertEquals("访客", fromVisitor.getSenderName());

        session.setAgentName("小王");
        CsChatMessageEntity fromAgent = CsSessionService.buildMessage(session, CsChatSender.agent, "在的", NOW + 6);
        CsSessionService.applyMessage(session, fromAgent);
        assertEquals(2, session.getMessageCount());
        assertEquals(1, session.getAgentUnread());
        assertEquals(1, session.getVisitorUnread());
        assertEquals("小王", fromAgent.getSenderName());
        assertEquals(CsSessionService.SYSTEM_SENDER_NAME,
                     CsSessionService.buildMessage(session, CsChatSender.system, "x", NOW).getSenderName());
    }

    @Test
    void summaryTruncatesLongContent() {
        String summary = CsSessionService.summarize("a".repeat(500));
        assertEquals(CsSessionService.LAST_MESSAGE_MAX_LENGTH, summary.length());
        assertEquals("", CsSessionService.summarize(null));
    }

    @Test
    void tokensAreUrlSafeAndUnique() {
        String token = CsSessionService.newToken();
        assertTrue(token.matches("[A-Za-z0-9_-]{32}"));
        assertNotEquals(token, CsSessionService.newToken());
    }

    @Test
    void closeTextDependsOnWhoClosed() {
        assertEquals("访客已结束会话", CsSessionService.closeText(CsSessionService.CLOSED_BY_VISITOR));
        assertEquals("客服已结束会话", CsSessionService.closeText(CsSessionService.CLOSED_BY_AGENT));
        assertTrue(CsSessionService.closeText(CsSessionService.CLOSED_BY_SYSTEM).contains("自动结束"));
    }
}
