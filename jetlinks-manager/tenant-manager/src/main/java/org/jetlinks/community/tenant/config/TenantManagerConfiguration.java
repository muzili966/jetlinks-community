package org.jetlinks.community.tenant.config;

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
import org.jetlinks.community.tenant.role.TenantGrantGuard;
import org.jetlinks.community.tenant.role.TenantRoleInitializer;
import org.jetlinks.community.tenant.service.TenantInvoiceService;
import org.jetlinks.community.tenant.service.TenantOrderService;
import org.jetlinks.community.tenant.web.TenantInvoiceController;
import org.jetlinks.community.tenant.service.TenantPlanService;
import org.jetlinks.community.tenant.service.TenantQuotaResolver;
import org.jetlinks.community.tenant.service.TenantService;
import org.jetlinks.community.tenant.web.TenantController;
import org.jetlinks.community.tenant.web.TenantImpersonationFilter;
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
    public TenantService tenantService(DefaultDimensionUserService dimensionUserService) {
        return new TenantService(dimensionUserService);
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

    @Bean
    public TenantRoleInitializer tenantRoleInitializer(TenantProperties properties,
                                                       ReactiveRepository<RoleEntity, String> roleRepository) {
        return new TenantRoleInitializer(properties, roleRepository);
    }

    @Bean
    public TenantGrantGuard tenantGrantGuard(TenantProperties properties) {
        return new TenantGrantGuard(properties);
    }

    @Bean
    public TenantController tenantController(TenantService tenantService) {
        return new TenantController(tenantService);
    }

    @Bean
    public TenantPlanController tenantPlanController(TenantPlanService planService) {
        return new TenantPlanController(planService);
    }

    @Bean
    public TenantOrderService tenantOrderService(TenantService tenantService,
                                                 TenantPlanService planService) {
        return new TenantOrderService(tenantService, planService);
    }

    @Bean
    public TenantOrderController tenantOrderController(TenantOrderService orderService) {
        return new TenantOrderController(orderService);
    }

    @Bean
    public TenantInvoiceService tenantInvoiceService(TenantOrderService orderService) {
        return new TenantInvoiceService(orderService);
    }

    @Bean
    public TenantInvoiceController tenantInvoiceController(TenantInvoiceService invoiceService) {
        return new TenantInvoiceController(invoiceService);
    }

    @Bean
    public TenantExpireNotifier tenantExpireNotifier(TenantProperties properties,
                                                     TenantService tenantService,
                                                     DefaultDimensionUserService dimensionUserService,
                                                     NotificationService notificationService) {
        return new TenantExpireNotifier(properties, tenantService, dimensionUserService, notificationService);
    }
}
