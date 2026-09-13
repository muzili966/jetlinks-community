package org.jetlinks.community.pay.spi;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 收银台上的业务明细.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class PayBizSummary {

    @Schema(description = "标题, 如 标准版 订阅")
    private final String title;

    @Schema(description = "明细行")
    private final List<Field> fields;

    @Schema(description = "支付完成后建议跳转的页面")
    private final String returnPath;

    /**
     * 明细行. 日期类字段传毫秒时间戳, 由前端按浏览器时区格式化, 后端不拼日期字符串.
     */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Field {

        public static final String TYPE_TEXT = "text";
        public static final String TYPE_DATETIME = "datetime";

        @Schema(description = "名称")
        private final String label;

        @Schema(description = "值; datetime 类型为毫秒时间戳, 为空表示无")
        private final String value;

        @Schema(description = "类型: text / datetime")
        private final String type;

        public static Field text(String label, String value) {
            return new Field(label, value, TYPE_TEXT);
        }

        public static Field datetime(String label, Long epochMillis) {
            return new Field(label, epochMillis == null ? null : String.valueOf(epochMillis), TYPE_DATETIME);
        }
    }
}
