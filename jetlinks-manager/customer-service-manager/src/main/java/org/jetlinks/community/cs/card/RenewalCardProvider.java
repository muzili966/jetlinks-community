package org.jetlinks.community.cs.card;

import lombok.RequiredArgsConstructor;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.pay.core.PayAmounts;
import org.jetlinks.community.pay.enums.PayOrderStatus;
import org.jetlinks.community.tenant.service.TenantRenewal;
import org.jetlinks.community.tenant.service.TenantRenewalService;
import org.jetlinks.community.tenant.service.request.TenantRenewalRequest;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 续费卡片: 坐席选套餐和月数, 后端当场生成待支付订单与支付单, 卡片按钮跳转收银台.
 * 只对控制台里租户用户发起的会话开放, 租户ID取自会话记录, 不接受坐席指定.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class RenewalCardProvider implements CsCardProvider {

    public static final String KIND = "renewal";
    public static final String REF_TYPE_PAY_ORDER = "pay-order";
    static final String CHECKOUT_ROUTE = "/pay/checkout/";
    static final int DEFAULT_MONTHS = 12;

    private final ObjectProvider<TenantRenewalService> renewalService;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public String getName() {
        return "续费卡片";
    }

    @Override
    public boolean supports(CsSessionEntity session) {
        return session.getTenantId() != null && renewalService.getIfAvailable() != null;
    }

    @Override
    public Mono<CsCardDraft> build(CsCardContext context) {
        TenantRenewalService service = renewalService.getIfAvailable();
        CsSessionEntity session = context.getSession();
        if (service == null || session.getTenantId() == null) {
            return Mono.error(new BusinessException("error.cs_card_renewal_unavailable", 400));
        }
        TenantRenewalRequest request = new TenantRenewalRequest();
        request.setTenantId(session.getTenantId());
        request.setPlanId(context.text("planId"));
        request.setMonths(context.integer("months", DEFAULT_MONTHS));
        request.setRemark("客服会话 " + session.getId() + " 发起续费");
        request.setCreatorId(context.getOperatorId());
        return service
            .create(request)
            // 客服账号通常不属于任何租户, 带着它的登录态写租户订单会被租户隔离改写成"无租户"而写错归属.
            // 这里的租户ID来自会话记录(租户用户本人登录发起), 且上层已校验当前坐席正在接待这个会话,
            // 所以按系统内部链路执行, 不带坐席登录态.
            .contextWrite(ctx -> ctx.delete(Authentication.class))
            .map(RenewalCardProvider::draftOf);
    }

    static CsCardDraft draftOf(TenantRenewal renewal) {
        CsCard card = new CsCard();
        card.setKind(KIND);
        card.setTitle("续费 " + renewal.getPlanName());
        card.setDescription(renewal.getSubject());
        card.setAmountText(PayAmounts.format(renewal.getPayAmount()));
        card.setFields(List.of(
            new CsCard.Field("租户", renewal.getTenantName(), CsCard.Field.TYPE_TEXT),
            new CsCard.Field("时长", renewal.getMonths() + " 个月", CsCard.Field.TYPE_TEXT),
            new CsCard.Field("当前到期", millis(renewal.getCurrentExpireTime()), CsCard.Field.TYPE_DATETIME),
            new CsCard.Field("付款后到期(预估)", millis(renewal.getExpireTimeAfterPreview()), CsCard.Field.TYPE_DATETIME)
        ));
        card.setAction(new CsCard.Action(CsCard.Action.TYPE_ROUTE, "去支付", CHECKOUT_ROUTE + renewal.getPayOrderId()));
        card.setExpireAt(renewal.getPayExpireAt());
        return new CsCardDraft(card, REF_TYPE_PAY_ORDER, renewal.getPayOrderId(), PayOrderStatus.pending.name());
    }

    private static String millis(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
