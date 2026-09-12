package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 访客对会话的评价.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsSessionRateRequest {

    @NotNull(message = "评分不能为空")
    @Min(1)
    @Max(5)
    @Schema(description = "评分 1-5")
    private Integer rating;

    @Size(max = 512)
    @Schema(description = "评价内容")
    private String comment;
}
