package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.exception.ValidationException;

/**
 * 线索转化为租户: 关联已有租户, 或按线索信息新建租户.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsConvertRequest {

    @Size(max = 64)
    @Schema(description = "关联已有租户ID(与新建二选一)")
    private String tenantId;

    @Pattern(regexp = "^[0-9a-zA-Z_\\-]*$", message = "租户ID只能由数字,字母,下划线和中划线组成")
    @Size(max = 64)
    @Schema(description = "新建租户ID")
    private String newTenantId;

    @Size(max = 64)
    @Schema(description = "新建租户名称, 留空取线索的公司名")
    private String newTenantName;

    @Size(max = 64)
    @Schema(description = "新建租户的套餐ID, 留空为免费版")
    private String planId;

    public boolean isLinkExisting() {
        return tenantId != null && !tenantId.isBlank();
    }

    public void validate() {
        boolean create = newTenantId != null && !newTenantId.isBlank();
        if (isLinkExisting() == create) {
            throw new ValidationException.NoStackTrace("error.cs_convert_target_required");
        }
    }
}
