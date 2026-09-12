package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.cs.enums.CsAgentStatus;

/**
 * 坐席切换自己的状态.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsAgentStatusRequest {

    @NotNull(message = "状态不能为空")
    @Schema(description = "状态")
    private CsAgentStatus status;

    @Min(1)
    @Max(50)
    @Schema(description = "同时接待上限, 留空沿用之前设置")
    private Integer maxConcurrent;
}
