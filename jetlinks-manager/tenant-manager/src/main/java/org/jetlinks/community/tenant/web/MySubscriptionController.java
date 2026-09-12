package org.jetlinks.community.tenant.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.hswebframework.web.api.crud.entity.PagerResult;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.exception.AccessDenyException;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import io.swagger.v3.oas.annotations.Parameter;
import org.hswebframework.reactor.excel.ReactorExcel;
import org.hswebframework.reactor.excel.WriterOperator;
import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
@ConditionalOnProperty(prefix = "tenant", name = "enabled", havingValue = "true")
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
        // service.apply 内校验「同租户 + 已支付 + 未开票」，但它只保证多笔订单属于同一租户，
        // 至于是不是「本人的」租户，靠行级隔离让 findById 查不到别人的订单来兜。
        // 自助入口不依赖那一层：先把租户ID从登录态取出来显式比对，隔离万一失效也不会越权开票。
        return Mono
            .zip(currentTenantId(), request)
            .flatMap(tp -> assertOwnOrders(tp.getT1(), tp.getT2())
                .then(invoiceService.apply(tp.getT2())));
    }

    private Mono<Void> assertOwnOrders(String tenantId, TenantInvoiceApplyRequest request) {
        return orderService
            .findById(request.getOrderIdList())
            .filter(order -> !tenantId.equals(order.getTenantId()))
            .next()
            .flatMap(foreign -> Mono.<Void>error(new AccessDenyException()))
            .then();
    }

    @GetMapping("/orders/export.{format}")
    @Authorize(merge = false)
    @Operation(summary = "导出我的订单(xlsx/csv)")
    public Mono<Void> exportMyOrders(ServerHttpResponse response,
                                     @PathVariable @Parameter(description = "文件格式: xlsx 或 csv") String format,
                                     @Parameter(hidden = true) QueryParamEntity query) {
        response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=" + URLEncoder.encode("my-orders." + format, StandardCharsets.UTF_8));
        query.setPaging(false);
        // 租户ID 只从登录态取，不接受任何客户端入参，避免改 query 就导出别人的账单
        return currentTenantId()
            .flatMap(tenantId -> {
                query.and(TenantConstants.TENANT_ID_PROPERTY, "eq", tenantId);
                WriterOperator<TenantOrderEntity> writer = ReactorExcel
                    .<TenantOrderEntity>writer(format)
                    .header("id", "订单号")
                    .header("planName", "套餐")
                    .header("months", "月数")
                    .header("totalAmount", "金额(元)")
                    .header("orderType", "类型")
                    .header("status", "状态")
                    .header("payChannel", "支付渠道")
                    .header("invoiceState", "开票状态")
                    .header("expireTimeAfter", "生效后到期")
                    .header("createTime", "下单时间")
                    .header("remark", "备注")
                    .converter(MySubscriptionController::toExportRow);
                // 不用库的 writeBuffer：它按引用发射可复用缓冲区，导出会损坏/错行
                return ExcelExportSupport
                    .writeAll(writer, orderService.query(query))
                    .flatMap(bytes -> response.writeWith(
                        Mono.just(response.bufferFactory().wrap(bytes))));
            });
    }

    /** 不含租户名/租户ID：租户导出自己的账单，这两列是冗余信息 */
    private static Map<String, Object> toExportRow(TenantOrderEntity order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("planName", order.getPlanName());
        row.put("months", order.getMonths());
        row.put("totalAmount", order.getTotalAmount());
        row.put("orderType", ORDER_TYPE_TEXT.getOrDefault(order.getOrderType(), order.getOrderType()));
        row.put("status", order.getStatus() == null ? "" : order.getStatus().getText());
        row.put("payChannel", order.getPayChannel());
        row.put("invoiceState", order.getInvoiceId() == null ? "未开票" : "已开票");
        row.put("expireTimeAfter", formatTime(order.getExpireTimeAfter()));
        row.put("createTime", formatTime(order.getCreateTime()));
        row.put("remark", order.getRemark());
        return row;
    }

    private static final Map<String, String> ORDER_TYPE_TEXT = Map.of(
        "subscribe", "首次开通",
        "renew", "续费",
        "change", "变更套餐");

    private static final DateTimeFormatter EXPORT_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String formatTime(Long time) {
        if (time == null) {
            return "";
        }
        return EXPORT_TIME_FORMAT.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
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
