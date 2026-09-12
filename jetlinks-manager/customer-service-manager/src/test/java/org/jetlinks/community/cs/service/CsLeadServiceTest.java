package org.jetlinks.community.cs.service;

import org.jetlinks.community.cs.entity.CsLeadEntity;
import org.jetlinks.community.cs.enums.CsLeadSource;
import org.jetlinks.community.cs.enums.CsLeadState;
import org.jetlinks.community.cs.lead.LeadDraft;
import org.jetlinks.community.cs.service.request.CsConvertRequest;
import org.jetlinks.community.cs.service.request.CsInboxSubmitRequest;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CsLeadServiceTest {

    private CsInboxSubmitRequest request() {
        CsInboxSubmitRequest request = new CsInboxSubmitRequest();
        request.setName("张三");
        request.setPhone("+86 138-0013-8000");
        request.setCompany("天马科技");
        request.setContent("想接 200 台 PLC");
        request.setSourcePage("https://site/#pricing");
        request.setUtm(Collections.singletonMap("utm_source", "baidu"));
        return request;
    }

    @Test
    void newLeadStartsPendingFromWebsite() {
        CsLeadEntity lead = CsLeadService.buildLead(LeadDraft.fromInbox(request()));
        assertNotNull(lead.getId());
        assertEquals(CsLeadState.pending, lead.getState());
        assertEquals(CsLeadSource.website, lead.getSource());
        assertEquals("13800138000", lead.getPhone());
        assertEquals("想接 200 台 PLC", lead.getSummary());
        assertEquals("baidu", lead.getUtm().get("utm_source"));
        assertEquals(0, lead.getFollowCount());
        assertEquals(0, lead.getMessageCount());
    }

    @Test
    void messageIncrementsCountAndFillsEmptySummaryOnly() {
        CsLeadEntity lead = new CsLeadEntity();
        lead.setMessageCount(null);
        CsLeadService.applyMessage(lead, "首条", 10L);
        assertEquals(1, lead.getMessageCount());
        assertEquals(10L, lead.getLastMessageAt());
        assertEquals("首条", lead.getSummary());

        CsLeadService.applyMessage(lead, "第二条", 20L);
        assertEquals(2, lead.getMessageCount());
        assertEquals(20L, lead.getLastMessageAt());
        assertEquals("首条", lead.getSummary());
    }

    @Test
    void newTenantNameFallsBackToCompanyThenName() {
        CsLeadEntity lead = CsLeadService.buildLead(LeadDraft.fromInbox(request()));
        CsConvertRequest convert = new CsConvertRequest();
        convert.setNewTenantId("t-001");
        convert.setPlanId("standard");

        TenantEntity tenant = CsLeadService.buildTenant(convert, lead);
        assertEquals("t-001", tenant.getId());
        assertEquals("天马科技", tenant.getName());
        assertEquals("standard", tenant.getPlanId());

        lead.setCompany(null);
        assertEquals("张三", CsLeadService.buildTenant(convert, lead).getName());

        convert.setNewTenantName("自定义");
        assertEquals("自定义", CsLeadService.buildTenant(convert, lead).getName());
    }
}
