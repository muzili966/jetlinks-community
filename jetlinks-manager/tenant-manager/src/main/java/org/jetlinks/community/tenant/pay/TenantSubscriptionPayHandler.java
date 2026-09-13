package org.jetlinks.community.tenant.pay;

import lombok.RequiredArgsConstructor;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.exception.NotFoundException;
import org.jetlinks.community.pay.spi.PayBizHandler;
import org.jetlinks.community.pay.spi.PayBizSummary;
import org.jetlinks.community.pay.spi.PayOrderInfo;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.context.TenantContext;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.jetlinks.community.tenant.enums.TenantOrderStatus;
import org.jetlinks.community.tenant.service.TenantOrderService;
import org.jetlinks.community.tenant.service.TenantRenewalService;
import org.jetlinks.community.tenant.service.TenantService;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 租户订阅接入支付底座: 谁能付、到账后顺延订阅、支付单关闭后取消待支付订单、收银台展示什么.
 *
 * @author tenant-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class TenantSubscriptionPayHandler implements PayBizHandler {

    static final String CLOSED_REMARK = "支付单已关闭";

    private final TenantProperties properties;
    private final TenantOrderService orderService;
    private final TenantService tenantService;

    @Override
    public String getBizType() {
        return TenantRenewalService.BIZ_TYPE;
    }

    /**
     * 平台管理员可以代付; 租户用户只能付自己租户的单.
     * 不是自己的单一律按"不存在"处理, 不给人拿支付单号去试探别的租户.
     */
    @Override
    public Mono<Void> assertPayable(PayOrderInfo order, Authentication auth) {
        return canPay(auth, order.getOwnerId(), properties.getPlatformAdminRoleId())
            ? Mono.empty()
            : Mono.error(new NotFoundException("error.pay_order_not_found"));
    }

    static boolean canPay(Authentication auth, String ownerTenantId, String platformAdminRoleId) {
        if (auth == null) {
            return false;
        }
        if (TenantContext.isPlatformAdmin(auth, platformAdminRoleId)) {
            return true;
        }
        return ownerTenantId != null && TenantContext.currentTenant(auth).map(ownerTenantId::equals).orElse(false);
    }

    @Override
    public Mono<Void> onPaid(PayOrderInfo order) {
        long paidAt = order.getPaidAt() == null ? System.currentTimeMillis() : order.getPaidAt();
        return orderService.fulfill(order.getBizId(), order.getChannel(), paidAt);
    }

    @Override
    public Mono<Void> onClosed(PayOrderInfo order) {
        return orderService.cancelPending(order.getBizId(), CLOSED_REMARK);
    }

    @Override
    public Mono<PayBizSummary> describe(PayOrderInfo order) {
        return orderService
            .findById(order.getBizId())
            .flatMap(tenantOrder -> tenantService
                .findById(tenantOrder.getTenantId())
                .map(tenant -> summaryOf(tenantOrder, tenant, System.currentTimeMillis())));
    }

    /**
     * 已到账展示实际到期时间; 未到账按"现在付款"预估, 标签上写明是预估
     */
    static PayBizSummary summaryOf(TenantOrderEntity order, TenantEntity tenant, long now) {
        int months = order.getMonths() == null ? 0 : order.getMonths();
        boolean paid = order.getStatus() == TenantOrderStatus.paid && order.getExpireTimeAfter() != null;
        long after = paid
            ? order.getExpireTimeAfter()
            : TenantOrderService.computeExpireAfter(tenant.getSubscribeExpireTime(), months, now);
        List<PayBizSummary.Field> fields = List.of(
            PayBizSummary.Field.text("租户", tenant.getName()),
            PayBizSummary.Field.text("套餐", order.getPlanName()),
            PayBizSummary.Field.text("时长", months + " 个月"),
            PayBizSummary.Field.datetime("当前到期", tenant.getSubscribeExpireTime()),
            PayBizSummary.Field.datetime(paid ? "续费后到期" : "付款后到期(预估)", after)
        );
        return new PayBizSummary(order.getPlanName() + " 订阅", fields, null);
    }
}
