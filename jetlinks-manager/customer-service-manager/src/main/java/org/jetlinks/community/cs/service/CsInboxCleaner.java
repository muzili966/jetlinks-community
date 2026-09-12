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
 * 留言保留期清理: 周期删除超过保留天数的留言原文. 线索是业务记录, 不在清理范围.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Slf4j
@RequiredArgsConstructor
public class CsInboxCleaner implements DisposableBean {

    private static final Duration INITIAL_DELAY = Duration.ofMinutes(5);

    private final CsProperties properties;
    private final CsInboxService inboxService;

    private volatile Disposable task;

    @EventListener
    public void start(ApplicationReadyEvent event) {
        Duration interval = properties.getInbox().getCleanInterval();
        task = Flux
            .interval(INITIAL_DELAY, interval)
            .concatMap(ignore -> clean()
                .onErrorResume(err -> {
                    log.error("clean expired customer-service inbox messages failed", err);
                    return Mono.empty();
                }))
            .subscribe();
        log.info("customer-service inbox cleaner started, interval={}, retentionDays={}",
                 interval, properties.getInbox().getRetentionDays());
    }

    Mono<Void> clean() {
        long cutoff = cutoffTime(System.currentTimeMillis(), properties.getInbox().getRetentionDays());
        return inboxService
            .deleteBefore(cutoff)
            .doOnNext(count -> {
                if (count > 0) {
                    log.info("cleaned {} expired customer-service inbox messages", count);
                }
            })
            .then();
    }

    static long cutoffTime(long now, int retentionDays) {
        return now - Duration.ofDays(retentionDays).toMillis();
    }

    @Override
    public void destroy() {
        if (task != null && !task.isDisposed()) {
            task.dispose();
        }
    }
}
