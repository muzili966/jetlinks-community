package org.jetlinks.community.tenant.config;

import org.jetlinks.community.pay.service.PayOrderService;
import org.jetlinks.community.tenant.pay.TenantSubscriptionPayHandler;
import org.jetlinks.community.tenant.service.TenantRenewalService;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.system.authorization.defaults.service.DefaultDimensionUserService;
import org.jetlinks.community.device.entity.DeviceInstanceEntity;
import org.jetlinks.community.device.entity.DeviceProductEntity;
import org.jetlinks.community.gateway.external.DefaultMessagingManager;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.cache.ProductTenantCache;
import org.jetlinks.community.tenant.dimension.TenantDimensionProvider;
import org.jetlinks.community.tenant.interceptor.TenantEventListener;
import org.jetlinks.community.tenant.messaging.TenantMessagingManager;
import org.jetlinks.community.tenant.messaging.TenantTopicChecker;
import org.jetlinks.community.tenant.metric.TenantThingsDataCustomizer;
import org.jetlinks.community.notify.manager.service.NotificationService;
import org.jetlinks.community.tenant.notice.TenantExpireNotifier;
import org.jetlinks.community.auth.entity.RoleEntity;
import org.jetlinks.community.tenant.quota.TenantQuotaListener;
import org.hswebframework.web.system.authorization.api.entity.UserEntity;
import org.jetlinks.community.auth.entity.MenuEntity;
import org.jetlinks.community.tenant.interceptor.TenantMenuGrantListener;
import org.jetlinks.community.tenant.interceptor.TenantMenuScopeListener;
import org.hswebframework.web.authorization.token.UserTokenManager;
import org.jetlinks.community.tenant.role.TenantGrantGuard;
import org.jetlinks.community.tenant.role.TenantUserDetailQueryCustomizer;
import org.jetlinks.community.tenant.timeseries.TenantTimeSeriesManager;
import org.jetlinks.community.timeseries.TimeSeriesManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.annotation.Nonnull;
import org.jetlinks.community.tenant.role.TenantRoleInitializer;
import org.jetlinks.community.tenant.service.TenantInvoiceService;
import org.jetlinks.community.tenant.service.TenantOrderService;
import org.jetlinks.community.tenant.web.TenantInvoiceController;
import org.jetlinks.community.tenant.service.TenantPlanService;
import org.jetlinks.community.tenant.service.TenantQuotaResolver;
import org.jetlinks.community.tenant.service.TenantService;
import org.jetlinks.community.tenant.web.TenantAuthContextFilter;
import org.hswebframework.web.authorization.ReactiveAuthenticationManager;
import org.jetlinks.community.auth.initialize.MenuAuthenticationInitializeService;
import org.jetlinks.community.gateway.external.MessagingManager;
import org.jetlinks.community.gateway.external.socket.WebSocketMessagingHandler;
import org.jetlinks.community.tenant.context.TenantImpersonationAuthenticator;
import org.jetlinks.community.tenant.messaging.ImpersonatingAuthenticationManager;
import org.jetlinks.community.tenant.messaging.TenantMessagingHandlerMappingPostProcessor;
import org.jetlinks.community.tenant.role.TenantMenuProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.jetlinks.community.tenant.web.TenantController;
import org.jetlinks.community.tenant.web.TenantImpersonationFilter;
import org.jetlinks.community.tenant.service.TenantBillingService;
import org.jetlinks.community.tenant.web.MySubscriptionController;
import org.jetlinks.community.tenant.web.TenantOrderController;
import org.jetlinks.community.tenant.web.TenantPlanController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 多租户模块装配: 所有bean都挂在 {@code tenant.enabled=true} 之下,
 * 关闭时系统与单租户版本完全一致(灰度/回滚开关).
 * <p>
 * Controller 不在这里注册——它们带 {@code @RestController}(即 {@code @Component})
 * 会被组件扫描拾取，再在此处 {@code @Bean} 一次就成了双重装配：
 * {@code tenant.enabled=false} 时本配置类整体失效，但扫描仍会实例化 Controller，
 * 于是找不到 Service 直接启动失败——回滚开关反而把服务打死了（真机踩过）。
 * 现改为在各 Controller 上单独标注 {@code @ConditionalOnProperty}，构造参数靠自动注入。
 *
 * @author tenant-manager
 * @since 2.11
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenantProperties.class)
@ConditionalOnProperty(prefix = "tenant", name = "enabled", havingValue = "true")
public class TenantManagerConfiguration {

