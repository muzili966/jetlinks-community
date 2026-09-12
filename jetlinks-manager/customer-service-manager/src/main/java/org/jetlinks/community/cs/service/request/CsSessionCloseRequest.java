package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 坐席结束会话时的标签.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsSessionCloseRequest {

    @Schema(description = "会话标签, 如 售前 / 报价 / 技术支持")
    private List<String> tags;
}
