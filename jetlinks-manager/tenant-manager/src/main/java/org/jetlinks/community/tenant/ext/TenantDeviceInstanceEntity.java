package org.jetlinks.community.tenant.ext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.device.entity.DeviceInstanceEntity;
import org.jetlinks.community.tenant.TenantAware;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.core.device.DeviceInfo;

import javax.persistence.Column;

/**
 * DeviceInstanceEntity 的租户扩展: 通过实体工厂映射替换原实体, 零侵入添加租户列.
 *
 * @see org.jetlinks.community.tenant.config.TenantEntityMappingCustomizer
 */
@Getter
@Setter
public class TenantDeviceInstanceEntity extends DeviceInstanceEntity implements TenantAware {

    @Column(name = "tenant_id", length = 64, updatable = false)
    @Schema(description = "租户ID(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private String tenantId;

    /**
     * 设备激活时把租户ID注入 DeviceOperator 配置,
     * 让无登录态的设备链路(上行/规则/告警)自带租户身份.
     */
    @Override
    public DeviceInfo toDeviceInfo(boolean includeConfiguration) {
        DeviceInfo info = super.toDeviceInfo(includeConfiguration);
        if (includeConfiguration && tenantId != null) {
            info.addConfig(TenantConstants.tenantId.getKey(), tenantId);
        }
        return info;
    }
}
