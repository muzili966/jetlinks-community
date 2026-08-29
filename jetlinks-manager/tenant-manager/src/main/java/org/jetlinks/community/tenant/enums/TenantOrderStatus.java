package org.jetlinks.community.tenant.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

/**
 * 订阅订单状态.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum TenantOrderStatus implements EnumDict<String> {

    pending("待支付"),
    paid("已支付"),
    cancelled("已取消");

    private final String text;

    @Override
    public String getValue() {
        return name();
    }
}
