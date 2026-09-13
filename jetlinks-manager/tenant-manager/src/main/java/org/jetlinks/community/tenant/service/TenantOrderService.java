package org.jetlinks.community.tenant.service;

import lombok.AllArgsConstructor;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.jetlinks.community.tenant.entity.TenantPlanEntity;
import org.jetlinks.community.tenant.enums.TenantOrderStatus;
import org.jetlinks.community.tenant.enums.TenantState;
import org.jetlinks.community.tenant.service.request.TenantSubscribeRequest;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.hswebframework.ezorm.rdb.mapping.ReactiveUpdate;
import org.hswebframework.web.id.IDGenerator;
import reactor.util.retry.Retry;

/**
 * 套餐订阅计费: 开通/续费生成订单流水并顺延订阅到期时间.
 * <p>
 * 当前支持线下收款(下单即已支付); 在线支付渠道(微信/支付宝)通过
 * {@code payChannel} + {@link TenantOrderStatus#pending} 预留.
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
public class TenantOrderService extends GenericReactiveCrudService<TenantOrderEntity, String> {

    static final String ORDER_TYPE_SUBSCRIBE = "subscribe";
    static final String ORDER_TYPE_RENEW = "renew";
    static final String ORDER_TYPE_CHANGE = "change";
    static final String PAY_CHANNEL_OFFLINE = "offline";

    /** 退款窗口：自支付之日起 14 天内可退，超期需走人工流程 */
    public static final int REFUND_WINDOW_DAYS = 14;

    private final TenantService tenantService;
    private final TenantPlanService planService;

    /**
     * 开通/续费套餐: 校验 → 生成已支付订单 → 更新租户订阅.
     * 免费套餐不生成订单, 直接切换且不限期.
     */
    @Transactional
    public Mono<TenantOrderEntity> subscribe(TenantSubscribeRequest request) {
        return Mono
            .zip(findTenant(request.getTenantId()), findPlan(request.getPlanId()))
            .flatMap(tp -> doSubscribe(tp.getT1(), tp.getT2(), request));
    }

    private Mono<TenantEntity> findTenant(String tenantId) {
        return tenantService
            .findById(tenantId)
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_not_exist", 404, tenantId)));
    }

    private Mono<TenantPlanEntity> findPlan(String planId) {
        return planService
            .findById(planId)
            .filter(plan -> plan.getState() != TenantState.disabled)
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_plan_not_available", 400, planId)));
    }

    private Mono<TenantOrderEntity> doSubscribe(TenantEntity tenant,
                                                TenantPlanEntity plan,
                                                TenantSubscribeRequest request) {
        long now = System.currentTimeMillis();
        boolean freePlan = plan.getMonthlyPrice() == null || plan.getMonthlyPrice() == 0;
        // 免费套餐不限期; 付费套餐从 max(当前时间, 原到期时间) 按日历月顺延
        Long expireAfter = freePlan
            ? null
            : computeExpireAfter(tenant.getSubscribeExpireTime(), request.getMonths(), now);

        Mono<Void> updateTenant = tenantService
            .createUpdate()
            .set(TenantEntity::getPlanId, plan.getId())
            .set(TenantEntity::getSubscribeExpireTime, expireAfter)
            .where(TenantEntity::getId, tenant.getId())
            .execute()
            .then();

        if (freePlan) {
            return updateTenant.then(Mono.empty());
        }
        TenantOrderEntity order = buildOrder(tenant, plan, request, expireAfter, now);
        return updateTenant
            .then(insert(order).thenReturn(order));
    }

    private TenantOrderEntity buildOrder(TenantEntity tenant,
                                         TenantPlanEntity plan,
                                         TenantSubscribeRequest request,
                                         Long expireAfter,
                                         long now) {
        TenantOrderEntity order = new TenantOrderEntity();
        order.setTenantId(tenant.getId());
        order.setTenantName(tenant.getName());
        order.setPlanId(plan.getId());
        order.setPlanName(plan.getName());
        order.setMonthlyPrice(plan.getMonthlyPrice());
        order.setMonths(request.getMonths());
        order.setTotalAmount(computeAmount(plan.getMonthlyPrice(), request.getMonths()));
        order.setOrderType(resolveOrderType(tenant.getPlanId(), plan.getId()));
        order.setPayChannel(request.getPayChannel() == null ? PAY_CHANNEL_OFFLINE : request.getPayChannel());
        // 线下收款: 下单即已支付; 在线支付渠道接入后此处改为 pending + 回调置 paid
        order.setStatus(TenantOrderStatus.paid);
        order.setPayTime(now);
        order.setExpireTimeAfter(expireAfter);
        order.setRemark(request.getRemark());
        return order;
    }

    /**
     * 订单退款：仅限已支付、未开票的订单。
     * <p>
     * 退款会<strong>回退租户的订阅到期时间</strong>（扣回本单顺延的月数），
     * 否则会出现「钱退了但服务照用」的漏洞。
     */
    @Transactional
    public Mono<Void> refund(String orderId, String reason) {
        return findById(orderId)
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_order_not_found", 404, orderId)))
            .flatMap(order -> {
                if (order.getStatus() != TenantOrderStatus.paid) {
                    return Mono.error(new BusinessException("error.tenant_order_not_paid", 400, orderId));
                }
                if (order.getInvoiceId() != null) {
                    return Mono.error(new BusinessException("error.tenant_order_invoiced_cannot_refund", 400, orderId));
                }
                if (isOutOfRefundWindow(order.getPayTime(), System.currentTimeMillis())) {
                    return Mono.error(new BusinessException(
                        "error.tenant_order_refund_window_expired", 400, REFUND_WINDOW_DAYS));
                }
                return rollbackSubscription(order)
                    .then(createUpdate()
                              .set(TenantOrderEntity::getStatus, TenantOrderStatus.refunded)
                              .set(TenantOrderEntity::getRemark,
                                   appendRemark(order.getRemark(), "退款: " + reason))
                              .where(TenantOrderEntity::getId, orderId)
                              .execute()
                              .then());
            });
    }

    /** 回退本单顺延的月数 */
    private Mono<Void> rollbackSubscription(TenantOrderEntity order) {
        int months = order.getMonths() == null ? 0 : order.getMonths();
        if (months <= 0) {
            return Mono.empty();
        }
        return tenantService
            .findById(order.getTenantId())
            .flatMap(tenant -> {
                Long expire = tenant.getSubscribeExpireTime();
                if (expire == null) {
                    return Mono.empty();
                }
                long rolledBack = ZonedDateTime
                    .ofInstant(Instant.ofEpochMilli(expire), ZoneId.systemDefault())
                    .minusMonths(months)
                    .toInstant()
                    .toEpochMilli();
                return tenantService
                    .createUpdate()
                    .set(TenantEntity::getSubscribeExpireTime, rolledBack)
                    .where(TenantEntity::getId, order.getTenantId())
                    .execute()
                    .then();
            });
    }

    /**
     * 是否已超出退款窗口。支付时间缺失时按不可退处理（宁可拦住，也不放过一笔无据可查的退款）。
     */
    public static boolean isOutOfRefundWindow(Long payTime, long now) {
        if (payTime == null) {
            return true;
        }
        return now - payTime > Duration.ofDays(REFUND_WINDOW_DAYS).toMillis();
    }

    // ---------- 在线支付 ----------

    /** 顺延到期时间时乐观更新冲突的最大重试次数 */
    static final int EXPIRE_UPDATE_RETRIES = 3;

    /**
     * 在线支付下单: 生成待支付订单, 不改租户订阅; 到账后由 {@link #fulfill} 顺延到期时间.
     * 免费套餐不需要付款, 直接拒绝.
     */
    @Transactional
    public Mono<TenantOrderEntity> createPending(TenantSubscribeRequest request) {
        return Mono
            .zip(findTenant(request.getTenantId()), findPlan(request.getPlanId()))
            .flatMap(tp -> {
                if (isFreePlan(tp.getT2())) {
                    return Mono.error(new BusinessException("error.tenant_plan_free_no_payment", 400, tp.getT2().getId()));
                }
                TenantOrderEntity order = buildPendingOrder(tp.getT1(), tp.getT2(), request);
                return insert(order).thenReturn(order);
            });
    }

    static boolean isFreePlan(TenantPlanEntity plan) {
        return plan.getMonthlyPrice() == null || plan.getMonthlyPrice() == 0;
    }

    /**
     * 先生成订单号: 支付单要用它做业务单号. 支付渠道到账时才确定, 这里留空.
     */
    static TenantOrderEntity buildPendingOrder(TenantEntity tenant, TenantPlanEntity plan, TenantSubscribeRequest request) {
        TenantOrderEntity order = new TenantOrderEntity();
        order.setId(IDGenerator.SNOW_FLAKE_STRING.generate());
        order.setTenantId(tenant.getId());
        order.setTenantName(tenant.getName());
        order.setPlanId(plan.getId());
        order.setPlanName(plan.getName());
        order.setMonthlyPrice(plan.getMonthlyPrice());
        order.setMonths(request.getMonths());
        order.setTotalAmount(computeAmount(plan.getMonthlyPrice(), request.getMonths()));
        order.setOrderType(resolveOrderType(tenant.getPlanId(), plan.getId()));
        order.setStatus(TenantOrderStatus.pending);
        order.setRemark(request.getRemark());
        return order;
    }

    /**
     * 到账履约, 幂等: 已支付直接返回; 只有待支付的订单会顺延订阅并置为已支付.
     * 支付核心已保证同一张支付单只回调一次, 这里仍按状态判断, 防人工重复调用.
     */
    @Transactional
    public Mono<Void> fulfill(String orderId, String payChannel, long paidAt) {
        return findById(orderId)
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_order_not_found", 404, orderId)))
            .flatMap(order -> {
                if (order.getStatus() == TenantOrderStatus.paid) {
                    return Mono.<Void>empty();
                }
                if (order.getStatus() != TenantOrderStatus.pending) {
                    return Mono.<Void>error(new BusinessException("error.tenant_order_not_pending", 400, orderId));
                }
                return extendSubscription(order)
                    .flatMap(expireAfter -> createUpdate()
                        .set(TenantOrderEntity::getStatus, TenantOrderStatus.paid)
                        .set(TenantOrderEntity::getPayChannel, payChannel)
                        .set(TenantOrderEntity::getPayTime, paidAt)
                        .set(TenantOrderEntity::getExpireTimeAfter, expireAfter)
                        .where(TenantOrderEntity::getId, orderId)
                        .and(TenantOrderEntity::getStatus, TenantOrderStatus.pending)
                        .execute())
                    .flatMap(updated -> updated == 0
                        ? Mono.<Void>error(new BusinessException("error.tenant_order_not_pending", 409, orderId))
                        : Mono.<Void>empty());
            });
    }

    /**
     * 顺延订阅到期时间. 同一租户可能有多笔订单同时到账, 用"到期时间没变才写入"的乐观更新,
     * 冲突时重读重算, 避免后到的一笔覆盖先到的一笔.
     */
    private Mono<Long> extendSubscription(TenantOrderEntity order) {
        int months = order.getMonths() == null ? 0 : order.getMonths();
        return findTenant(order.getTenantId())
            .flatMap(tenant -> {
                Long current = tenant.getSubscribeExpireTime();
                long next = computeExpireAfter(current, months, System.currentTimeMillis());
                ReactiveUpdate<TenantEntity> update = tenantService
                    .createUpdate()
                    .set(TenantEntity::getPlanId, order.getPlanId())
                    .set(TenantEntity::getSubscribeExpireTime, next);
                Mono<Integer> written = current == null
                    ? update.where(TenantEntity::getId, tenant.getId()).isNull(TenantEntity::getSubscribeExpireTime).execute()
                    : update.where(TenantEntity::getId, tenant.getId()).and(TenantEntity::getSubscribeExpireTime, current).execute();
                return written.flatMap(count -> count == 0
                    ? Mono.<Long>error(new ExpireChangedConcurrently())
                    : Mono.just(next));
            })
            .retryWhen(Retry.max(EXPIRE_UPDATE_RETRIES).filter(ExpireChangedConcurrently.class::isInstance));
    }

    /**
     * 支付单关闭时取消对应的待支付订单. 订单不存在或已不是待支付(已到账)属于正常情况, 不处理.
     */
    public Mono<Void> cancelPending(String orderId, String reason) {
        return findById(orderId)
            .filter(order -> order.getStatus() == TenantOrderStatus.pending)
            .flatMap(order -> createUpdate()
                .set(TenantOrderEntity::getStatus, TenantOrderStatus.cancelled)
                .set(TenantOrderEntity::getRemark, appendRemark(order.getRemark(), reason))
                .where(TenantOrderEntity::getId, orderId)
                .and(TenantOrderEntity::getStatus, TenantOrderStatus.pending)
                .execute())
            .then();
    }

    /** 乐观更新冲突, 只用于触发重试, 不需要堆栈 */
    static final class ExpireChangedConcurrently extends RuntimeException {
        ExpireChangedConcurrently() {
            super("subscribe expire time changed concurrently", null, false, false);
        }
    }

    static String appendRemark(String origin, String append) {
        return origin == null || origin.isBlank() ? append : origin + " | " + append;
    }

    /**
     * 到期时间顺延: 未到期从原到期时间起算, 已到期/未订阅从当前时间起算, 按日历月累加
     */
    public static long computeExpireAfter(Long currentExpire, int months, long now) {
        long base = currentExpire != null && currentExpire > now ? currentExpire : now;
        return ZonedDateTime
            .ofInstant(Instant.ofEpochMilli(base), ZoneId.systemDefault())
            .plusMonths(months)
            .toInstant()
            .toEpochMilli();
    }

    static long computeAmount(Long monthlyPrice, int months) {
        return (monthlyPrice == null ? 0 : monthlyPrice) * months;
    }

    /**
     * 订单类型: 同套餐=续费, 无套餐=首次开通, 换套餐=变更(余期直接顺延, 不做折算)
     */
    static String resolveOrderType(String currentPlanId, String targetPlanId) {
        if (currentPlanId == null) {
            return ORDER_TYPE_SUBSCRIBE;
        }
        return Objects.equals(currentPlanId, targetPlanId) ? ORDER_TYPE_RENEW : ORDER_TYPE_CHANGE;
    }
}
