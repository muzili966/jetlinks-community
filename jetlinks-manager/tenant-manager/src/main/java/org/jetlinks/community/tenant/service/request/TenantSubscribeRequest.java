package org.jetlinks.community.tenant.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 套餐开通/续费请求.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@Setter
public class TenantSubscribeRequest {

    @NotBlank(message = "租户ID不能为空")
    @Schema(description = "租户ID")
    private String tenantId;

    @NotBlank(message = "套餐ID不能为空")
    @Schema(description = "套餐ID")
    private String planId;

    @Min(value = 1, message = "购买月数至少为1")
    @Schema(description = "购买月数(免费套餐忽略)", defaultValue = "1")
    private int months = 1;

    @Schema(description = "支付渠道: offline(线下收款, 默认)/wechat/alipay(预留)", defaultValue = "offline")
    private String payChannel = "offline";

    @Schema(description = "备注(如线下收款凭证号)")
    private String remark;
}
