package org.jetlinks.community.cs.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsInboxCleanerTest {

    @Test
    void cutoffIsRetentionDaysBeforeNow() {
        long now = 1_800_000_000_000L;
        assertEquals(now - Duration.ofDays(365).toMillis(), CsInboxCleaner.cutoffTime(now, 365));
        assertEquals(now, CsInboxCleaner.cutoffTime(now, 0));
    }
}
