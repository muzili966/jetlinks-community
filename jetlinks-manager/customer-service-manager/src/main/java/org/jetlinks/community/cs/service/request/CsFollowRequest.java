package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.cs.enums.CsFollowChannel;

/**
 * 新增跟进记录.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsFollowRequest {

    @NotNull(message = "跟进方式不能为空")
    @Schema(description = "跟进方式")
    private CsFollowChannel channel;

    @NotBlank(message = "跟进内容不能为空")
    @Size(max = 2000)
    @Schema(description = "跟进内容")
    private String content;

    @Schema(description = "下次跟进时间(毫秒时间戳), 留空表示暂无计划")
    private Long nextFollowAt;
}
