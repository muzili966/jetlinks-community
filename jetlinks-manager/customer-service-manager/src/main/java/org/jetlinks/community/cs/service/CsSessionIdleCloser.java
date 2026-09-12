package org.jetlinks.community.cs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.cs.CsProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 周期结束长时间无消息的会话, 避免访客关掉页面后会话一直占着坐席并发额度.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Slf4j
@RequiredArgsConstructor
public class CsSessionIdleCloser implements DisposableBean {

    private final CsProperties properties;
    private final CsSessionService sessionService;

    private volatile Disposable task;

    @EventListener
    public void start(ApplicationReadyEvent event) {
        Duration interval = properties.getChat().getIdleCheckInterval();
        task = Flux
            .interval(interval, interval)
            .concatMap(ignore -> closeIdle()
                .onErrorResume(err -> {
                    log.error("close idle customer-service sessions failed", err);
                    return Mono.empty();
                }))
            .subscribe();
        log.info("customer-service idle session closer started, interval={}, idleTimeout={}",
                 interval, properties.getChat().getIdleTimeout());
    }

    Mono<Void> closeIdle() {
        long cutoff = cutoffTime(System.currentTimeMillis(), properties.getChat().getIdleTimeout());
        return sessionService
            .closeIdleBefore(cutoff)
            .doOnNext(count -> {
                if (count > 0) {
                    log.info("closed {} idle customer-service sessions", count);
                }
            })
            .then();
    }

    static long cutoffTime(long now, Duration idleTimeout) {
        return now - idleTimeout.toMillis();
    }

    @Override
    public void destroy() {
        if (task != null && !task.isDisposed()) {
            task.dispose();
        }
    }
}
