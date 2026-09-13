package org.jetlinks.community.pay.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 批量关单结果: 失败的单要单独统计, 不能混在"成功"里.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class PayCloseResult {

    private final int closed;
    private final int skipped;
    private final int failed;

    public static PayCloseResult empty() {
        return new PayCloseResult(0, 0, 0);
    }

    public static PayCloseResult ofClosed(boolean changed) {
        return changed ? new PayCloseResult(1, 0, 0) : new PayCloseResult(0, 1, 0);
    }

    public static PayCloseResult ofFailed() {
        return new PayCloseResult(0, 0, 1);
    }

    public PayCloseResult plus(PayCloseResult other) {
        return new PayCloseResult(closed + other.closed, skipped + other.skipped, failed + other.failed);
    }
}
