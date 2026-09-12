package org.jetlinks.community.cs.service.request;

import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.cs.enums.CsLeadSource;
import org.jetlinks.community.cs.lead.LeadDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsSessionLeadRequestTest {

    private CsSessionEntity session(String name, String contact) {
        CsSessionEntity session = new CsSessionEntity();
        session.setId("s1");
        session.setVisitorName(name);
        session.setVisitorContact(contact);
        session.setLastMessage("想了解私有化部署");
        session.setSourcePage("https://site/#pricing");
        return session;
    }

    @Test
    void phoneLikeContactBecomesPhone() {
        LeadDraft draft = new CsSessionLeadRequest().toDraft(session("张三", "+86 138-0013-8000"));
        assertEquals("张三", draft.getName());
        assertEquals("13800138000", draft.getPhone());
        assertNull(draft.getWechat());
        assertEquals("想了解私有化部署", draft.getSummary());
        assertEquals(CsLeadSource.chat, draft.getSource());
        assertEquals("https://site/#pricing", draft.getSourcePage());
    }

    @Test
    void otherContactBecomesWechatAndNameDefaults() {
        LeadDraft draft = new CsSessionLeadRequest().toDraft(session(null, "wx_zhangsan"));
        assertEquals("在线访客", draft.getName());
        assertNull(draft.getPhone());
        assertEquals("wx_zhangsan", draft.getWechat());
    }

    @Test
    void agentInputOverridesSessionValues() {
        CsSessionLeadRequest request = new CsSessionLeadRequest();
        request.setName(" 李四 ");
        request.setPhone("13900000000");
        request.setCompany("天马科技");
        request.setSummary("要 200 台网关");
        LeadDraft draft = request.toDraft(session("张三", "wx_zhangsan"));
        assertEquals("李四", draft.getName());
        assertEquals("13900000000", draft.getPhone());
        assertNull(draft.getWechat());
        assertEquals("天马科技", draft.getCompany());
        assertEquals("要 200 台网关", draft.getSummary());
    }

    @Test
    void rejectsWhenNoContactAtAll() {
        assertThrows(ValidationException.class, () -> new CsSessionLeadRequest().toDraft(session("张三", null)));
    }
}
