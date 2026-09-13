package org.jetlinks.community.pay.spi;

/**
 * 渠道能力. 新增能力只追加, 不改名(会被前端据此展示操作).
 *
 * @author pay-manager
 * @since 2.11
 */
public enum PayCapability {

    /** 跳转网关收银台 */
    REDIRECT,

    /** 展示二维码让用户扫码 */
    QR_CODE,

    /** 网关异步回调通知支付结果 */
    NOTIFY,

    /** 由平台人工确认到账(线下转账) */
    OFFLINE_CONFIRM,

    /** 模拟支付成功, 仅用于开发联调 */
    SIMULATE
}
