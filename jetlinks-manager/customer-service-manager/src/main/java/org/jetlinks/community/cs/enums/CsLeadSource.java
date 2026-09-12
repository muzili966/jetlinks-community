package org.jetlinks.community.cs.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

/**
 * 线索来源.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum CsLeadSource implements EnumDict<String> {

    website("官网留言"),
    manual("手动录入"),
    wecom("企业微信"),
    other("其他");

    private final String text;

    @Override
    public String getValue() {
        return name();
    }
}
