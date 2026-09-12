package org.jetlinks.community.cs.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 匿名留言接口的按键(IP)滑动窗口限流. 节点内计数, 多节点部署时每个节点各自计数,
 * 作为验证码之外的第二道防线足够; 需要精确的全局限流时应放到网关层.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public class CsInboxRateLimiter {

    /** 活跃键超过此数量时清理一次空闲键, 防止 map 无界增长 */
    static final int EVICT_THRESHOLD = 10_000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public CsInboxRateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /**
     * @return true 表示放行并记一次; false 表示窗口内已达上限
     */
    public boolean tryAcquire(String key) {
        long now = clock.millis();
        if (hits.size() > EVICT_THRESHOLD) {
            evictIdle(now);
        }
        Deque<Long> stamps = hits.computeIfAbsent(key, ignore -> new ArrayDeque<>());
        synchronized (stamps) {
            prune(stamps, now);
            if (stamps.size() >= limit) {
                return false;
            }
            stamps.addLast(now);
            return true;
        }
    }

    int activeKeys() {
        return hits.size();
    }

    private void prune(Deque<Long> stamps, long now) {
        long threshold = now - windowMillis;
        while (!stamps.isEmpty() && stamps.peekFirst() <= threshold) {
            stamps.pollFirst();
        }
    }

    private void evictIdle(long now) {
        hits.entrySet().removeIf(entry -> {
            Deque<Long> stamps = entry.getValue();
            synchronized (stamps) {
                prune(stamps, now);
                return stamps.isEmpty();
            }
        });
    }
}
