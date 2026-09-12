package org.jetlinks.community.tenant.ext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.system.authorization.api.entity.UserEntity;
import org.jetlinks.community.tenant.TenantAware;

import javax.persistence.Column;

/**
 * UserEntity 的租户扩展。
 * <p>
 * 没有它, 拿到 user:query 权限的租户管理员可以通过 /user/_query 枚举全平台账号
 * (s_user 无租户列, 行级隔离判定不生效)。
 * <p>
 * tenantId 特意保持可更新: 归属以 s_dimension_user 的 tenant 维度绑定为准,
 * 绑定/解绑时由 {@link org.jetlinks.community.tenant.service.TenantService} 回写本列。
 * 租户端的篡改防护在隔离监听器里(更新语句会剔除 tenantId 列)。
 * <p>
 * 登录链路不受影响: 按用户名查用户发生在认证建立之前, 无认证则不注入过滤。
 */
@Getter
@Setter
public class TenantUserEntity extends UserEntity implements TenantAware {

    @Column(name = "tenant_id", length = 64)
    @Schema(description = "所属租户ID(以维度绑定为准, 由绑定操作回写)", accessMode = Schema.AccessMode.READ_ONLY)
    private String tenantId;
}
