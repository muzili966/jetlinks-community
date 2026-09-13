package org.jetlinks.community.cs.card;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 卡片消息的内容, 序列化成 JSON 存进消息 content. 三端(官网、控制台浮窗、坐席工作台)按同一结构渲染,
 * 新卡片种类优先复用这些字段, 不必改前端.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
@NoArgsConstructor
public class CsCard {

    @Schema(description = "卡片种类, 如 link / renewal")
    private String kind;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "醒目金额, 如 ¥5,400.00")
    private String amountText;

    @Schema(description = "明细行")
    private List<Field> fields;

    @Schema(description = "按钮")
    private Action action;

    @Schema(description = "卡片失效时间(毫秒), 如支付单过期时间")
    private Long expireAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Field {

        public static final String TYPE_TEXT = "text";

        /** value 为毫秒时间戳, 前端按本地时区格式化 */
        public static final String TYPE_DATETIME = "datetime";

        private String label;
        private String value;
        private String type;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {

        /** 控制台内路由跳转, target 是路由路径 */
        public static final String TYPE_ROUTE = "route";

        /** 新窗口打开, target 是 http/https 地址 */
        public static final String TYPE_URL = "url";

        private String type;
        private String text;
        private String target;
    }
}
