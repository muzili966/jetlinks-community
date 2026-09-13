package org.jetlinks.community.pay.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

/**
 * 支付单状态: 待支付 → 已支付 / 已关闭. 已支付、已关闭都是终态.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum PayOrderStatus implements EnumDict<String> {

    pending("待支付"),
    paid("已支付"),
    closed("已关闭");

    private final String text;

    @Override
    public String getValue() {
        return name();
    }
}
