package org.jetlinks.community.pay.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.community.pay.PayProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 周期关闭过期未付的支付单. 多节点同时跑是安全的: 关单用 where status = pending 的条件更新, 同一张单只会关一次.
 *
 * @author pay-manager
 * @since 2.11
 */
@Slf4j
@RequiredArgsConstructor
public class PayOrderExpireCloser implements DisposableBean {

    private final PayProperties properties;
    private final PayOrderService orderService;

    private volatile Disposable task;

    @EventListener
    public void start(ApplicationReadyEvent event) {
        Duration interval = properties.getExpireCheckInterval();
        task = Flux
            .interval(interval, interval)
            .concatMap(ignore -> orderService
                .closeExpiredBefore(System.currentTimeMillis())
                .doOnNext(result -> {
                    if (result.getClosed() > 0 || result.getFailed() > 0) {
                        log.info("closed {} expired pay orders, {} failed", result.getClosed(), result.getFailed());
                    }
                })
                .onErrorResume(err -> {
                    log.error("close expired pay orders failed", err);
                    return Mono.empty();
                }))
            .subscribe();
        log.info("pay order expire closer started, interval={}, orderTtl={}", interval, properties.getOrderTtl());
    }

    @Override
    public void destroy() {
        if (task != null && !task.isDisposed()) {
            task.dispose();
        }
    }
}
