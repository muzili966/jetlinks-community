package org.jetlinks.community.tenant.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.exception.ValidationException;

/**
 * 生成续费支付. 只在服务端组装: 租户ID来自可信来源(会话记录 / 登录态), 不接受前端直接指定别人的租户.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@Setter
public class TenantRenewalRequest {

    static final int MONTHS_MIN = 1;
    static final int MONTHS_MAX = 36;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "套餐ID; 为空时续当前套餐")
    private String planId;

    @Schema(description = "购买月数 1-36")
    private int months = MONTHS_MIN;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "发起人用户ID")
    private String creatorId;

    public void validate() {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ValidationException.NoStackTrace("error.tenant_renew_tenant_required");
        }
        if (months < MONTHS_MIN || months > MONTHS_MAX) {
            throw new ValidationException.NoStackTrace("error.tenant_renew_months_invalid");
        }
    }
}
