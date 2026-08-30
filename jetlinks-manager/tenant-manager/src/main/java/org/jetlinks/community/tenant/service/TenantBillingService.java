package org.jetlinks.community.tenant.service;

import lombok.AllArgsConstructor;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.entity.TenantInvoiceEntity;
import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.jetlinks.community.tenant.entity.TenantPlanEntity;
import org.jetlinks.community.tenant.enums.TenantInvoiceStatus;
import org.jetlinks.community.tenant.enums.TenantOrderStatus;
import org.jetlinks.community.tenant.web.response.BillingSummary;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计费统计：把订单/发票/套餐聚合成经营指标。
 * <p>
 * 订单量级不大（每租户每年个位数），直接在内存聚合即可，
 * 避免为几个数字引入额外的统计表与同步逻辑。
 *
 * @author tenant-manager
 * @since 2.11
 */
@AllArgsConstructor
public class TenantBillingService {

    private static final int TREND_MONTHS = 12;

    private final TenantOrderService orderService;
    private final TenantInvoiceService invoiceService;
    private final TenantPlanService planService;
    private final TenantService tenantService;

    public Mono<BillingSummary> summary() {
        long monthStart = monthStartMillis(YearMonth.now());

        Mono<List<TenantOrderEntity>> orders = orderService
            .createQuery()
            .where(TenantOrderEntity::getStatus, TenantOrderStatus.paid)
            .fetch()
            .collectList();

        Mono<Long> pendingApply = invoiceService
            .createQuery()
            .where(TenantInvoiceEntity::getStatus, TenantInvoiceStatus.pending)
            .count()
            .map(Integer::longValue);

        Mono<Map<String, String>> planNames = planService
            .createQuery()
            .fetch()
            .collectMap(TenantPlanEntity::getId, TenantPlanEntity::getName);

        Mono<Map<String, Long>> tenantsByPlan = tenantService
            .createQuery()
            .fetch()
            .filter(t -> t.getPlanId() != null)
            .collectList()
            .map(list -> {
                Map<String, Long> counter = new LinkedHashMap<>();
                for (TenantEntity t : list) {
                    counter.merge(t.getPlanId(), 1L, Long::sum);
                }
                return counter;
            });

        return Mono
            .zip(orders, pendingApply, planNames, tenantsByPlan)
            .map(tp -> build(tp.getT1(), tp.getT2(), tp.getT3(), tp.getT4(), monthStart));
    }

    private BillingSummary build(List<TenantOrderEntity> orders,
                                 long pendingApply,
                                 Map<String, String> planNames,
                                 Map<String, Long> tenantsByPlan,
                                 long monthStart) {
        BillingSummary s = new BillingSummary();
        s.setOrderCount(orders.size());
        s.setPendingInvoiceApply(pendingApply);

        long total = 0, month = 0, monthCount = 0, pendingAmount = 0, pendingCount = 0;
        Map<String, long[]> byPlan = new LinkedHashMap<>();       // planId -> [count, amount]
        Map<String, long[]> byMonth = new LinkedHashMap<>();      // yyyy-MM -> [amount, count]

        for (TenantOrderEntity o : orders) {
            long amount = o.getTotalAmount() == null ? 0 : o.getTotalAmount();
            long created = o.getCreateTime() == null ? 0 : o.getCreateTime();
            total += amount;
            if (created >= monthStart) {
                month += amount;
                monthCount++;
            }
            // 已支付但未关联发票 = 待开票
            if (o.getInvoiceId() == null) {
                pendingAmount += amount;
                pendingCount++;
            }
            long[] plan = byPlan.computeIfAbsent(o.getPlanId(), k -> new long[2]);
            plan[0]++;
            plan[1] += amount;

            String ym = YearMonth.from(Instant.ofEpochMilli(created).atZone(ZoneId.systemDefault())).toString();
            long[] m = byMonth.computeIfAbsent(ym, k -> new long[2]);
            m[0] += amount;
            m[1]++;
        }

        s.setTotalPaid(total);
        s.setMonthPaid(month);
        s.setMonthOrderCount(monthCount);
        s.setPendingInvoiceAmount(pendingAmount);
        s.setPendingInvoiceCount(pendingCount);

        List<BillingSummary.PlanRevenue> planRevenues = new ArrayList<>();
        byPlan.forEach((planId, v) -> planRevenues.add(new BillingSummary.PlanRevenue(
            planId,
            planNames.getOrDefault(planId, planId),
            v[0], v[1],
            tenantsByPlan.getOrDefault(planId, 0L))));
        s.setPlanRevenues(planRevenues);

        // 补齐近 12 个月，缺口填 0，否则前端画趋势图会断裂
        List<BillingSummary.MonthRevenue> trend = new ArrayList<>();
        YearMonth cursor = YearMonth.now().minusMonths(TREND_MONTHS - 1L);
        for (int i = 0; i < TREND_MONTHS; i++) {
            String key = cursor.toString();
            long[] v = byMonth.getOrDefault(key, new long[]{0, 0});
            trend.add(new BillingSummary.MonthRevenue(key, v[0], v[1]));
            cursor = cursor.plusMonths(1);
        }
        s.setMonthlyTrend(trend);
        return s;
    }

    static long monthStartMillis(YearMonth ym) {
        return LocalDate.of(ym.getYear(), ym.getMonth(), 1)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
    }

    /**
     * 租户自助：查看自己的订阅与账单概览
     */
    public Mono<BillingSummary> summaryOfTenant(String tenantId) {
        return orderService
            .createQuery()
            .where(TenantOrderEntity::getTenantId, tenantId)
            .and(TenantOrderEntity::getStatus, TenantOrderStatus.paid)
            .fetch()
            .collectList()
            .zipWith(planService.createQuery().fetch()
                                .collectMap(TenantPlanEntity::getId, TenantPlanEntity::getName))
            .map(tp -> build(tp.getT1(), 0L, tp.getT2(),
                             Map.of(), monthStartMillis(YearMonth.now())));
    }

    QueryParamEntity noPaging() {
        QueryParamEntity q = new QueryParamEntity();
        q.setPaging(false);
        return q;
    }
}
