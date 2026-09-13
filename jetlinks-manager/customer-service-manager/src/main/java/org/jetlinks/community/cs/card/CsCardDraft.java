package org.jetlinks.community.cs.card;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 生成好的卡片, 以及它引用的业务对象(可选). 有引用的卡片会随对象状态变化实时更新.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class CsCardDraft {

    private final CsCard card;

    /** 引用对象类型, 如 pay-order; 纯展示卡片为空 */
    private final String refType;

    private final String refId;

    /** 发送时的状态 */
    private final String refStatus;

    public static CsCardDraft of(CsCard card) {
        return new CsCardDraft(card, null, null, null);
    }
}
