package org.jetlinks.community.cs.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 线索看板汇总.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CsLeadSummary {

    @Schema(description = "新线索数")
    private long pending;

    @Schema(description = "跟进中数")
    private long following;

    @Schema(description = "已转化数")
    private long converted;

    @Schema(description = "无效数")
    private long invalid;

    @Schema(description = "跟进已逾期数(约定时间已过且仍在跟进中)")
    private long overdue;
}
