package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.exception.ValidationException;

/**
 * 发送一条会话消息(访客与坐席共用).
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsChatMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "消息内容")
    private String content;

    public String validated(int maxLength) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            throw new ValidationException.NoStackTrace("error.cs_chat_message_empty");
        }
        if (text.length() > maxLength) {
            throw new ValidationException.NoStackTrace("error.cs_chat_message_too_long");
        }
        return text;
    }
}
