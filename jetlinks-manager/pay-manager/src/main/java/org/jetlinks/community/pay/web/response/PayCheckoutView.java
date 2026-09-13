package org.jetlinks.community.pay.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.community.pay.core.PayAmounts;
import org.jetlinks.community.pay.entity.PayOrderEntity;
import org.jetlinks.community.pay.enums.PayOrderStatus;
import org.jetlinks.community.pay.spi.PayBizSummary;

import java.util.List;

/**
 * 收银台视图: 付款人需要的全部信息, 不暴露回执、通知次数这类内部字段.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class PayCheckoutView {

    @Schema(description = "支付单号")
    private final String id;

    @Schema(description = "商品描述")
    private final String subject;

    @Schema(description = "金额(分)")
    private final long amount;

    @Schema(description = "金额展示文本, 如 ¥1,800.00")
    private final String amountText;

    @Schema(description = "状态")
    private final PayOrderStatus status;

    @Schema(description = "已选渠道")
    private final String channel;

    @Schema(description = "过期时间")
    private final Long expireAt;

    @Schema(description = "到账时间")
    private final Long paidAt;

    @Schema(description = "业务明细")
    private final PayBizSummary biz;

    @Schema(description = "可选支付方式")
    private final List<PayChannelView> channels;

    public static PayCheckoutView of(PayOrderEntity order, PayBizSummary biz, List<PayChannelView> channels) {
        long amount = order.getAmount() == null ? 0 : order.getAmount();
        return new PayCheckoutView(order.getId(), order.getSubject(), amount, PayAmounts.format(amount),
                                   order.getStatus(), order.getChannel(), order.getExpireAt(), order.getPaidAt(),
                                   biz, channels);
    }
}
