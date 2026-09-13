package org.jetlinks.community.tenant.service;

import lombok.RequiredArgsConstructor;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.pay.core.PayAmounts;
import org.jetlinks.community.pay.entity.PayOrderEntity;
import org.jetlinks.community.pay.service.PayOrderService;
import org.jetlinks.community.pay.service.request.PayCreateRequest;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.jetlinks.community.tenant.service.request.TenantRenewalRequest;
import org.jetlinks.community.tenant.service.request.TenantSubscribeRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * 续费走在线支付: 生成待支付订单和对应支付单. 到账后由支付底座回调
 * {@code TenantSubscriptionPayHandler} 履约, 这里不碰订阅到期时间.
 * <p>
 * 支付模块可被整体关闭({@code pay.enabled=false}), 所以通过 ObjectProvider 取用, 关闭时明确报错.
 *
 * @author tenant-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class TenantRenewalService {

    /** 支付单业务类型, 稳定契约 */
    public static final String BIZ_TYPE = "tenant-subscription";

    private final TenantService tenantService;
    private final TenantOrderService orderService;
    private final ObjectProvider<PayOrderService> payOrderService;

    /**
     * 订单与支付单同一事务写入: 不会出现有订单没支付单、或有支付单指向不存在订单的情况
     */
    @Transactional
    public Mono<TenantRenewal> create(TenantRenewalRequest request) {
        PayOrderService pay = payOrderService.getIfAvailable();
        if (pay == null) {
            return Mono.error(new BusinessException("error.tenant_pay_disabled", 400));
        }
        return Mono
            .fromRunnable(request::validate)
            .then(tenantService.findById(request.getTenantId()))
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_not_exist", 404, request.getTenantId())))
            .flatMap(tenant -> {
                String planId = resolvePlanId(request.getPlanId(), tenant.getPlanId());
                if (planId == null) {
                    return Mono.error(new BusinessException("error.tenant_renew_plan_required", 400));
                }
                return orderService
                    .createPending(subscribeRequestOf(request, planId))
                    .flatMap(order -> pay
                        .create(payRequestOf(order, request.getCreatorId()))
                        .map(payOrder -> renewalOf(tenant, order, payOrder)));
            });
    }

    static String resolvePlanId(String requested, String current) {
        return requested == null || requested.isBlank() ? current : requested;
    }

    static TenantSubscribeRequest subscribeRequestOf(TenantRenewalRequest request, String planId) {
        TenantSubscribeRequest subscribe = new TenantSubscribeRequest();
        subscribe.setTenantId(request.getTenantId());
        subscribe.setPlanId(planId);
        subscribe.setMonths(request.getMonths());
        subscribe.setPayChannel(null);
        subscribe.setRemark(request.getRemark());
        return subscribe;
    }

    static PayCreateRequest payRequestOf(TenantOrderEntity order, String creatorId) {
        return PayCreateRequest
            .builder()
            .bizType(BIZ_TYPE)
            .bizId(order.getId())
            .subject(subjectOf(order))
            .amount(PayAmounts.fenOfYuan(order.getTotalAmount() == null ? 0 : order.getTotalAmount()))
            .ownerId(order.getTenantId())
            .creatorId(creatorId)
            .build();
    }

    /**
     * 付款人在收银台和网关账单里看到的描述, 如 "标准版 续费 3 个月"
     */
    static String subjectOf(TenantOrderEntity order) {
        String action;
        if (TenantOrderService.ORDER_TYPE_CHANGE.equals(order.getOrderType())) {
            action = "变更套餐";
        } else if (TenantOrderService.ORDER_TYPE_SUBSCRIBE.equals(order.getOrderType())) {
            action = "开通";
        } else {
            action = "续费";
        }
        return order.getPlanName() + " " + action + " " + order.getMonths() + " 个月";
    }

    static TenantRenewal renewalOf(TenantEntity tenant, TenantOrderEntity order, PayOrderEntity payOrder) {
        int months = order.getMonths() == null ? 0 : order.getMonths();
        return TenantRenewal
            .builder()
            .tenantId(tenant.getId())
            .tenantName(tenant.getName())
            .planName(order.getPlanName())
            .months(months)
            .totalAmount(order.getTotalAmount() == null ? 0 : order.getTotalAmount())
            .currentExpireTime(tenant.getSubscribeExpireTime())
            .expireTimeAfterPreview(TenantOrderService.computeExpireAfter(
                tenant.getSubscribeExpireTime(), months, System.currentTimeMillis()))
            .tenantOrderId(order.getId())
            .payOrderId(payOrder.getId())
            .subject(payOrder.getSubject())
            .payAmount(payOrder.getAmount() == null ? 0 : payOrder.getAmount())
            .payExpireAt(payOrder.getExpireAt())
            .build();
    }
}
