package org.jetlinks.community.pay.core;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * 金额换算. 支付核心只认「分」; 业务侧价格是「元」时在边界处换算一次, 不在中途混用.
 *
 * @author pay-manager
 * @since 2.11
 */
public final class PayAmounts {

    private static final int FEN_PER_YUAN = 100;
    private static final String DISPLAY_PATTERN = "#,##0.00";

    private PayAmounts() {
    }

    /**
     * 元 → 分. 溢出直接抛错, 不能让金额悄悄变成负数.
     */
    public static long fenOfYuan(long yuan) {
        return Math.multiplyExact(yuan, FEN_PER_YUAN);
    }

    /**
     * 分 → 展示文本, 如 180000 → ¥1,800.00
     */
    public static String format(long fen) {
        DecimalFormat format = new DecimalFormat(DISPLAY_PATTERN, DecimalFormatSymbols.getInstance(Locale.ROOT));
        return "¥" + format.format(BigDecimal.valueOf(fen).movePointLeft(2));
    }
}
