package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.cs.enums.CsLeadState;

/**
 * 变更线索状态.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsStateRequest {

    @NotNull(message = "目标状态不能为空")
    @Schema(description = "目标状态")
    private CsLeadState state;

    @Size(max = 512)
    @Schema(description = "原因(标记无效时建议填写)")
    private String reason;
}
