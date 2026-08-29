package org.jetlinks.community.tenant.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

/**
 * 租户状态.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum TenantState implements EnumDict<String> {

    enabled("正常"),
    disabled("已禁用");

    private final String text;

    @Override
    public String getValue() {
        return name();
    }
}
