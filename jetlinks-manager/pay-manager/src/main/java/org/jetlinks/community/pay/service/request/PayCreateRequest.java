package org.jetlinks.community.pay.service.request;

import lombok.Builder;
import lombok.Getter;
import org.hswebframework.web.exception.ValidationException;

import java.time.Duration;

/**
 * 业务模块创建支付单. 只供服务端调用, 不直接暴露成接口: 金额必须由业务按自己的价目计算, 不能由前端传.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@Builder
public class PayCreateRequest {

    static final int SUBJECT_MAX_LENGTH = 256;

    private final String bizType;
    private final String bizId;
    private final String subject;

    /** 金额, 单位: 分 */
    private final long amount;
    private final String ownerId;
    private final String creatorId;

    /** 为空时使用 pay.order-ttl */
    private final Duration ttl;

    public void validate() {
        if (isBlank(bizType) || isBlank(bizId)) {
            throw new ValidationException.NoStackTrace("error.pay_order_biz_required");
        }
        if (isBlank(subject) || subject.length() > SUBJECT_MAX_LENGTH) {
            throw new ValidationException.NoStackTrace("error.pay_order_subject_invalid");
        }
        if (amount <= 0) {
            throw new ValidationException.NoStackTrace("error.pay_order_amount_invalid");
        }
        if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
            throw new ValidationException.NoStackTrace("error.pay_order_ttl_invalid");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
