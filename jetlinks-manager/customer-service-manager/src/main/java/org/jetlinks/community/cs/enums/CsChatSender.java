package org.jetlinks.community.cs.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

/**
 * 会话消息发送方.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum CsChatSender implements EnumDict<String> {

    visitor("访客"),
    agent("客服"),
    system("系统");

    private final String text;

    @Override
    public String getValue() {
        return name();
    }
}
