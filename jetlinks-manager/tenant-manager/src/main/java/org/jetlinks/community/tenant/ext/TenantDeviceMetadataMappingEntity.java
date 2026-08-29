package org.jetlinks.community.tenant.ext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.device.entity.DeviceMetadataMappingEntity;
import org.jetlinks.community.tenant.TenantAware;

import javax.persistence.Column;

/**
 * DeviceMetadataMappingEntity 的租户扩展: 通过实体工厂映射替换原实体, 零侵入添加租户列.
 *
 * @see org.jetlinks.community.tenant.config.TenantEntityMappingCustomizer
 */
@Getter
@Setter
public class TenantDeviceMetadataMappingEntity extends DeviceMetadataMappingEntity implements TenantAware {

    @Column(name = "tenant_id", length = 64, updatable = false)
    @Schema(description = "租户ID(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private String tenantId;
}
