package org.jetlinks.community.pay.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 平台确认线下到账.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@Setter
public class PayConfirmOfflineRequest {

    @Schema(description = "确认所用渠道, 需具备人工确认能力", defaultValue = "offline")
    private String channel = "offline";

    @Size(max = 128)
    @Schema(description = "银行流水号等到账凭证, 作为交易号记录; 留空自动生成")
    private String voucher;
}
