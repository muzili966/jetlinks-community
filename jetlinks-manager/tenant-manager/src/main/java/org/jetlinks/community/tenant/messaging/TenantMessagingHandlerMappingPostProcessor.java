package org.jetlinks.community.tenant.messaging;

import lombok.RequiredArgsConstructor;
import org.jetlinks.community.gateway.external.socket.WebSocketMessagingHandler;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 给平台的 {@code /messaging/**} WebSocket 处理器套上代理态身份（见 {@link TenantImpersonationWebSocketHandler}），
 * 不改 gateway-component。
 * <p>
 * 必须整体替换 mapping 而不能改它的 urlMap：handler 在 {@code setApplicationContext} 阶段就已注册，
 * 那一步由容器内置的 Aware 处理器执行，早于任何自定义 BeanPostProcessor，事后改 urlMap 不生效。
 *
 * @author tenant-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class TenantMessagingHandlerMappingPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    static final String MESSAGING_PATH = "/messaging/**";

    private final Function<String, WebSocketHandler> impersonatedHandlerFactory;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(@Nonnull Object bean, @Nonnull String beanName) {
        if (!(bean instanceof SimpleUrlHandlerMapping mapping)
            || !(mapping.getUrlMap().get(MESSAGING_PATH) instanceof WebSocketMessagingHandler handler)) {
            return bean;
        }
        Map<String, Object> urlMap = new LinkedHashMap<>(mapping.getUrlMap());
        urlMap.put(MESSAGING_PATH, new TenantImpersonationWebSocketHandler(handler, impersonatedHandlerFactory));
        SimpleUrlHandlerMapping replaced = new SimpleUrlHandlerMapping(urlMap, mapping.getOrder());
        // 新实例没有经过容器初始化，手动触发 handler 注册
        replaced.setApplicationContext(applicationContext);
        return replaced;
    }
}
