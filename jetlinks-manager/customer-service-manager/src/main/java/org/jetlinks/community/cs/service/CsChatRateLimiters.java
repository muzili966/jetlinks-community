package org.jetlinks.community.cs.service;

import lombok.Getter;
import org.jetlinks.community.cs.CsProperties;

import java.time.Clock;
import java.time.Duration;

/**
 * 在线会话的两道限流: 按 IP 限制发起会话次数, 按会话限制发消息频率.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
public class CsChatRateLimiters {

    private static final Duration SESSION_WINDOW = Duration.ofHours(1);
    private static final Duration MESSAGE_WINDOW = Duration.ofMinutes(1);

    private final CsInboxRateLimiter sessions;
    private final CsInboxRateLimiter messages;

    public CsChatRateLimiters(CsProperties.Chat chat, Clock clock) {
        this.sessions = new CsInboxRateLimiter(chat.getSessionRateLimitPerHour(), SESSION_WINDOW, clock);
        this.messages = new CsInboxRateLimiter(chat.getMessageRateLimitPerMinute(), MESSAGE_WINDOW, clock);
    }
}
