package org.jetlinks.community.tenant;

/**
 * 租户数据标记接口: 实现此接口的实体参与租户隔离,
 * 未实现的实体(平台级白名单表)会被 TenantEventListener 跳过.
 *
 * @author tenant-manager
 * @since 2.11
 */
public interface TenantAware {

    String getTenantId();

    void setTenantId(String tenantId);
}
