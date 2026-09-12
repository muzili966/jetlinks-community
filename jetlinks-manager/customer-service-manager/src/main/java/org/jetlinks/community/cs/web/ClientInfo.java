package org.jetlinks.community.cs.web;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;

/**
 * 匿名请求的客户端信息, 随留言一起保存用于防刷与溯源.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class ClientInfo {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final int USER_AGENT_MAX_LENGTH = 512;

    private final String ip;
    private final String userAgent;

    public static ClientInfo from(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        String remoteHost = remote == null || remote.getAddress() == null
            ? null
            : remote.getAddress().getHostAddress();
        String ip = ClientAddress.resolve(request.getHeaders().getFirst(FORWARDED_FOR), remoteHost);
        String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
        return new ClientInfo(ip, truncate(userAgent));
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= USER_AGENT_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, USER_AGENT_MAX_LENGTH);
    }
}
