package org.jetlinks.community.cs.service;

import org.jetlinks.community.cs.card.CsCard;
import org.jetlinks.community.cs.card.CsCardDraft;
import org.jetlinks.community.cs.chat.CsSessionEvent;
import org.jetlinks.community.cs.entity.CsChatMessageEntity;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.cs.enums.CsChatMessageType;
import org.jetlinks.community.cs.enums.CsChatSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsCardMessageTest {

    private CsSessionEntity session() {
        CsSessionEntity session = new CsSessionEntity();
        session.setId("s1");
        session.setAgentId("a1");
        session.setAgentName("小王");
        return session;
    }

    @Test
    void cardMessageCarriesJsonAndReference() {
        CsCard card = new CsCard();
        card.setKind("renewal");
        card.setTitle("续费 标准版");
        CsChatMessageEntity message = CsSessionService
            .buildCardMessage(session(), new CsCardDraft(card, "pay-order", "p1", "pending"), 1L);

        assertEquals(CsChatMessageType.card, message.getType());
        assertEquals(CsChatSender.agent, message.getSender());
        assertEquals("小王", message.getSenderName());
        assertEquals("pay-order", message.getRefType());
        assertEquals("p1", message.getRefId());
        assertEquals("pending", message.getRefStatus());
        assertTrue(message.getContent().contains("\"title\":\"续费 标准版\""));
        assertEquals("[卡片] 续费 标准版", CsSessionService.summaryOf(message));
    }

    @Test
    void cardSummaryFallsBackWhenContentBroken() {
        assertEquals("[卡片]", CsSessionService.cardSummary("not json"));
        assertEquals("[卡片]", CsSessionService.cardSummary("{}"));
        assertEquals("[卡片]", CsSessionService.cardSummary(null));
    }

    @Test
    void cardIsNotAnAttachment() {
        assertFalse(CsChatMessageType.card.isAttachment(), "卡片 content 是 JSON, 当成附件会被拼成文件地址");
        assertFalse(CsChatMessageType.text.isAttachment());
        assertTrue(CsChatMessageType.image.isAttachment());
        assertTrue(CsChatMessageType.file.isAttachment());
    }

    @Test
    void cardEventUsesCardType() {
        CsChatMessageEntity message = new CsChatMessageEntity();
        message.setId("m1");
        CsSessionEvent event = CsSessionService.cardEvent(session(), message);
        assertEquals(CsSessionEvent.TYPE_CARD, event.getType());
        assertEquals("s1", event.getSessionId());
        assertEquals("m1", event.getMessage().getId());
    }
}
