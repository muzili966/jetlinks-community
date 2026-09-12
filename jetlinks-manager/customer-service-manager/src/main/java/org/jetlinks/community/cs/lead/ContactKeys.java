package org.jetlinks.community.cs.lead;

/**
 * 联系方式归一化: 同一个人多次留言时用手机号 / 微信号把留言归到同一条线索.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public final class ContactKeys {

    private static final String CN_COUNTRY_CODE = "86";
    private static final int CN_MOBILE_LENGTH = 11;
    private static final int PHONE_MIN_LENGTH = 6;
    private static final int PHONE_MAX_LENGTH = 20;

    private ContactKeys() {
    }

    /**
     * 只保留数字; 带 +86 / 86 前缀的国内手机号去掉国家码, 使 "+86 138..." 与 "138..." 归为同一键.
     *
     * @return 归一化后的号码, 无有效数字时返回 null
     */
    public static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == CN_MOBILE_LENGTH + CN_COUNTRY_CODE.length() && digits.startsWith(CN_COUNTRY_CODE)) {
            digits = digits.substring(CN_COUNTRY_CODE.length());
        }
        if (digits.length() < PHONE_MIN_LENGTH || digits.length() > PHONE_MAX_LENGTH) {
            return null;
        }
        return digits;
    }

    /**
     * 微信号大小写敏感, 只去首尾空白.
     */
    public static String normalizeWechat(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean hasContact(String phone, String wechat) {
        return normalizePhone(phone) != null || normalizeWechat(wechat) != null;
    }
}
