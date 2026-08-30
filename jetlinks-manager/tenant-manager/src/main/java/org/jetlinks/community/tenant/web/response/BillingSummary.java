package org.jetlinks.community.tenant.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 计费概览：订单页顶部的经营指标，避免纯列表看不出状况。
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BillingSummary {

    @Schema(description = "累计已支付金额(元)")
    private long totalPaid;

    @Schema(description = "本月已支付金额(元)")
    private long monthPaid;

    @Schema(description = "累计订单数")
    private long orderCount;

    @Schema(description = "本月订单数")
    private long monthOrderCount;

    @Schema(description = "待开票金额(已支付且未申请开票)")
    private long pendingInvoiceAmount;

    @Schema(description = "待开票订单数")
    private long pendingInvoiceCount;

    @Schema(description = "待处理发票申请数")
    private long pendingInvoiceApply;

    @Schema(description = "按套餐维度的收入分布")
    private List<PlanRevenue> planRevenues;

    @Schema(description = "近 12 个月的收入趋势")
    private List<MonthRevenue> monthlyTrend;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanRevenue {
        @Schema(description = "套餐ID")
        private String planId;
        @Schema(description = "套餐名称")
        private String planName;
        @Schema(description = "订单数")
        private long orderCount;
        @Schema(description = "金额合计(元)")
        private long amount;
        @Schema(description = "当前订阅该套餐的租户数")
        private long tenantCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthRevenue {
        @Schema(description = "月份, 形如 2026-08")
        private String month;
        @Schema(description = "金额合计(元)")
        private long amount;
        @Schema(description = "订单数")
        private long orderCount;
    }
}
