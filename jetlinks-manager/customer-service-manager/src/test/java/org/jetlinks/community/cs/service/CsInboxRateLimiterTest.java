package org.jetlinks.community.cs.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsInboxRateLimiterTest {

    private static final int LIMIT = 3;
    private static final Duration WINDOW = Duration.ofHours(1);

    /** 可拨动的时钟, 让窗口滑动可控 */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.ofEpochMilli(1_800_000_000_000L);

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void blocksAfterLimitWithinWindow() {
        MutableClock clock = new MutableClock();
        CsInboxRateLimiter limiter = new CsInboxRateLimiter(LIMIT, WINDOW, clock);
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));
    }

    @Test
    void windowSlidesAsOldHitsExpire() {
        MutableClock clock = new MutableClock();
        CsInboxRateLimiter limiter = new CsInboxRateLimiter(LIMIT, WINDOW, clock);
        limiter.tryAcquire("ip");
        clock.advance(Duration.ofMinutes(30));
        limiter.tryAcquire("ip");
        limiter.tryAcquire("ip");
        assertFalse(limiter.tryAcquire("ip"));
        // 第一条过期后放出一个名额, 后两条仍在窗口内
        clock.advance(Duration.ofMinutes(31));
        assertTrue(limiter.tryAcquire("ip"));
        assertFalse(limiter.tryAcquire("ip"));
    }

    @Test
    void keysAreIndependent() {
        MutableClock clock = new MutableClock();
        CsInboxRateLimiter limiter = new CsInboxRateLimiter(1, WINDOW, clock);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    void idleKeysAreEvictedOnceThresholdExceeded() {
        MutableClock clock = new MutableClock();
        CsInboxRateLimiter limiter = new CsInboxRateLimiter(1, WINDOW, clock);
        for (int i = 0; i <= CsInboxRateLimiter.EVICT_THRESHOLD; i++) {
            limiter.tryAcquire("ip-" + i);
        }
        clock.advance(WINDOW.plusSeconds(1));
        limiter.tryAcquire("fresh");
        assertEquals(1, limiter.activeKeys());
    }
}
