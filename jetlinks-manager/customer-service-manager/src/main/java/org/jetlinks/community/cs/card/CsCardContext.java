package org.jetlinks.community.cs.card;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.community.cs.entity.CsSessionEntity;

import java.util.Collections;
import java.util.Map;

/**
 * 生成卡片的上下文. 会话来自数据库记录, 是可信的(比如租户ID); params 来自坐席输入, 按不可信数据校验.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class CsCardContext {

    private final CsSessionEntity session;

    private final Map<String, Object> params;

    private final String operatorId;

    public Map<String, Object> paramsOrEmpty() {
        return params == null ? Collections.emptyMap() : params;
    }

    public String text(String name) {
        Object value = paramsOrEmpty().get(name);
        return value == null ? null : String.valueOf(value).strip();
    }

    public int integer(String name, int defaultValue) {
        Object value = paramsOrEmpty().get(name);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException e) {
            throw new org.hswebframework.web.exception.ValidationException.NoStackTrace("error.cs_card_param_invalid", name);
        }
    }
}
