package org.jetlinks.community.pay.spi;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发起支付后前端下一步要做的动作. type 是前端分发依据, 新渠道优先复用已有类型.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PayAction {

    public static final String TYPE_REDIRECT = "redirect";
    public static final String TYPE_QR_CODE = "qrcode";
    public static final String TYPE_OFFLINE = "offline";
    public static final String TYPE_SIMULATE = "simulate";

    @Schema(description = "动作类型: redirect / qrcode / offline / simulate")
    private final String type;

    @Schema(description = "跳转地址(redirect)")
    private final String url;

    @Schema(description = "二维码内容(qrcode)")
    private final String qrContent;

    @Schema(description = "展示给付款人的说明, 如线下转账的收款账户")
    private final Map<String, String> instructions;

    public static PayAction redirect(String url) {
        return new PayAction(TYPE_REDIRECT, url, null, Collections.emptyMap());
    }

    public static PayAction qrCode(String content) {
        return new PayAction(TYPE_QR_CODE, null, content, Collections.emptyMap());
    }

    public static PayAction offline(Map<String, String> instructions) {
        return new PayAction(TYPE_OFFLINE, null, null, Collections.unmodifiableMap(new LinkedHashMap<>(instructions)));
    }

    public static PayAction simulate() {
        return new PayAction(TYPE_SIMULATE, null, null, Collections.emptyMap());
    }
}