    @Bean
    public TenantEntityMappingCustomizer tenantEntityMappingCustomizer() {
        return new TenantEntityMappingCustomizer();
    }

    @Bean
    public TenantEventListener tenantEventListener(TenantProperties properties) {
        return new TenantEventListener(properties);
    }

    @Bean
    public TenantService tenantService(DefaultDimensionUserService dimensionUserService,
                                       ReactiveRepository<UserEntity, String> userRepository) {
        return new TenantService(dimensionUserService, userRepository);
    }

    @Bean
    public TenantDimensionProvider tenantDimensionProvider(TenantService tenantService,
                                                           DefaultDimensionUserService dimensionUserService,
                                                           ApplicationEventPublisher eventPublisher) {
        return new TenantDimensionProvider(tenantService, dimensionUserService, eventPublisher);
    }

    @Bean
    public ProductTenantCache productTenantCache(ReactiveRepository<DeviceProductEntity, String> productRepository) {
        return new ProductTenantCache(productRepository);
    }

    @Bean
    @ConditionalOnProperty(prefix = "tenant", name = "time-series-prefix", havingValue = "true", matchIfMissing = true)
    public TenantThingsDataCustomizer tenantThingsDataCustomizer(ProductTenantCache cache) {
        return new TenantThingsDataCustomizer(cache);
    }

    @Bean
    public TenantTopicChecker tenantTopicChecker(ProductTenantCache cache, TenantProperties properties) {
        return new TenantTopicChecker(cache, properties);
    }

    @Bean
    public TenantMenuScopeListener tenantMenuScopeListener(TenantProperties properties) {
        return new TenantMenuScopeListener(properties);
    }

    @Bean
    public TenantMenuGrantListener tenantMenuGrantListener(TenantProperties properties,
                                                           ObjectProvider<ReactiveRepository<MenuEntity, String>> menuRepository) {
        return new TenantMenuGrantListener(properties, menuRepository);
    }

