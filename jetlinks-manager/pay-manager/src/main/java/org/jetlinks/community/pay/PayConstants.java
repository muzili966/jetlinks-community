package org.jetlinks.community.pay;

/**
 * 支付模块常量.
 *
 * @author pay-manager
 * @since 2.11
 */
public interface PayConstants {

    /**
     * 支付订单资源权限ID(前端权限码 pay-order:query / pay-order:save)
     */
    String RESOURCE_ORDER = "pay-order";

    /**
     * 回调入口前缀, 网关侧据此配置白名单
     */
    String NOTIFY_PATH = "/pay/notify";

    String CLOSE_REASON_EXPIRED = "超时未支付";

    String DEFAULT_CURRENCY = "CNY";
}
