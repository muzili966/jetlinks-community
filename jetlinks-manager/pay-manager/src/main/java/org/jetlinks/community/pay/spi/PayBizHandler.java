package org.jetlinks.community.pay.spi;

import org.hswebframework.web.authorization.Authentication;
import reactor.core.publisher.Mono;

/**
 * 业务扩展点: 一种要收钱的业务(租户订阅续费、增购配额、私有化部署定金...)实现一个,
 * 由 {@code PayBizRegistry} 按 {@link #getBizType()} 汇总. 支付核心只管钱, 收到钱之后做什么由业务决定.
 * <p>
 * 契约:
 * <ul>
 *     <li>{@link #getBizType()} 写进支付单, 是稳定契约</li>
 *     <li>{@link #onPaid} 必须幂等: 网关会重复通知, 人工也可能重复确认</li>
 *     <li>{@link #onPaid} 与支付单置为已支付在同一事务里执行; 抛错会回滚, 支付单保持待支付, 等网关重试</li>
 * </ul>
 *
 * @author pay-manager
 * @since 2.11
 */
public interface PayBizHandler {

    String getBizType();

    /**
     * 当前用户能否查看并支付这笔订单; 不能时返回 error. 收银台每次访问都会调用.
     */
    Mono<Void> assertPayable(PayOrderInfo order, Authentication auth);

    /**
     * 支付成功后履约
     */
    Mono<Void> onPaid(PayOrderInfo order);

    /**
     * 支付单关闭(超时或人工关闭), 业务侧释放占用
     */
    default Mono<Void> onClosed(PayOrderInfo order) {
        return Mono.empty();
    }

    /**
     * 收银台上展示的业务明细, 例如套餐、时长、续费后到期时间
     */
    default Mono<PayBizSummary> describe(PayOrderInfo order) {
        return Mono.empty();
    }
}
