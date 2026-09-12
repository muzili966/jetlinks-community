package org.jetlinks.community.cs.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

/**
 * 跟进方式.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum CsFollowChannel implements EnumDict<String> {

    phone("电话"),
    wechat("微信"),
    email("邮件"),
    meeting("会面"),
    other("其他");

    private final String text;

    @Override
    public String getValue() {
        return name();
    }
}
