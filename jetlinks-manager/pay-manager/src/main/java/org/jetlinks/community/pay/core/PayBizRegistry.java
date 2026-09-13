package org.jetlinks.community.pay.core;

import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.pay.spi.PayBizHandler;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 业务处理器注册表. 与渠道注册表同一套规则: 首次使用时解析, 重复业务类型启动失败.
 *
 * @author pay-manager
 * @since 2.11
 */
public class PayBizRegistry {

    private final ObjectProvider<PayBizHandler> handlers;
    private volatile Map<String, PayBizHandler> index;

    public PayBizRegistry(ObjectProvider<PayBizHandler> handlers) {
        this.handlers = handlers;
    }

    public Optional<PayBizHandler> find(String bizType) {
        return Optional.ofNullable(bizType == null ? null : index().get(bizType));
    }

    /**
     * 业务模块被关闭后, 历史支付单的业务类型会找不到处理器; 这里明确报错, 不能当作无事发生
     */
    public PayBizHandler required(String bizType) {
        return find(bizType).orElseThrow(() -> new ValidationException.NoStackTrace("error.pay_biz_unsupported", bizType));
    }

    private Map<String, PayBizHandler> index() {
        Map<String, PayBizHandler> resolved = index;
        if (resolved == null) {
            synchronized (this) {
                if (index == null) {
                    index = build(handlers.orderedStream().toList());
                }
                resolved = index;
            }
        }
        return resolved;
    }

    static Map<String, PayBizHandler> build(List<PayBizHandler> list) {
        Map<String, PayBizHandler> result = new LinkedHashMap<>();
        for (PayBizHandler handler : list) {
            PayBizHandler exists = result.putIfAbsent(handler.getBizType(), handler);
            if (exists != null) {
                throw new IllegalStateException(String.format(
                    "duplicate pay biz type [%s]: %s and %s",
                    handler.getBizType(), exists.getClass().getName(), handler.getClass().getName()));
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
