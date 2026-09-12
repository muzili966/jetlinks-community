package org.jetlinks.community.cs.web;

/**
 * 客户端地址解析: 反向代理场景取 X-Forwarded-For 的第一段.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public final class ClientAddress {

    public static final String UNKNOWN = "unknown";
    private static final String FORWARDED_SEPARATOR = ",";

    private ClientAddress() {
    }

    public static String resolve(String forwardedFor, String remoteHost) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(FORWARDED_SEPARATOR)[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return remoteHost == null || remoteHost.isBlank() ? UNKNOWN : remoteHost;
    }
}
