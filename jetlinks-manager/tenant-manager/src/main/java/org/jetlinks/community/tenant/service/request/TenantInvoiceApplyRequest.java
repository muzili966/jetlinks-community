package org.jetlinks.community.tenant.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 发票申请请求.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@Setter
public class TenantInvoiceApplyRequest {

    @NotEmpty(message = "请选择要开票的订单")
    @Schema(description = "订单号列表(必须为同一租户的已支付未开票订单)")
    private List<String> orderIdList;

    @Schema(description = "发票类型: normal(普票, 默认)/special(专票)", defaultValue = "normal")
    private String invoiceType = "normal";

    @NotBlank(message = "发票抬头不能为空")
    @Schema(description = "发票抬头")
    private String title;

    @NotBlank(message = "纳税人识别号不能为空")
    @Schema(description = "纳税人识别号")
    private String taxNo;

    @Schema(description = "开户银行")
    private String bankName;

    @Schema(description = "银行账号")
    private String bankAccount;

    @Schema(description = "注册地址")
    private String address;

    @Schema(description = "注册电话")
    private String phone;

    @Schema(description = "接收邮箱(电子发票)")
    private String email;

    @Schema(description = "备注")
    private String remark;
}