    /**
     * 用 BeanPostProcessor 而非 @Primary @Bean 包装 {@link TimeSeriesManager}:
     * 具体实现类随存储后端而变(TimescaleDB/ES), 按接口注入 delegate 会与自身循环。
     * static + ObjectProvider 避免过早触发配置绑定。
     */
    @Bean
    public static BeanPostProcessor tenantTimeSeriesManagerWrapper(ObjectProvider<TenantProperties> properties) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(@Nonnull Object bean, @Nonnull String beanName) {
                if (bean instanceof TimeSeriesManager && !(bean instanceof TenantTimeSeriesManager)) {
                    return new TenantTimeSeriesManager((TimeSeriesManager) bean, properties.getObject());
                }
                return bean;
            }
        };
    }

    @Bean
    @Primary
    public TenantMessagingManager tenantMessagingManager(DefaultMessagingManager delegate,
                                                         TenantTopicChecker checker,
                                                         TenantProperties properties) {
        return new TenantMessagingManager(delegate, checker, properties);
    }

    @Bean
    public TenantPlanService tenantPlanService() {
        return new TenantPlanService();
    }

    @Bean
    public TenantQuotaResolver tenantQuotaResolver(TenantService tenantService,
                                                   TenantPlanService planService) {
        return new TenantQuotaResolver(tenantService, planService);
    }

    @Bean
    public TenantQuotaListener tenantQuotaListener(TenantProperties properties,
                                                   TenantQuotaResolver quotaResolver,
                                                   ReactiveRepository<DeviceInstanceEntity, String> deviceRepository,
                                                   ReactiveRepository<DeviceProductEntity, String> productRepository) {
        return new TenantQuotaListener(properties, quotaResolver, deviceRepository, productRepository);
    }

    @Bean
    public TenantImpersonationFilter tenantImpersonationFilter() {
        return new TenantImpersonationFilter();
    }

    /**
     * 必须先于隔离监听器把认证放进 Reactor Context——监听器只读不解析，
     * 否则会在认证装配途中反向触发装配（详见该类注释）。
     */
    @Bean
    public TenantAuthContextFilter tenantAuthContextFilter(UserTokenManager userTokenManager,
                                                           TenantImpersonationAuthenticator impersonationAuthenticator) {
        return new TenantAuthContextFilter(userTokenManager, impersonationAuthenticator);
    }

    @Bean
    public TenantImpersonationAuthenticator tenantImpersonationAuthenticator(TenantProperties properties,
                                                                             TenantService tenantService,
                                                                             ReactiveRepository<RoleEntity, String> roleRepository,
                                                                             MenuAuthenticationInitializeService menuPermissionService) {
        return new TenantImpersonationAuthenticator(properties,
                                                    tenantService.getRepository(),
                                                    roleRepository,
                                                    menuPermissionService);
    }

    /**
     * 代理态下关闭 admin 的全量菜单分支。@Primary 让 DefaultMenuService 注入这一份；
     * 上游经 @EnableConfigurationProperties 注册的那份仍在容器里，只是不再被按类型注入。
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "menu")
    public TenantMenuProperties tenantMenuProperties() {
        return new TenantMenuProperties();
    }

    /**
     * WebSocket 订阅同样按代理租户降权。static + ObjectProvider：避免 BeanPostProcessor 过早拉起业务 bean
     */
    @Bean
    public static TenantMessagingHandlerMappingPostProcessor tenantMessagingHandlerMappingPostProcessor(
        ObjectProvider<MessagingManager> messagingManager,
        ObjectProvider<UserTokenManager> userTokenManager,
        ObjectProvider<ReactiveAuthenticationManager> authenticationManager,
        ObjectProvider<TenantImpersonationAuthenticator> impersonationAuthenticator) {
        return new TenantMessagingHandlerMappingPostProcessor(tenantId -> new WebSocketMessagingHandler(
            messagingManager.getObject(),
            userTokenManager.getObject(),
            new ImpersonatingAuthenticationManager(authenticationManager.getObject(),
                                                   impersonationAuthenticator.getObject(),
                                                   tenantId)));
    }

    @Bean
    public TenantRoleInitializer tenantRoleInitializer(TenantProperties properties,
                                                       ReactiveRepository<RoleEntity, String> roleRepository) {
        return new TenantRoleInitializer(properties, roleRepository);
    }

    @Bean
    public TenantGrantGuard tenantGrantGuard(TenantProperties properties,
                                             ReactiveRepository<RoleEntity, String> roleRepository,
                                             UserTokenManager userTokenManager) {
        return new TenantGrantGuard(properties, roleRepository, userTokenManager);
    }

    @Bean
    public TenantUserDetailQueryCustomizer tenantUserDetailQueryCustomizer(TenantProperties properties) {
        return new TenantUserDetailQueryCustomizer(properties);
    }

    @Bean
    public TenantOrderService tenantOrderService(TenantService tenantService,
                                                 TenantPlanService planService) {
        return new TenantOrderService(tenantService, planService);
    }

    @Bean
    public TenantBillingService tenantBillingService(TenantOrderService orderService,
                                                     TenantInvoiceService invoiceService,
                                                     TenantPlanService planService,
                                                     TenantService tenantService) {
        return new TenantBillingService(orderService, invoiceService, planService, tenantService);
    }

    @Bean
    public TenantInvoiceService tenantInvoiceService(TenantOrderService orderService) {
        return new TenantInvoiceService(orderService);
    }

    /**
     * 续费生成待支付订单与支付单; 支付模块可整体关闭, 按需取用
     */
    @Bean
    public TenantRenewalService tenantRenewalService(TenantService tenantService,
                                                     TenantOrderService orderService,
                                                     ObjectProvider<PayOrderService> payOrderService) {
        return new TenantRenewalService(tenantService, orderService, payOrderService);
    }

    /**
     * 租户订阅作为 tenant-subscription 业务接入支付底座
     */
    @Bean
    public TenantSubscriptionPayHandler tenantSubscriptionPayHandler(TenantProperties properties,
                                                                     TenantOrderService orderService,
                                                                     TenantService tenantService) {
        return new TenantSubscriptionPayHandler(properties, orderService, tenantService);
    }

    @Bean
    public TenantExpireNotifier tenantExpireNotifier(TenantProperties properties,
                                                     TenantService tenantService,
                                                     DefaultDimensionUserService dimensionUserService,
                                                     NotificationService notificationService) {
        return new TenantExpireNotifier(properties, tenantService, dimensionUserService, notificationService);
    }
}
