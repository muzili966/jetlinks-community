package org.jetlinks.community.tenant.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

/**
 * 发票申请状态.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum TenantInvoiceStatus implements EnumDict<String> {

    pending("待开具"),
    issued("已开具"),
    rejected("已驳回");

    private final String text;

    @Override
    public String getValue() {
        return name();
    }
}
