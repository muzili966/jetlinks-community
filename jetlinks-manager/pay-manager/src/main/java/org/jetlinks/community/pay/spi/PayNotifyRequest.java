package org.jetlinks.community.pay.spi;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * 网关回调原文. 验签依赖原始 body 与头, 所以这里不做任何解析, 交给渠道自己处理.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class PayNotifyRequest {

    private final String channel;

    /** 头名统一小写 */
    private final Map<String, String> headers;

    private final Map<String, String> query;

    private final String body;

    private final String clientIp;

    public String header(String name) {
        return headers == null ? null : headers.get(name.toLowerCase(Locale.ROOT));
    }

    public Map<String, String> queryOrEmpty() {
        return query == null ? Collections.emptyMap() : query;
    }
}
