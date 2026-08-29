package org.jetlinks.community.tenant.notice;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantExpireNotifierTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final int DAYS = 7;
    private static final long WINDOW = Duration.ofDays(DAYS).toMillis();

    @Test
    void notInWindowYet() {
        // 距到期还有 8 天: 不提醒
        long expire = NOW + WINDOW + Duration.ofDays(1).toMillis();
        assertFalse(TenantExpireNotifier.shouldNotify(expire, null, NOW, DAYS));
    }

    @Test
    void inWindowFirstTime() {
        // 距到期 3 天且从未提醒: 提醒
        long expire = NOW + Duration.ofDays(3).toMillis();
        assertTrue(TenantExpireNotifier.shouldNotify(expire, null, NOW, DAYS));
    }

    @Test
    void alreadyNotifiedThisCycle() {
        // 本周期已提醒过: 不重复
        long expire = NOW + Duration.ofDays(3).toMillis();
        long notifiedInWindow = expire - WINDOW + 1;
        assertFalse(TenantExpireNotifier.shouldNotify(expire, notifiedInWindow, NOW, DAYS));
    }

    @Test
    void renewalResetsWindow() {
        // 上周期提醒过, 续费后到期时间后移 → 窗口重置, 新周期到点再提醒
        long oldNotifyTime = NOW - Duration.ofDays(30).toMillis();
        long newExpire = NOW + Duration.ofDays(3).toMillis();
        assertTrue(TenantExpireNotifier.shouldNotify(newExpire, oldNotifyTime, NOW, DAYS));
    }

    @Test
    void expiredStillNotifiesOnce() {
        // 已过期未提醒: 补一次提醒
        long expire = NOW - Duration.ofDays(1).toMillis();
        assertTrue(TenantExpireNotifier.shouldNotify(expire, null, NOW, DAYS));
        // 已提醒过则不再发
        assertFalse(TenantExpireNotifier.shouldNotify(expire, NOW - 1000, NOW, DAYS));
    }
}
