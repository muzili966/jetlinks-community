package org.jetlinks.community.cs.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

/**
 * 坐席状态: 只有在线才参与自动分配.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public enum CsAgentStatus implements EnumDict<String> {

    online("在线"),
    busy("忙碌"),
    offline("离线");

    private final String text;

    @Override
    public String getValue() {
        return name();
    }
}
