package org.jetlinks.community.pay.core;

import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.pay.PayProperties;
import org.jetlinks.community.pay.spi.PayChannelProvider;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 支付渠道注册表. 收集容器里全部 {@link PayChannelProvider}, 按配置剔除停用渠道.
 * 首次使用时才解析, 业务模块里的渠道 Bean 不必早于支付核心初始化.
 *
 * @author pay-manager
 * @since 2.11
 */
public class PayChannelRegistry {

    private final ObjectProvider<PayChannelProvider> providers;
    private final PayProperties properties;
    private volatile Map<String, PayChannelProvider> channels;

    public PayChannelRegistry(ObjectProvider<PayChannelProvider> providers, PayProperties properties) {
        this.providers = providers;
        this.properties = properties;
    }

    public Optional<PayChannelProvider> find(String id) {
        return Optional.ofNullable(id == null ? null : channels().get(id));
    }

    public PayChannelProvider required(String id) {
        return find(id).orElseThrow(() -> new ValidationException.NoStackTrace("error.pay_channel_unavailable", id));
    }

    public List<PayChannelProvider> all() {
        return List.copyOf(channels().values());
    }

    private Map<String, PayChannelProvider> channels() {
        Map<String, PayChannelProvider> resolved = channels;
        if (resolved == null) {
            synchronized (this) {
                if (channels == null) {
                    channels = index(providers.orderedStream().toList(), properties);
                }
                resolved = channels;
            }
        }
        return resolved;
    }

    /**
     * 重复 ID 直接启动失败: 两个实现抢同一个回调地址, 静默覆盖会让钱进了却记不上账
     */
    static Map<String, PayChannelProvider> index(List<PayChannelProvider> list, PayProperties properties) {
        Map<String, PayChannelProvider> result = new LinkedHashMap<>();
        for (PayChannelProvider provider : list) {
            if (!properties.isChannelEnabled(provider.getId())) {
                continue;
            }
            PayChannelProvider exists = result.putIfAbsent(provider.getId(), provider);
            if (exists != null) {
                throw new IllegalStateException(String.format(
                    "duplicate pay channel id [%s]: %s and %s",
                    provider.getId(), exists.getClass().getName(), provider.getClass().getName()));
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
