package org.jetlinks.community.cs.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

/**
 * 线索状态: 新线索 → 跟进中 → 已转化 / 无效; 无效可重新激活为新线索.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum CsLeadState implements EnumDict<String> {

    pending("新线索"),
    following("跟进中"),
    converted("已转化"),
    invalid("无效");

    private final String text;

    @Override
    public String getValue() {
        return name();
    }
}
