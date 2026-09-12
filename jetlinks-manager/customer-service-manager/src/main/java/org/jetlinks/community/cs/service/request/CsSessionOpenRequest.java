package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 访客发起在线会话.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsSessionOpenRequest {

    @NotBlank(message = "访客ID不能为空")
    @Size(max = 64)
    @Schema(description = "访客ID(浏览器本地生成)")
    private String visitorId;

    @Size(max = 64)
    @Schema(description = "访客称呼, 可为空")
    private String visitorName;

    @Size(max = 64)
    @Schema(description = "访客联系方式, 可为空")
    private String visitorContact;

    @Size(max = 512)
    @Schema(description = "发起会话的页面")
    private String sourcePage;

    @Size(max = 512)
    @Schema(description = "来源站点")
    private String referrer;

    @Schema(description = "来源渠道参数")
    private Map<String, String> utm;

    @Schema(description = "首条消息, 可为空")
    private String firstMessage;

    public boolean hasFirstMessage() {
        return firstMessage != null && !firstMessage.isBlank();
    }
}
