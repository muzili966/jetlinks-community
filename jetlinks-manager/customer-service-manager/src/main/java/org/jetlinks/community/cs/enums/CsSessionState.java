package org.jetlinks.community.cs.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

/**
 * 会话状态: 排队中 → 接待中 → 已结束.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum CsSessionState implements EnumDict<String> {

    queued("排队中"),
    active("接待中"),
    closed("已结束");

    private final String text;

    @Override
    public String getValue() {
        return name();
    }
}
