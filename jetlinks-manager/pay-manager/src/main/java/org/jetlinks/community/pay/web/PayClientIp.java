package org.jetlinks.community.pay.web;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;

/**
 * 请求方 IP: 反向代理后取 X-Forwarded-For 第一段. 只用于记录与网关预下单, 不作为鉴权依据(头可以伪造).
 *
 * @author pay-manager
 * @since 2.11
 */
final class PayClientIp {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final int MAX_LENGTH = 64;

    private PayClientIp() {
    }

    static String of(ServerHttpRequest request) {
        return resolve(request.getHeaders().getFirst(FORWARDED_FOR), remoteHost(request));
    }

    static String resolve(String forwardedFor, String remoteHost) {
        if (forwardedFor != null) {
            String first = forwardedFor.split(",")[0].strip();
            if (!first.isEmpty()) {
                return first.length() <= MAX_LENGTH ? first : first.substring(0, MAX_LENGTH);
            }
        }
        return remoteHost;
    }

    private static String remoteHost(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
    }
}
