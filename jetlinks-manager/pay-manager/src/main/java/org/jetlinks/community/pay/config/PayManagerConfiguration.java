package org.jetlinks.community.pay.config;

import org.jetlinks.community.pay.PayProperties;
import org.jetlinks.community.pay.channel.OfflinePayChannel;
import org.jetlinks.community.pay.channel.SandboxPayChannel;
import org.jetlinks.community.pay.core.PayBizRegistry;
import org.jetlinks.community.pay.core.PayChannelRegistry;
import org.jetlinks.community.pay.event.PayEventPublisher;
import org.jetlinks.community.pay.service.PayOrderExpireCloser;
import org.jetlinks.community.pay.service.PayOrderService;
import org.jetlinks.community.pay.spi.PayBizHandler;
import org.jetlinks.community.pay.spi.PayChannelProvider;
import org.jetlinks.core.event.EventBus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.time.Clock;

/**
 * 支付模块装配. 默认启用, {@code pay.enabled=false} 整体关闭(表保留).
 * Controller 各自标注同样的条件注解, 由组件扫描拾取, 这里不重复注册.
 *
 * @author pay-manager
 * @since 2.11
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PayProperties.class)
@ConditionalOnProperty(prefix = "pay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PayManagerConfiguration {

    @Bean
    public PayChannelRegistry payChannelRegistry(ObjectProvider<PayChannelProvider> providers, PayProperties properties) {
        return new PayChannelRegistry(providers, properties);
    }

    @Bean
    public PayBizRegistry payBizRegistry(ObjectProvider<PayBizHandler> handlers) {
        return new PayBizRegistry(handlers);
    }

    @Bean
    public PayEventPublisher payEventPublisher(EventBus eventBus) {
        return new PayEventPublisher(eventBus);
    }

    @Bean
    public PayOrderService payOrderService(PayProperties properties,
                                           PayChannelRegistry channels,
                                           PayBizRegistry bizHandlers,
                                           PayEventPublisher events,
                                           TransactionalOperator transactionalOperator) {
        return new PayOrderService(properties, channels, bizHandlers, events, transactionalOperator);
    }

    @Bean
    public PayOrderExpireCloser payOrderExpireCloser(PayProperties properties, PayOrderService orderService) {
        return new PayOrderExpireCloser(properties, orderService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "pay.channels.offline", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OfflinePayChannel offlinePayChannel(PayProperties properties) {
        return new OfflinePayChannel(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "pay.sandbox", name = "enabled", havingValue = "true")
    public SandboxPayChannel sandboxPayChannel(PayProperties properties) {
        return new SandboxPayChannel(properties, Clock.systemUTC());
    }
}
