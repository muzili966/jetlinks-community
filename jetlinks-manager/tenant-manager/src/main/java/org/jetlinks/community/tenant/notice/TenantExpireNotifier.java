package org.jetlinks.community.tenant.notice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.id.IDGenerator;
import org.hswebframework.web.system.authorization.api.entity.DimensionUserEntity;
import org.hswebframework.web.system.authorization.defaults.service.DefaultDimensionUserService;
import org.jetlinks.community.notify.manager.entity.Notification;
import org.jetlinks.community.notify.manager.service.NotificationService;
import org.jetlinks.community.tenant.TenantDimensionType;
import org.jetlinks.community.tenant.TenantProperties;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.enums.TenantState;
import org.jetlinks.community.tenant.service.TenantService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 订阅到期提醒: 周期扫描将到期/已到期租户, 给租户全部用户发站内通知(通知中心铃铛).
 * <p>
 * 去重: 以到期时间为锚, 每个订阅周期只提醒一次({@code expireNotifiedAt});
 * 续费后到期时间后移, 提醒窗口自动重置.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
@RequiredArgsConstructor
public class TenantExpireNotifier implements DisposableBean {

    static final String TOPIC_PROVIDER = "tenant-expire";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TenantProperties properties;
    private final TenantService tenantService;
    private final DefaultDimensionUserService dimensionUserService;
    private final NotificationService notificationService;

    private volatile Disposable task;

    @EventListener
    public void start(ApplicationReadyEvent event) {
        Duration interval = properties.getExpireCheckInterval();
        task = Flux
            .interval(Duration.ofMinutes(1), interval)
            .concatMap(ignore -> scan()
                .onErrorResume(err -> {
                    log.error("scan expiring tenants failed", err);
                    return Mono.empty();
                }))
            .subscribe();
        log.info("tenant expire notifier started, interval={}, notifyDays={}",
                 interval, properties.getExpireNotifyDays());
    }

    Mono<Void> scan() {
        long now = System.currentTimeMillis();
        return tenantService
            .createQuery()
            .where(TenantEntity::getState, TenantState.enabled)
            .notNull(TenantEntity::getSubscribeExpireTime)
            .fetch()
            .filter(tenant -> shouldNotify(tenant.getSubscribeExpireTime(),
                                           tenant.getExpireNotifiedAt(),
                                           now,
                                           properties.getExpireNotifyDays()))
            .concatMap(tenant -> notifyTenant(tenant, now))
            .then();
    }

    /**
     * 提醒窗口判定: now 进入 [到期-提前天数, +∞) 且本周期(以到期时间为锚)未提醒过
     */
    static boolean shouldNotify(long expireTime, Long notifiedAt, long now, int notifyDays) {
        long windowStart = expireTime - Duration.ofDays(notifyDays).toMillis();
        if (now < windowStart) {
            return false;
        }
        return notifiedAt == null || notifiedAt < windowStart;
    }

    private Mono<Void> notifyTenant(TenantEntity tenant, long now) {
        return dimensionUserService
            .createQuery()
            .where(DimensionUserEntity::getDimensionTypeId, TenantDimensionType.tenant.getId())
            .and(DimensionUserEntity::getDimensionId, tenant.getId())
            .fetch()
            .map(DimensionUserEntity::getUserId)
            .distinct()
            .concatMap(userId -> notificationService
                .subscribeNotifications(buildNotification(tenant, userId, now)))
            .then(markNotified(tenant.getId(), now))
            .doOnSuccess(ignore -> log.info("tenant [{}] expire notification sent, expireAt={}",
                                            tenant.getId(), tenant.getSubscribeExpireTime()));
    }

    private Notification buildNotification(TenantEntity tenant, String userId, long now) {
        boolean expired = tenant.getSubscribeExpireTime() < now;
        String expireDate = DATE_FORMAT.format(
            Instant.ofEpochMilli(tenant.getSubscribeExpireTime()).atZone(ZoneId.systemDefault()));

        Notification notification = new Notification();
        notification.setId(IDGenerator.SNOW_FLAKE_STRING.generate());
        notification.setSubscribeId(TOPIC_PROVIDER + ":" + tenant.getId());
        notification.setSubscriberType("user");
        notification.setSubscriber(userId);
        notification.setTopicProvider(TOPIC_PROVIDER);
        notification.setTopicName("订阅到期提醒");
        notification.setMessage(expired
            ? String.format("租户[%s]的订阅已于 %s 到期, 当前按免费版配额执行, 请及时续费。", tenant.getName(), expireDate)
            : String.format("租户[%s]的订阅将于 %s 到期, 到期后按免费版配额执行, 请及时续费。", tenant.getName(), expireDate));
        notification.setDataId(tenant.getId());
        notification.setNotifyTime(now);
        return notification;
    }

    private Mono<Void> markNotified(String tenantId, long now) {
        return tenantService
            .createUpdate()
            .set(TenantEntity::getExpireNotifiedAt, now)
            .where(TenantEntity::getId, tenantId)
            .execute()
            .then();
    }

    @Override
    public void destroy() {
        if (task != null && !task.isDisposed()) {
            task.dispose();
        }
    }
}
