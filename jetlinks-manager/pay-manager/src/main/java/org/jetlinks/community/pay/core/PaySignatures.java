package org.jetlinks.community.pay.core;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 回调验签工具: HMAC-SHA256 + 参数按键排序拼接. 沙箱渠道使用, 也供 Webhook 形式的第三方渠道复用.
 *
 * @author pay-manager
 * @since 2.11
 */
public final class PaySignatures {

    public static final String SIGN_FIELD = "sign";
    private static final String ALGORITHM = "HmacSHA256";

    private PaySignatures() {
    }

    /**
     * 待签名串: 去掉 sign 字段与空值, 按键字典序拼成 k1=v1&k2=v2
     */
    public static String canonical(Map<String, String> fields) {
        return new TreeMap<>(fields)
            .entrySet()
            .stream()
            .filter(entry -> !SIGN_FIELD.equals(entry.getKey()))
            .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));
    }

    public static String hmacSha256Hex(String secret, String content) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    public static String sign(String secret, Map<String, String> fields) {
        return hmacSha256Hex(secret, canonical(fields));
    }

    /**
     * 常量时间比较, 防止按响应耗时逐位猜出签名
     */
    public static boolean verify(String secret, Map<String, String> fields, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        byte[] expected = sign(secret, fields).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
