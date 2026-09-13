package org.jetlinks.community.pay.service;

import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.hswebframework.web.exception.BusinessException;
import org.hswebframework.web.exception.NotFoundException;
import org.hswebframework.web.exception.ValidationException;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.community.pay.PayConstants;
import org.jetlinks.community.pay.PayProperties;
import org.jetlinks.community.pay.core.PayBizRegistry;
import org.jetlinks.community.pay.core.PayChannelRegistry;
import org.jetlinks.community.pay.core.PayOrderStateMachine;
import org.jetlinks.community.pay.core.PaySettleDecision;
import org.jetlinks.community.pay.entity.PayOrderEntity;
import org.jetlinks.community.pay.enums.PayOrderStatus;
import org.jetlinks.community.pay.event.PayEventPublisher;
import org.jetlinks.community.pay.service.request.PayCreateRequest;
import org.jetlinks.community.pay.service.request.PayPrepareCommand;
import org.jetlinks.community.pay.spi.PayAction;
import org.jetlinks.community.pay.spi.PayBizSummary;
import org.jetlinks.community.pay.spi.PayCapability;
import org.jetlinks.community.pay.spi.PayChannelProvider;
import org.jetlinks.community.pay.spi.PayNotification;
import org.jetlinks.community.pay.spi.PayNotifyRequest;
import org.jetlinks.community.pay.spi.PayPrepareContext;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 支付单生命周期: 创建 → 发起支付 → 到账(回调 / 人工确认) → 履约; 或 → 关闭.
 * <p>
 * 并发与幂等靠数据库条件更新保证(where status = pending), 多节点、网关重复通知都安全, 不依赖分布式锁.
 * 到账分两步: 先把交易号回执落库(即使后面履约失败也留有证据, 超时关单会跳过有交易号的单),
 * 再在一个事务里改状态并调用业务履约; 履约失败整体回滚, 支付单保持待支付, 等网关重试.
 *
 * @author pay-manager
 * @since 2.11
 */
@Slf4j
public class PayOrderService extends GenericReactiveCrudService<PayOrderEntity, String> {

    static final int CLOSE_REASON_MAX_LENGTH = 256;
    static final int TRADE_NO_MAX_LENGTH = 128;
    static final String OFFLINE_TRADE_NO_PREFIX = "OFFLINE-";

    private final PayProperties properties;
    private final PayChannelRegistry channels;
    private final PayBizRegistry bizHandlers;
    private final PayEventPublisher events;
    private final TransactionalOperator transactional;

    public PayOrderService(PayProperties properties,
                           PayChannelRegistry channels,
                           PayBizRegistry bizHandlers,
                           PayEventPublisher events,
                           TransactionalOperator transactional) {
        this.properties = properties;
        this.channels = channels;
        this.bizHandlers = bizHandlers;
        this.events = events;
        this.transactional = transactional;
    }

    // ---------- 创建 ----------

    /**
     * 业务模块调用: 金额由业务按价目算好传进来, 核心只校验不定价
     */
    public Mono<PayOrderEntity> create(PayCreateRequest request) {
        return Mono
            .fromCallable(() -> {
                request.validate();
                bizHandlers.required(request.getBizType());
                return buildOrder(request, System.currentTimeMillis(), properties.getOrderTtl());
            })
            .flatMap(order -> insert(order).thenReturn(order));
    }

    static PayOrderEntity buildOrder(PayCreateRequest request, long now, Duration defaultTtl) {
        Duration ttl = request.getTtl() == null ? defaultTtl : request.getTtl();
        PayOrderEntity order = new PayOrderEntity();
        order.setId(IDGenerator.SNOW_FLAKE_STRING.generate());
        order.setBizType(request.getBizType());
        order.setBizId(request.getBizId());
        order.setSubject(request.getSubject());
        order.setAmount(request.getAmount());
        order.setCurrency(PayConstants.DEFAULT_CURRENCY);
        order.setStatus(PayOrderStatus.pending);
        order.setOwnerId(request.getOwnerId());
        order.setCreatorId(request.getCreatorId());
        order.setExpireAt(now + ttl.toMillis());
        order.setNotifyCount(0);
        order.setCreateTime(now);
        return order;
    }

