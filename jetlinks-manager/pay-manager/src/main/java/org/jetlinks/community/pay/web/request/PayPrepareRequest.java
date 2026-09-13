package org.jetlinks.community.pay.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 收银台发起支付.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@Setter
public class PayPrepareRequest {

    @NotBlank(message = "请选择支付方式")
    @Schema(description = "支付渠道ID, 如 offline / sandbox")
    private String channel;
}
