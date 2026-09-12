package org.jetlinks.community.cs.config;

import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.system.authorization.api.entity.UserEntity;
import org.hswebframework.web.system.authorization.defaults.service.DefaultDimensionUserService;
import org.jetlinks.community.auth.entity.RoleEntity;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.entity.CsLeadFollowEntity;
import org.jetlinks.community.cs.role.CsRoleInitializer;
import org.jetlinks.community.cs.service.CsAgentNotifier;
import org.jetlinks.community.cs.service.CsInboxCleaner;
import org.jetlinks.community.cs.service.CsInboxRateLimiter;
import org.jetlinks.community.cs.service.CsInboxService;
import org.jetlinks.community.cs.service.CsLeadService;
import org.jetlinks.community.notify.NotifierManager;
import org.jetlinks.community.notify.manager.service.NotificationService;
import org.jetlinks.community.tenant.service.TenantService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * 客服模块装配. 默认启用, {@code customer-service.enabled=false} 整体关闭(表保留).
 * <p>
 * Controller 不在此注册: 它们带 {@code @RestController} 会被组件扫描拾取, 各自标注了同样的条件注解,
 * 避免关闭开关时扫描到 Controller 却找不到 Service 而启动失败(租户模块踩过).
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CsProperties.class)
@ConditionalOnProperty(prefix = "customer-service", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CsManagerConfiguration {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);

    @Bean
    public CsLeadService csLeadService(CsProperties properties,
                                       ReactiveRepository<CsLeadFollowEntity, String> followRepository,
                                       ReactiveRepository<UserEntity, String> userRepository,
                                       ObjectProvider<TenantService> tenantService) {
        return new CsLeadService(properties, followRepository, userRepository, tenantService);
    }

    @Bean
    public CsAgentNotifier csAgentNotifier(CsProperties properties,
                                           DefaultDimensionUserService dimensionUserService,
                                           NotificationService notificationService,
                                           ObjectProvider<NotifierManager> notifierManager) {
        return new CsAgentNotifier(properties, dimensionUserService, notificationService, notifierManager);
    }

    @Bean
    public CsInboxService csInboxService(CsProperties properties,
                                         CsLeadService leadService,
                                         CsAgentNotifier notifier) {
        return new CsInboxService(properties, leadService, notifier);
    }

    @Bean
    public CsInboxRateLimiter csInboxRateLimiter(CsProperties properties) {
        return new CsInboxRateLimiter(properties.getInbox().getRateLimitPerHour(), RATE_LIMIT_WINDOW, Clock.systemUTC());
    }

    @Bean
    public CsInboxCleaner csInboxCleaner(CsProperties properties, CsInboxService inboxService) {
        return new CsInboxCleaner(properties, inboxService);
    }

    @Bean
    public CsRoleInitializer csRoleInitializer(CsProperties properties,
                                               ReactiveRepository<RoleEntity, String> roleRepository) {
        return new CsRoleInitializer(properties, roleRepository);
    }
}