    public Mono<PayOrderEntity> findRequired(String id) {
        return findById(id).switchIfEmpty(Mono.error(() -> new NotFoundException("error.pay_order_not_found")));
    }

    /** 某笔业务当前还能付的支付单, 用于重复发起时复用而不是再开一张 */
    public Mono<PayOrderEntity> findPendingByBiz(String bizType, String bizId) {
        return createQuery()
            .where(PayOrderEntity::getBizType, bizType)
            .and(PayOrderEntity::getBizId, bizId)
            .and(PayOrderEntity::getStatus, PayOrderStatus.pending)
            .gt(PayOrderEntity::getExpireAt, System.currentTimeMillis())
            .fetchOne();
    }

    // ---------- 收银台 ----------

    /**
     * 当前用户能看、能付的支付单; 权限由业务决定(租户只能付自己租户的单)
     */
    public Mono<PayOrderEntity> findPayable(String id, Authentication auth) {
        return findRequired(id)
            .flatMap(order -> bizHandlers
                .required(order.getBizType())
                .assertPayable(order.toInfo(), auth)
                .thenReturn(order));
    }

    public Mono<PayBizSummary> describe(PayOrderEntity order) {
        return bizHandlers
            .find(order.getBizType())
            .map(handler -> handler.describe(order.toInfo()))
            .orElseGet(Mono::empty);
    }

    public Mono<PayAction> prepare(PayPrepareCommand command) {
        return Mono
            .fromCallable(() -> channels.required(command.getChannelId()))
            .flatMap(channel -> findPayable(command.getOrderId(), command.getAuth())
                .flatMap(order -> prepareWith(channel, order, command.getClientIp())));
    }

    private Mono<PayAction> prepareWith(PayChannelProvider channel, PayOrderEntity order, String clientIp) {
        if (!PayOrderStateMachine.canPay(order.getStatus())) {
            return Mono.error(new BusinessException("error.pay_order_not_payable", 400));
        }
        if (order.isExpiredAt(System.currentTimeMillis())) {
            return Mono.error(new BusinessException("error.pay_order_expired", 400));
        }
        PayPrepareContext context = new PayPrepareContext(
            order.toInfo(), clientIp, notifyUrlOf(properties.getNotifyBaseUrl(), channel.getId()));
        return channel
            .prepare(context)
            .flatMap(action -> createUpdate()
                .set(PayOrderEntity::getChannel, channel.getId())
                .where(PayOrderEntity::getId, order.getId())
                .and(PayOrderEntity::getStatus, PayOrderStatus.pending)
                .execute()
                .thenReturn(action));
    }

