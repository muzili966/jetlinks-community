package org.jetlinks.community.cs.service;

import org.jetlinks.community.cs.CsConstants;
import org.jetlinks.community.cs.entity.CsInboxMessageEntity;
import org.jetlinks.community.notify.manager.entity.Notification;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsAgentNotifierTest {

    private CsInboxMessageEntity message(String company, String content) {
        CsInboxMessageEntity message = new CsInboxMessageEntity();
        message.setId("m1");
        message.setLeadId("l1");
        message.setName("张三");
        message.setPhone("13800138000");
        message.setCompany(company);
        message.setContent(content);
        message.setSourcePage("https://site/#pricing");
        message.setCreateTime(1_800_000_000_000L);
        return message;
    }

    @Test
    void summaryLineIncludesCompanyWhenPresent() {
        assertEquals("张三(天马科技) 留言: 想接 200 台 PLC",
                     CsAgentNotifier.summaryLine(message("天马科技", "想接 200 台 PLC")));
        assertEquals("张三 留言: 想接 200 台 PLC",
                     CsAgentNotifier.summaryLine(message(null, "想接 200 台 PLC")));
    }

    @Test
    void summaryLineCollapsesWhitespaceAndTruncates() {
        String longContent = "a".repeat(100);
        String line = CsAgentNotifier.summaryLine(message("", "第一行\n\n  第二行"));
        assertEquals("张三 留言: 第一行 第二行", line);
        assertTrue(CsAgentNotifier.summaryLine(message("", longContent)).endsWith("…"));
    }

    @Test
    void notificationTargetsUserAndLead() {
        Notification notification = CsAgentNotifier.buildNotification(message("X", "hi"), "u1", 1L);
        assertNotNull(notification.getId());
        assertEquals("u1", notification.getSubscriber());
        assertEquals("user", notification.getSubscriberType());
        assertEquals(CsConstants.NOTIFY_TOPIC_PROVIDER, notification.getTopicProvider());
        assertEquals("l1", notification.getDataId());
        assertEquals(1L, notification.getNotifyTime());
    }

    @Test
    void templateContextPrefersPhoneAndFillsBlanks() {
        Map<String, Object> context = CsAgentNotifier.templateContext(message(null, "hi"));
        assertEquals("13800138000", context.get("contact"));
        assertEquals("", context.get("company"));
        assertEquals("hi", context.get("content"));
        assertTrue(context.get("time").toString().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"));
    }
}
