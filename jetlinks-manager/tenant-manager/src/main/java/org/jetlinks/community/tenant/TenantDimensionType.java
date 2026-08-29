package org.jetlinks.community.tenant;

import lombok.AllArgsConstructor;
import lombok.Generated;
import lombok.Getter;
import org.hswebframework.web.authorization.DimensionType;

/**
 * 租户维度类型, 与 {@link org.jetlinks.community.authorize.OrgDimensionType} 同机制.
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
@Getter
@Generated
public enum TenantDimensionType implements DimensionType {
    tenant("tenant", "租户");

    private final String id;
    private final String name;
}
