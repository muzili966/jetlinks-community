package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.exception.ValidationException;

/**
 * 访客在会话中补充称呼与联系方式.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsSessionContactRequest {

    @Size(max = 64)
    @Schema(description = "称呼")
    private String visitorName;

    @Size(max = 64)
    @Schema(description = "手机号或微信号")
    private String visitorContact;

    public void validate() {
        boolean hasName = visitorName != null && !visitorName.isBlank();
        boolean hasContact = visitorContact != null && !visitorContact.isBlank();
        if (!hasName && !hasContact) {
            throw new ValidationException.NoStackTrace("error.cs_chat_contact_required");
        }
    }
}
