package org.jetlinks.community.cs.lead;

import lombok.Builder;
import lombok.Getter;
import org.jetlinks.community.cs.enums.CsLeadSource;
import org.jetlinks.community.cs.service.request.CsInboxSubmitRequest;

import java.util.Map;

/**
 * 新建线索所需的最小信息, 留言与在线会话都能转成它.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Builder
public class LeadDraft {

    private final String name;
    private final String phone;
    private final String wechat;
    private final String company;
    private final String summary;
    private final CsLeadSource source;
    private final String sourcePage;
    private final Map<String, String> utm;

    public static LeadDraft fromInbox(CsInboxSubmitRequest request) {
        return LeadDraft
            .builder()
            .name(request.getName())
            .phone(ContactKeys.normalizePhone(request.getPhone()))
            .wechat(ContactKeys.normalizeWechat(request.getWechat()))
            .company(request.getCompany())
            .summary(request.getContent())
            .source(CsLeadSource.website)
            .sourcePage(request.getSourcePage())
            .utm(request.getUtm())
            .build();
    }
}
