package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.cs.enums.CsLeadSource;
import org.jetlinks.community.cs.lead.ContactKeys;
import org.jetlinks.community.cs.lead.LeadDraft;

/**
 * 坐席把会话转为线索时补充的资料; 留空的字段取会话里访客自己填的.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsSessionLeadRequest {

    @Size(max = 64)
    @Schema(description = "称呼, 留空取访客称呼")
    private String name;

    @Size(max = 32)
    @Schema(description = "手机号")
    private String phone;

    @Size(max = 64)
    @Schema(description = "微信号")
    private String wechat;

    @Size(max = 128)
    @Schema(description = "公司")
    private String company;

    @Schema(description = "需求摘要, 留空取会话最后一条访客消息")
    private String summary;

    /**
     * 合并会话已有资料: 访客只填了一个联系方式字段, 按是否像手机号归到手机或微信.
     */
    public LeadDraft toDraft(CsSessionEntity session) {
        String phoneValue = firstNonBlank(phone, ContactKeys.normalizePhone(session.getVisitorContact()));
        String wechatValue = firstNonBlank(wechat, phoneValue == null ? session.getVisitorContact() : null);
        String normalizedPhone = ContactKeys.normalizePhone(phoneValue);
        String normalizedWechat = ContactKeys.normalizeWechat(wechatValue);
        if (!ContactKeys.hasContact(normalizedPhone, normalizedWechat)) {
            throw new ValidationException.NoStackTrace("error.cs_inbox_contact_required");
        }
        String nameValue = firstNonBlank(name, session.getVisitorName());
        return LeadDraft
            .builder()
            .name(nameValue == null ? "在线访客" : nameValue)
            .phone(normalizedPhone)
            .wechat(normalizedWechat)
            .company(company)
            .summary(firstNonBlank(summary, session.getLastMessage()))
            .source(CsLeadSource.chat)
            .sourcePage(session.getSourcePage())
            .utm(session.getUtm())
            .build();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        return second == null || second.isBlank() ? null : second.strip();
    }
}
