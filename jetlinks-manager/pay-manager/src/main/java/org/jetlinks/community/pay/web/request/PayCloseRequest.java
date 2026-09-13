package org.jetlinks.community.pay.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 关闭支付单.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@Setter
public class PayCloseRequest {

    @Size(max = 256)
    @Schema(description = "关闭原因")
    private String reason;
}
