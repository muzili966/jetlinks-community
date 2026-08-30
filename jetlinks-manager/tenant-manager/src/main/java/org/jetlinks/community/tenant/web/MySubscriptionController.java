package org.jetlinks.community.tenant.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.hswebframework.web.api.crud.entity.PagerResult;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.exception.NotFoundException;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantPlanConstants;
import org.jetlinks.community.tenant.context.TenantContext;
import org.jetlinks.community.tenant.entity.TenantInvoiceEntity;
import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.jetlinks.community.tenant.entity.TenantPlanEntity;
import org.jetlinks.community.tenant.service.*;
import org.jetlinks.community.tenant.service.request.TenantInvoiceApplyRequest;
import org.jetlinks.community.tenant.web.response.BillingSummary;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * 租户自助：租户查看自己的订阅、用量、账单，并申请开票。
 * <p>
 * 独立于 {@code /tenant} 路径之下，因为那里被 {@code TenantGrantGuard}
 * 整体拦截为平台专属。本控制器只暴露「自己的」数据：
 * <strong>租户ID 一律取自登录态，不接受客户端传入</strong>，
 * 避免出现 {@code ?tenantId=别人} 这种越权入口。
 *
 * @author tenant-manager
 * @since 2.11
 */
@RestController
@RequestMapping("/my/subscription")
@Authorize
@AllArgsConstructor
@Tag(name = "我的订阅(租户自助)")
public class MySubscriptionController {

    private final TenantService tenantService;
    private final TenantPlanService planService;
    private final TenantOrderService orderService;
    private final TenantInvoiceService invoiceService;
    private final TenantBillingService billingService;
    private final TenantQuotaResolver quotaResolver;

    /** 当前登录用户所属租户；平台管理员无租户时返回 404 而非报错 */
    private Mono<String> currentTenantId() {
        return Authentication
            .currentReactive()
            .flatMap(auth -> Mono.justOrEmpty(TenantContext.currentTenant(auth)))
            .switchIfEmpty(Mono.error(() -> new NotFoundException("当前账号不属于任何租户")));
    }

    @GetMapping
    @Authorize(merge = false)
    @Operation(summary = "我的订阅详情(含套餐与配额)")
    public Mono<Map<String, Object>> mySubscription() {
        return currentTenantId()
            .flatMap(tenantId -> tenantService
                .findById(tenantId)
                .<Map<String, Object>>flatMap(tenant -> Mono.zip(
                    // 到期后按免费版展示，与 TenantQuotaResolver 的降级口径保持一致
                    planService
                        .findById(tenant.isSubscribeExpired() || tenant.getPlanId() == null
                                      ? TenantPlanConstants.PLAN_FREE
                                      : tenant.getPlanId())
                        .defaultIfEmpty(new TenantPlanEntity()),
                    quotaResolver.resolve(tenantId, TenantConstants.QUOTA_MAX_DEVICE)
                ).map(tp -> Map.<String, Object>of(
                    "tenant", tenant,
                    "plan", tp.getT1(),
                    "maxDeviceCount", tp.getT2().<Object>map(v -> v).orElse("不限"),
                    "expired", tenant.isSubscribeExpired()
                ))));
    }

    @GetMapping("/billing")
    @Authorize(merge = false)
    @Operation(summary = "我的账单概览")
    public Mono<BillingSummary> myBilling() {
        return currentTenantId().flatMap(billingService::summaryOfTenant);
    }

    @PostMapping("/orders/_query")
    @Authorize(merge = false)
    @Operation(summary = "我的订单流水")
    public Mono<PagerResult<TenantOrderEntity>> myOrders(@RequestBody Mono<QueryParamEntity> query) {
        return Mono
            .zip(currentTenantId(), query)
            .flatMap(tp -> {
                QueryParamEntity q = tp.getT2();
                // 强制限定为自己的租户，忽略客户端可能传入的 tenantId 条件
                q.and(TenantConstants.TENANT_ID_PROPERTY, "eq", tp.getT1());
                return orderService.queryPager(q);
            });
    }

    @PostMapping("/invoices/_query")
    @Authorize(merge = false)
    @Operation(summary = "我的发票申请")
    public Mono<PagerResult<TenantInvoiceEntity>> myInvoices(@RequestBody Mono<QueryParamEntity> query) {
        return Mono
            .zip(currentTenantId(), query)
            .flatMap(tp -> {
                QueryParamEntity q = tp.getT2();
                q.and(TenantConstants.TENANT_ID_PROPERTY, "eq", tp.getT1());
                return invoiceService.queryPager(q);
            });
    }

    @PostMapping("/invoices/_apply")
    @Authorize(merge = false)
    @Operation(summary = "申请开票(仅能针对自己租户的订单)")
    public Mono<TenantInvoiceEntity> applyInvoice(@RequestBody @Valid Mono<TenantInvoiceApplyRequest> request) {
        // 订单归属校验在 TenantInvoiceService.apply 内完成（同租户 + 已支付 + 未开票）
        return request.flatMap(invoiceService::apply);
    }

    @GetMapping("/plans")
    @Authorize(merge = false)
    @Operation(summary = "可选套餐(供租户了解升级选项)")
    public Flux<TenantPlanEntity> availablePlans() {
        return QueryParamEntity
            .newQuery()
            .where(TenantPlanEntity::getState, org.jetlinks.community.tenant.enums.TenantState.enabled)
            .orderByAsc(TenantPlanEntity::getSortIndex)
            .execute(planService::query);
    }
}
