package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 控制台已登录用户发起会话. 称呼、联系方式、租户都由后端按当前登录身份自动登记, 不让前端传.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsSupportOpenRequest {

    @Size(max = 512)
    @Schema(description = "发起会话的页面")
    private String sourcePage;

    @Schema(description = "首条消息, 可为空")
    private String firstMessage;

    public boolean hasFirstMessage() {
        return firstMessage != null && !firstMessage.isBlank();
    }
}