    static String notifyUrlOf(String baseUrl, String channelId) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return baseUrl.replaceAll("/+$", "") + PayConstants.NOTIFY_PATH + "/" + channelId;
    }

    // ---------- 到账 ----------

    /**
     * 网关回调: 渠道验签 → 统一入账
     */
    public Mono<Void> handleNotify(PayNotifyRequest request) {
        return Mono
            .fromCallable(() -> capableChannel(request.getChannel(), PayCapability.NOTIFY))
            .flatMap(channel -> channel.parseNotify(request))
            .flatMap(notification -> settle(request.getChannel(), notification));
    }

    /**
     * 平台人工确认线下到账, 与回调走同一条入账路径; 凭证号即交易号
     */
    public Mono<Void> confirmOffline(String orderId, String channelId, String voucher) {
        return Mono
            .fromCallable(() -> capableChannel(channelId, PayCapability.OFFLINE_CONFIRM))
            .flatMap(channel -> findRequired(orderId)
                .flatMap(order -> settle(channel.getId(), PayNotification
                    .builder()
                    .payOrderId(order.getId())
                    .channelTradeNo(offlineTradeNo(voucher, order.getId()))
                    .paidAmount(order.getAmount() == null ? 0 : order.getAmount())
                    .paidAt(System.currentTimeMillis())
                    .success(true)
                    .build())));
    }

    static String offlineTradeNo(String voucher, String orderId) {
        String value = voucher == null || voucher.isBlank() ? OFFLINE_TRADE_NO_PREFIX + orderId : voucher.strip();
        return value.length() <= TRADE_NO_MAX_LENGTH ? value : value.substring(0, TRADE_NO_MAX_LENGTH);
    }

    private PayChannelProvider capableChannel(String channelId, PayCapability capability) {
        PayChannelProvider channel = channels.required(channelId);
        if (!channel.supports(capability)) {
            throw new ValidationException.NoStackTrace("error.pay_channel_capability_unsupported", channelId, capability);
        }
        return channel;
    }

    Mono<Void> settle(String channelId, PayNotification notification) {
        return findRequired(notification.getPayOrderId())
            .flatMap(order -> {
                PaySettleDecision decision = PaySettleDecision
                    .decide(order.getStatus(), order.getAmount(), order.getChannelTradeNo(), notification);
                return apply(decision, order, channelId, notification);
            });
    }

    private Mono<Void> apply(PaySettleDecision decision,
                             PayOrderEntity order,
                             String channelId,
                             PayNotification notification) {
        switch (decision) {
            case SETTLE:
                return recordReceipt(order, channelId, notification)
                    .then(markPaid(order, channelId, notification));
            case DUPLICATE:
                return Mono.empty();
            case IGNORE_NOT_SUCCESS:
                log.info("pay notify not success, order={}, channel={}", order.getId(), channelId);
                return Mono.empty();
            case DUPLICATE_OTHER_TRADE:
                log.error("duplicate payment needs refund, order={}, channel={}, settledTradeNo={}, newTradeNo={}",
                          order.getId(), channelId, order.getChannelTradeNo(), notification.getChannelTradeNo());
                return Mono.empty();
            case REJECT_NOT_PAYABLE:
                log.error("payment arrived for non-payable order, order={}, status={}, channel={}, tradeNo={}",
                          order.getId(), order.getStatus(), channelId, notification.getChannelTradeNo());
                return recordReceipt(order, channelId, notification)
                    .then(Mono.error(new BusinessException("error.pay_order_not_payable", 400)));
            default:
                log.error("pay notify amount mismatch, order={}, expected={}, actual={}, channel={}",
                          order.getId(), order.getAmount(), notification.getPaidAmount(), channelId);
                return Mono.error(new BusinessException("error.pay_notify_amount_mismatch", 400));
        }
    }

    /**
     * 回执不进事务: 履约失败回滚后, 交易号仍然留着作为到账证据
     */
    private Mono<Void> recordReceipt(PayOrderEntity order, String channelId, PayNotification notification) {
        // 通知次数只做观测, 并发通知下可能少计, 不参与任何判断
        int count = order.getNotifyCount() == null ? 0 : order.getNotifyCount();
        return createUpdate()
            .set(PayOrderEntity::getChannel, channelId)
            .set(PayOrderEntity::getChannelTradeNo, notification.getChannelTradeNo())
            .set(PayOrderEntity::getNotifyCount, count + 1)
            .set(PayOrderEntity::getLastNotifyAt, System.currentTimeMillis())
            .where(PayOrderEntity::getId, order.getId())
            .execute()
            .then();
    }

    private Mono<Void> markPaid(PayOrderEntity order, String channelId, PayNotification notification) {
        long paidAt = notification.getPaidAt() == null ? System.currentTimeMillis() : notification.getPaidAt();
        Mono<Boolean> changed = createUpdate()
            .set(PayOrderEntity::getStatus, PayOrderStatus.paid)
            .set(PayOrderEntity::getChannel, channelId)
            .set(PayOrderEntity::getChannelTradeNo, notification.getChannelTradeNo())
            .set(PayOrderEntity::getPaidAt, paidAt)
            .where(PayOrderEntity::getId, order.getId())
            .and(PayOrderEntity::getStatus, PayOrderStatus.pending)
            .execute()
            .flatMap(updated -> {
                if (updated == 0) {
                    // 另一个节点或另一条重复通知已经入账
                    return Mono.just(false);
                }
                order.setStatus(PayOrderStatus.paid);
                order.setChannel(channelId);
                order.setChannelTradeNo(notification.getChannelTradeNo());
                order.setPaidAt(paidAt);
                return bizHandlers.required(order.getBizType()).onPaid(order.toInfo()).thenReturn(true);
            });
        return transactional
            .transactional(changed)
            .flatMap(done -> done ? publishQuietly(order) : Mono.empty());
    }

    // ---------- 关闭 ----------

    public Mono<Void> close(String orderId, String reason) {
        return findRequired(orderId)
            .flatMap(order -> {
                if (!PayOrderStateMachine.canClose(order.getStatus(), order.getChannelTradeNo())) {
                    return Mono.error(new BusinessException("error.pay_order_not_closable", 400));
                }
                return closeOrder(order, reason);
            })
            .then();
    }

    /**
     * 过期未付的单批量关闭. 单张失败不影响其他单, 失败数单独统计
     */
    public Mono<PayCloseResult> closeExpiredBefore(long now) {
        return createQuery()
            .where(PayOrderEntity::getStatus, PayOrderStatus.pending)
            .lt(PayOrderEntity::getExpireAt, now)
            .isNull(PayOrderEntity::getChannelTradeNo)
            .fetch()
            .concatMap(order -> closeOrder(order, PayConstants.CLOSE_REASON_EXPIRED)
                .map(PayCloseResult::ofClosed)
                .onErrorResume(err -> {
                    log.warn("close expired pay order failed, order={}", order.getId(), err);
                    return Mono.just(PayCloseResult.ofFailed());
                }))
            .reduce(PayCloseResult.empty(), PayCloseResult::plus);
    }

    private Mono<Boolean> closeOrder(PayOrderEntity order, String reason) {
        long now = System.currentTimeMillis();
        String closeReason = truncate(reason, CLOSE_REASON_MAX_LENGTH);
        Mono<Boolean> changed = createUpdate()
            .set(PayOrderEntity::getStatus, PayOrderStatus.closed)
            .set(PayOrderEntity::getClosedAt, now)
            .set(PayOrderEntity::getCloseReason, closeReason)
            .where(PayOrderEntity::getId, order.getId())
            .and(PayOrderEntity::getStatus, PayOrderStatus.pending)
            .isNull(PayOrderEntity::getChannelTradeNo)
            .execute()
            .flatMap(updated -> {
                if (updated == 0) {
                    return Mono.just(false);
                }
                order.setStatus(PayOrderStatus.closed);
                order.setClosedAt(now);
                order.setCloseReason(closeReason);
                return bizHandlers
                    .find(order.getBizType())
                    .map(handler -> handler.onClosed(order.toInfo()))
                    .orElseGet(Mono::empty)
                    .thenReturn(true);
            });
        return transactional
            .transactional(changed)
            .flatMap(done -> done ? afterClosed(order).thenReturn(true) : Mono.just(false));
    }

    /**
     * 撤销网关预下单与广播事件都在事务之后, 失败只告警: 支付单已经关了, 不能因为通知失败再打开
     */
    private Mono<Void> afterClosed(PayOrderEntity order) {
        Mono<Void> gateway = channels
            .find(order.getChannel())
            .map(channel -> channel.close(order.toInfo()))
            .orElseGet(Mono::empty);
        return gateway
            .onErrorResume(err -> {
                log.warn("close gateway order failed, order={}, channel={}", order.getId(), order.getChannel(), err);
                return Mono.empty();
            })
            .then(publishQuietly(order));
    }

    private Mono<Void> publishQuietly(PayOrderEntity order) {
        return events
            .publish(order)
            .onErrorResume(err -> {
                log.warn("publish pay order event failed, order={}, status={}", order.getId(), order.getStatus(), err);
                return Mono.empty();
            });
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
