package org.jetlinks.community.cs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.DefaultDimensionType;
import org.hswebframework.web.id.IDGenerator;
import org.hswebframework.web.system.authorization.api.entity.DimensionUserEntity;
import org.hswebframework.web.system.authorization.defaults.service.DefaultDimensionUserService;
import org.jetlinks.community.cs.CsConstants;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.entity.CsInboxMessageEntity;
import org.jetlinks.community.notify.DefaultNotifyType;
import org.jetlinks.community.notify.NotifierManager;
import org.jetlinks.community.notify.manager.entity.Notification;
import org.jetlinks.community.notify.manager.service.NotificationService;
import org.jetlinks.core.Values;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 新留言通知: 站内通知发给全部客服坐席与主管; 可选再推一条到外部渠道(钉钉群等).
 * <p>
 * 通知是留言提交的旁路, 外部渠道失败只记日志, 不能让访客的留言提交失败.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Slf4j
@RequiredArgsConstructor
public class CsAgentNotifier {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int PREVIEW_LENGTH = 60;

    private final CsProperties properties;
    private final DefaultDimensionUserService dimensionUserService;
    private final NotificationService notificationService;
    private final ObjectProvider<NotifierManager> notifierManager;

    public Mono<Void> notifyNewMessage(CsInboxMessageEntity message) {
        return notifyInApp(message).then(notifyExternal(message));
    }

    private Mono<Void> notifyInApp(CsInboxMessageEntity message) {
        if (!properties.getNotify().isInApp()) {
            return Mono.empty();
        }
        long now = System.currentTimeMillis();
        return agentUserIds()
            .concatMap(userId -> notificationService
                .subscribeNotifications(buildNotification(message, userId, now)))
            .then();
    }

    private Flux<String> agentUserIds() {
        return dimensionUserService
            .createQuery()
            .where(DimensionUserEntity::getDimensionTypeId, DefaultDimensionType.role.getId())
            .in(DimensionUserEntity::getDimensionId,
                Arrays.asList(properties.getAgentRoleId(), properties.getSupervisorRoleId()))
            .fetch()
            .map(DimensionUserEntity::getUserId)
            .distinct();
    }

    private Mono<Void> notifyExternal(CsInboxMessageEntity message) {
        CsProperties.Notify notify = properties.getNotify();
        if (!notify.isExternalConfigured()) {
            return Mono.empty();
        }
        NotifierManager manager = notifierManager.getIfAvailable();
        if (manager == null) {
            log.warn("customer-service external notify configured but NotifierManager is unavailable");
            return Mono.empty();
        }
        return Mono
            .defer(() -> manager
                .getNotifier(DefaultNotifyType.valueOf(notify.getType()), notify.getNotifierId())
                .flatMap(notifier -> notifier.send(notify.getTemplateId(), Values.of(templateContext(message)))))
            // 外部渠道属于旁路, 失败不影响留言落库与站内通知
            .onErrorResume(err -> {
                log.warn("send customer-service external notify failed, inbox={}", message.getId(), err);
                return Mono.empty();
            });
    }

    static Notification buildNotification(CsInboxMessageEntity message, String userId, long now) {
        Notification notification = new Notification();
        notification.setId(IDGenerator.SNOW_FLAKE_STRING.generate());
        notification.setSubscribeId(CsConstants.NOTIFY_TOPIC_PROVIDER + ":" + message.getId());
        notification.setSubscriberType("user");
        notification.setSubscriber(userId);
        notification.setTopicProvider(CsConstants.NOTIFY_TOPIC_PROVIDER);
        notification.setTopicName(CsConstants.NOTIFY_TOPIC_NAME);
        notification.setMessage(summaryLine(message));
        notification.setDataId(message.getLeadId());
        notification.setNotifyTime(now);
        return notification;
    }

    static String summaryLine(CsInboxMessageEntity message) {
        String company = message.getCompany() == null || message.getCompany().isBlank()
            ? ""
            : "(" + message.getCompany() + ")";
        return String.format("%s%s 留言: %s", message.getName(), company, preview(message.getContent()));
    }

    static Map<String, Object> templateContext(CsInboxMessageEntity message) {
        Map<String, Object> context = new HashMap<>();
        context.put("name", message.getName());
        context.put("contact", contactOf(message));
        context.put("company", message.getCompany() == null ? "" : message.getCompany());
        context.put("content", message.getContent());
        context.put("sourcePage", message.getSourcePage() == null ? "" : message.getSourcePage());
        long time = message.getCreateTime() == null ? System.currentTimeMillis() : message.getCreateTime();
        context.put("time", TIME_FORMAT.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())));
        return context;
    }

    private static String contactOf(CsInboxMessageEntity message) {
        if (message.getPhone() != null && !message.getPhone().isBlank()) {
            return message.getPhone();
        }
        return message.getWechat() == null ? "" : message.getWechat();
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        String oneLine = content.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= PREVIEW_LENGTH ? oneLine : oneLine.substring(0, PREVIEW_LENGTH) + "…";
    }
}
