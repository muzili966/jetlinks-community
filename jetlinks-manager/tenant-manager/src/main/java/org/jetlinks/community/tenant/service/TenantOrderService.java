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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

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

    static String appendRemark(String origin, String append) {
        return origin == null || origin.isBlank() ? append : origin + " | " + append;
    }

    /**
     * 到期时间顺延: 未到期从原到期时间起算, 已到期/未订阅从当前时间起算, 按日历月累加
     */
    static long computeExpireAfter(Long currentExpire, int months, long now) {
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
