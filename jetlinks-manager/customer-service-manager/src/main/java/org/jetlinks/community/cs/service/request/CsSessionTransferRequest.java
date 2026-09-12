package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 转接会话到另一位在线坐席.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsSessionTransferRequest {

    @NotBlank(message = "目标坐席不能为空")
    @Schema(description = "目标坐席用户ID")
    private String agentId;
}
