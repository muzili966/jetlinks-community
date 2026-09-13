package org.jetlinks.community.cs.service;

import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.ezorm.rdb.mapping.ReactiveUpdate;
import org.hswebframework.ezorm.rdb.operator.dml.query.SortOrder;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.hswebframework.web.exception.BusinessException;
import org.hswebframework.web.exception.NotFoundException;
import org.hswebframework.web.exception.ValidationException;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.chat.CsAgentPicker;
import org.jetlinks.community.cs.chat.CsAttachmentPolicy;
import org.jetlinks.community.cs.chat.CsChatEventPublisher;
import org.jetlinks.community.cs.chat.CsSessionEvent;
import org.jetlinks.community.cs.chat.CsSessionStateMachine;
import org.jetlinks.community.cs.chat.CsSupportIdentity;
import org.jetlinks.community.cs.card.CsCardDraft;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.jetlinks.community.cs.chat.CsSessionView;
import org.jetlinks.community.cs.entity.CsAgentEntity;
import org.jetlinks.community.cs.entity.CsChatMessageEntity;
import org.jetlinks.community.cs.entity.CsLeadEntity;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.cs.enums.CsChatMessageType;
import org.jetlinks.community.cs.enums.CsChatSender;
import org.jetlinks.community.cs.enums.CsSessionState;
import org.jetlinks.community.cs.service.request.CsSessionContactRequest;
import org.jetlinks.community.cs.service.request.CsSessionLeadRequest;
import org.jetlinks.community.cs.service.request.CsSessionOpenRequest;
import org.jetlinks.community.cs.service.request.CsSessionRateRequest;
import org.jetlinks.community.cs.service.request.CsSupportOpenRequest;
import org.jetlinks.community.cs.web.ClientInfo;
import org.jetlinks.community.io.file.FileInfo;
import org.jetlinks.community.io.file.FileManager;
import org.jetlinks.community.io.file.FileOption;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 在线会话: 发起与自动分配、收发消息、接入 / 转接 / 结束、转线索.
 * 每次状态变化都通过 {@link CsChatEventPublisher} 推给访客(SSE)与坐席(WebSocket).
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Slf4j
public class CsSessionService extends GenericReactiveCrudService<CsSessionEntity, String> {

    static final String CLOSED_BY_VISITOR = "visitor";
    static final String CLOSED_BY_AGENT = "agent";
    static final String CLOSED_BY_SYSTEM = "system";
    static final String SYSTEM_SENDER_NAME = "系统";
    static final int LAST_MESSAGE_MAX_LENGTH = 200;
    private static final int TOKEN_BYTES = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CsProperties properties;
    private final CsAgentService agentService;
    private final ReactiveRepository<CsChatMessageEntity, String> messageRepository;
    private final CsLeadService leadService;
    private final CsChatEventPublisher publisher;
    private final FileManager fileManager;
    private final CsAttachmentPolicy attachmentPolicy;

    public CsSessionService(CsProperties properties,
                            CsAgentService agentService,
                            ReactiveRepository<CsChatMessageEntity, String> messageRepository,
                            CsLeadService leadService,
                            CsChatEventPublisher publisher,
                            FileManager fileManager) {
        this.properties = properties;
        this.agentService = agentService;
        this.messageRepository = messageRepository;
        this.leadService = leadService;
        this.publisher = publisher;
        this.fileManager = fileManager;
        this.attachmentPolicy = new CsAttachmentPolicy(properties.getChat());
    }

    // ---------- 访客侧 ----------

    /**
     * 发起会话: 有空闲坐席直接分配为接待中, 否则进入排队. 首条消息随会话一起写入.
     */
    @Transactional
    public Mono<CsSessionView> open(CsSessionOpenRequest request, ClientInfo client) {
        long now = System.currentTimeMillis();
        CsSessionEntity session = buildSession(request, client, now);
        return pickAgent()
            .map(agent -> assignTo(session, agent, now))
            .defaultIfEmpty(session)
            .flatMap(this::insert)
            .then(Mono.defer(() -> appendFirstMessage(session, request, now)))
            .then(Mono.defer(() -> announceOpened(session)))
            .then(agentService.hasOnline())
            .map(online -> CsSessionView.of(session).withToken(session.getVisitorToken()).withAgentsOnline(online));
    }

    private Mono<Void> appendFirstMessage(CsSessionEntity session, CsSessionOpenRequest request, long now) {
        if (!request.hasFirstMessage()) {
            return Mono.empty();
        }
        return appendMessage(session, CsChatSender.visitor, request.getFirstMessage().strip(), now);
    }

    /** 会话刚建好时写入首条消息: 此时还没有订阅者, 不发事件 */
    private Mono<Void> appendMessage(CsSessionEntity session, CsChatSender sender, String content, long now) {
        CsChatMessageEntity message = buildMessage(session, sender, content, now);
        applyMessage(session, message);
        return messageRepository.insert(message).then(persistCounters(session));
    }

    private Mono<Void> announceOpened(CsSessionEntity session) {
        if (session.getState() == CsSessionState.active) {
            return publisher.publish(CsSessionEvent.of(CsSessionEvent.TYPE_ACCEPTED, session), session.getAgentId());
        }
        return publisher.publish(CsSessionEvent.of(CsSessionEvent.TYPE_QUEUED, session), null);
    }

    public Mono<CsSessionView> resume(String sessionId, String token) {
        return findForVisitor(sessionId, token)
            .zipWith(agentService.hasOnline())
            .map(tuple -> CsSessionView.of(tuple.getT1()).withAgentsOnline(tuple.getT2()));
    }

    @Transactional
    public Mono<CsChatMessageEntity> visitorMessage(String sessionId, String token, String content) {
        return findForVisitor(sessionId, token)
            .flatMap(session -> send(session, CsChatSender.visitor, content));
    }

    @Transactional
    public Mono<CsChatMessageEntity> visitorAttachment(String sessionId, String token, FilePart file) {
        return findForVisitor(sessionId, token)
            .flatMap(session -> sendAttachment(session, CsChatSender.visitor, file));
    }

    @Transactional
    public Mono<Void> visitorRead(String sessionId, String token) {
        return findForVisitor(sessionId, token)
            .flatMap(session -> createUpdate()
                .set(CsSessionEntity::getVisitorUnread, 0)
                .where(CsSessionEntity::getId, sessionId)
                .execute())
            .then();
    }

    @Transactional
    public Mono<Void> visitorClose(String sessionId, String token) {
        return findForVisitor(sessionId, token)
            .flatMap(session -> close(session, CLOSED_BY_VISITOR, null));
    }

    @Transactional
    public Mono<Void> rate(String sessionId, String token, CsSessionRateRequest request) {
        return findForVisitor(sessionId, token)
            .flatMap(session -> createUpdate()
                .set(CsSessionEntity::getRating, request.getRating())
                .set(CsSessionEntity::getRatingComment, request.getComment())
                .where(CsSessionEntity::getId, sessionId)
                .execute())
            .then();
    }

    @Transactional
    public Mono<Void> updateContact(String sessionId, String token, CsSessionContactRequest request) {
        request.validate();
        return findForVisitor(sessionId, token)
            .flatMap(session -> {
                session.setVisitorName(orKeep(request.getVisitorName(), session.getVisitorName()));
                session.setVisitorContact(orKeep(request.getVisitorContact(), session.getVisitorContact()));
                return createUpdate()
                    .set(CsSessionEntity::getVisitorName, session.getVisitorName())
                    .set(CsSessionEntity::getVisitorContact, session.getVisitorContact())
                    .where(CsSessionEntity::getId, sessionId)
                    .execute()
                    .then(publisher.publish(CsSessionEvent.of(CsSessionEvent.TYPE_CONTACT, session), session.getAgentId()));
            });
    }

    public Flux<CsChatMessageEntity> visitorHistory(String sessionId, String token) {
        return findForVisitor(sessionId, token)
            .flatMapMany(session -> history(sessionId));
    }

    public Mono<CsSessionEntity> findForVisitor(String sessionId, String token) {
        return findRequired(sessionId)
            .filter(session -> session.hasToken(token))
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.cs_session_token_invalid", 403)));
    }

    // ---------- 控制台(已登录用户)侧 ----------

    /**
     * 控制台用户发起会话: 已有进行中的会话就直接复用, 避免同一个人开出一堆并行会话占着坐席额度.
     */
    @Transactional
    public Mono<CsSessionView> openForUser(CsSupportIdentity identity, CsSupportOpenRequest request, ClientInfo client) {
        return findOpenForUser(identity.getUserId())
            .flatMap(exists -> agentService
                .hasOnline()
                .map(online -> CsSessionView.of(exists).withAgentsOnline(online)))
            .switchIfEmpty(Mono.defer(() -> createForUser(identity, request, client)));
    }

    private Mono<CsSessionView> createForUser(CsSupportIdentity identity, CsSupportOpenRequest request, ClientInfo client) {
        long now = System.currentTimeMillis();
        CsSessionEntity session = buildUserSession(identity, request, client, now);
        return pickAgent()
            .map(agent -> assignTo(session, agent, now))
            .defaultIfEmpty(session)
            .flatMap(this::insert)
            .then(Mono.defer(() -> request.hasFirstMessage()
                ? appendMessage(session, CsChatSender.visitor, request.getFirstMessage().strip(), now)
                : Mono.empty()))
            .then(Mono.defer(() -> announceOpened(session)))
            .then(agentService.hasOnline())
            .map(online -> CsSessionView.of(session).withAgentsOnline(online));
    }

    static CsSessionEntity buildUserSession(CsSupportIdentity identity,
                                            CsSupportOpenRequest request,
                                            ClientInfo client,
                                            long now) {
        CsSessionOpenRequest open = new CsSessionOpenRequest();
        open.setVisitorId(identity.getUserId());
        open.setVisitorName(identity.getUserName());
        open.setVisitorContact(identity.getTelephone());
        open.setSourcePage(request.getSourcePage());
        CsSessionEntity session = buildSession(open, client, now);
        session.setUserId(identity.getUserId());
        session.setTenantId(identity.getTenantId());
        session.setTenantName(identity.getTenantName());
        return session;
    }

    /** 我当前进行中的会话(排队或接待中), 用于控制台每次进来自动接上 */
    public Mono<CsSessionEntity> findOpenForUser(String userId) {
        return createQuery()
            .where()
            .is(CsSessionEntity::getUserId, userId)
            .in(CsSessionEntity::getState, Arrays.asList(CsSessionState.queued, CsSessionState.active))
            .orderBy(SortOrder.desc(CsSessionEntity::getCreateTime))
            .fetchOne();
    }

    /** 我最近的会话, 按时间倒序; 控制台用它加载历史对话 */
    public Flux<CsSessionEntity> recentForUser(String userId, int limit) {
        return createQuery()
            .where(CsSessionEntity::getUserId, userId)
            .orderBy(SortOrder.desc(CsSessionEntity::getCreateTime))
            .paging(0, limit)
            .fetch();
    }

    public Mono<CsSessionEntity> findForUser(String sessionId, String userId) {
        return findRequired(sessionId)
            .filter(session -> session.isOwnedBy(userId))
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.cs_session_not_yours", 403)));
    }

    @Transactional
    public Mono<CsChatMessageEntity> userMessage(String sessionId, String userId, String content) {
        return findForUser(sessionId, userId)
            .flatMap(session -> send(session, CsChatSender.visitor, content));
    }

    @Transactional
    public Mono<CsChatMessageEntity> userAttachment(String sessionId, String userId, FilePart file) {
        return findForUser(sessionId, userId)
            .flatMap(session -> sendAttachment(session, CsChatSender.visitor, file));
    }

    @Transactional
    public Mono<Void> userRead(String sessionId, String userId) {
        return findForUser(sessionId, userId)
            .flatMap(session -> createUpdate()
                .set(CsSessionEntity::getVisitorUnread, 0)
                .where(CsSessionEntity::getId, sessionId)
                .execute())
            .then();
    }

    @Transactional
    public Mono<Void> userClose(String sessionId, String userId) {
        return findForUser(sessionId, userId)
            .flatMap(session -> close(session, CLOSED_BY_VISITOR, null));
    }

    @Transactional
    public Mono<Void> userRate(String sessionId, String userId, CsSessionRateRequest request) {
        return findForUser(sessionId, userId)
            .flatMap(session -> createUpdate()
                .set(CsSessionEntity::getRating, request.getRating())
                .set(CsSessionEntity::getRatingComment, request.getComment())
                .where(CsSessionEntity::getId, sessionId)
                .execute())
            .then();
    }

    // ---------- 坐席侧 ----------

    public Flux<CsSessionEntity> queue() {
        return createQuery()
            .where(CsSessionEntity::getState, CsSessionState.queued)
            .orderBy(SortOrder.asc(CsSessionEntity::getQueuedAt))
            .fetch();
    }

    public Flux<CsSessionEntity> mine() {
        return currentUserId()
            .flatMapMany(userId -> createQuery()
                .where(CsSessionEntity::getState, CsSessionState.active)
                .and(CsSessionEntity::getAgentId, userId)
                .orderBy(SortOrder.desc(CsSessionEntity::getLastMessageAt))
                .fetch());
    }

    @Transactional
    public Mono<CsSessionEntity> accept(String sessionId) {
        return currentAuth()
            .flatMap(auth -> findRequired(sessionId)
                .flatMap(session -> {
                    CsSessionStateMachine.assertAccept(session.getState());
                    long now = System.currentTimeMillis();
                    session.setState(CsSessionState.active);
                    session.setAgentId(auth.getUser().getId());
                    session.setAgentName(auth.getUser().getName());
                    session.setAcceptedAt(now);
                    return createUpdate()
                        .set(CsSessionEntity::getState, CsSessionState.active)
                        .set(CsSessionEntity::getAgentId, session.getAgentId())
                        .set(CsSessionEntity::getAgentName, session.getAgentName())
                        .set(CsSessionEntity::getAcceptedAt, now)
                        .where(CsSessionEntity::getId, sessionId)
                        .and(CsSessionEntity::getState, CsSessionState.queued)
                        .execute()
                        .filter(updated -> updated > 0)
                        .switchIfEmpty(Mono.error(() -> new BusinessException("error.cs_session_not_queued", 400)))
                        .then(systemMessage(session, "客服 " + session.getAgentName() + " 已接入", now))
                        .then(publisher.publish(CsSessionEvent.of(CsSessionEvent.TYPE_ACCEPTED, session), session.getAgentId()))
                        .then(publisher.publishQueue(CsSessionEvent.of(CsSessionEvent.TYPE_ACCEPTED, session)))
                        .thenReturn(session);
                }));
    }

    @Transactional
    public Mono<CsChatMessageEntity> agentMessage(String sessionId, String content) {
        return findHandledByCurrent(sessionId)
            .flatMap(session -> send(session, CsChatSender.agent, content));
    }

    @Transactional
    public Mono<CsChatMessageEntity> agentAttachment(String sessionId, FilePart file) {
        return findHandledByCurrent(sessionId)
            .flatMap(session -> sendAttachment(session, CsChatSender.agent, file));
    }

    /**
     * 坐席发卡片. 卡片内容由调用方按会话现场生成(续费卡片会同时建订单和支付单),
     * 与消息写入同一事务: 消息没发出去, 生成的订单也一起回滚.
     */
    @Transactional
    public Mono<CsChatMessageEntity> agentCard(String sessionId, Function<CsSessionEntity, Mono<CsCardDraft>> cardBuilder) {
        return findHandledByCurrent(sessionId)
            .flatMap(session -> {
                if (!CsSessionStateMachine.canChat(session.getState())) {
                    return Mono.error(new BusinessException("error.cs_session_already_closed", 400));
                }
                return cardBuilder
                    .apply(session)
                    .flatMap(draft -> deliver(session, buildCardMessage(session, draft, System.currentTimeMillis())));
            });
    }

    static CsChatMessageEntity buildCardMessage(CsSessionEntity session, CsCardDraft draft, long now) {
        CsChatMessageEntity message = buildMessage(session, CsChatSender.agent, JSON.toJSONString(draft.getCard()), now);
        message.setType(CsChatMessageType.card);
        message.setRefType(draft.getRefType());
        message.setRefId(draft.getRefId());
        message.setRefStatus(draft.getRefStatus());
        return message;
    }

    /**
     * 卡片引用的对象状态变了(如支付单已支付): 回写所有引用它的卡片, 并推给这些卡片所在的会话.
     * 没有卡片引用它时什么都不做.
     */
    public Mono<Void> syncCardStatus(String refType, String refId, String status) {
        return messageRepository
            .createUpdate()
            .set(CsChatMessageEntity::getRefStatus, status)
            .where(CsChatMessageEntity::getRefType, refType)
            .and(CsChatMessageEntity::getRefId, refId)
            .execute()
            .filter(updated -> updated > 0)
            .flatMapMany(ignore -> messageRepository
                .createQuery()
                .where(CsChatMessageEntity::getRefType, refType)
                .and(CsChatMessageEntity::getRefId, refId)
                .fetch())
            .concatMap(message -> findById(message.getSessionId())
                .flatMap(session -> publisher.publish(cardEvent(session, message), session.getAgentId())))
            .then();
    }

    static CsSessionEvent cardEvent(CsSessionEntity session, CsChatMessageEntity message) {
        return new CsSessionEvent(CsSessionEvent.TYPE_CARD, session.getId(), CsSessionView.of(session), message,
                                  System.currentTimeMillis());
    }

    @Transactional
    public Mono<Void> agentRead(String sessionId) {
        return findHandledByCurrent(sessionId)
            .flatMap(session -> createUpdate()
                .set(CsSessionEntity::getAgentUnread, 0)
                .where(CsSessionEntity::getId, sessionId)
                .execute())
            .then();
    }

    @Transactional
    public Mono<Void> agentClose(String sessionId, List<String> tags) {
        return currentAuth()
            .flatMap(auth -> findRequired(sessionId)
                .flatMap(session -> {
                    if (session.getState() == CsSessionState.active && !session.isHandledBy(auth.getUser().getId())
                        && !leadService.isSupervisor(auth)) {
                        return Mono.error(new BusinessException("error.cs_session_not_yours", 403));
                    }
                    return close(session, CLOSED_BY_AGENT, tags);
                }));
    }

    @Transactional
    public Mono<CsSessionEntity> transfer(String sessionId, String toAgentId) {
        return currentAuth()
            .flatMap(auth -> findRequired(sessionId)
                .flatMap(session -> {
                    CsSessionStateMachine.assertTransfer(session.getState());
                    if (!session.isHandledBy(auth.getUser().getId()) && !leadService.isSupervisor(auth)) {
                        return Mono.error(new BusinessException("error.cs_session_not_yours", 403));
                    }
                    return agentService
                        .findById(toAgentId)
                        .filter(CsAgentEntity::isOnline)
                        .switchIfEmpty(Mono.error(() -> new BusinessException("error.cs_agent_not_online", 400)))
                        .flatMap(target -> moveTo(session, target));
                }));
    }

    private Mono<CsSessionEntity> moveTo(CsSessionEntity session, CsAgentEntity target) {
        String fromAgentId = session.getAgentId();
        long now = System.currentTimeMillis();
        session.setAgentId(target.getId());
        session.setAgentName(target.getName());
        return createUpdate()
            .set(CsSessionEntity::getAgentId, target.getId())
            .set(CsSessionEntity::getAgentName, target.getName())
            .where(CsSessionEntity::getId, session.getId())
            .execute()
            .then(systemMessage(session, "会话已转接给客服 " + target.getName(), now))
            .then(publisher.publishTransfer(CsSessionEvent.of(CsSessionEvent.TYPE_TRANSFERRED, session), fromAgentId, target.getId()))
            .thenReturn(session);
    }

    /**
     * 会话转线索: 同一联系方式归入已有线索; 会话记住线索ID, 线索计一次消息.
     */
    @Transactional
    public Mono<CsLeadEntity> toLead(String sessionId, CsSessionLeadRequest request) {
        return findRequired(sessionId)
            .flatMap(session -> leadService
                .findOrCreate(request.toDraft(session))
                .flatMap(lead -> createUpdate()
                    .set(CsSessionEntity::getLeadId, lead.getId())
                    .where(CsSessionEntity::getId, sessionId)
                    .execute()
                    .then(leadService.recordMessage(lead.getId(), session.getLastMessage(), System.currentTimeMillis()))
                    .thenReturn(lead)));
    }

    public Flux<CsChatMessageEntity> history(String sessionId) {
        return messageRepository
            .createQuery()
            .where(CsChatMessageEntity::getSessionId, sessionId)
            .orderBy(SortOrder.desc(CsChatMessageEntity::getCreateTime))
            .paging(0, properties.getChat().getHistoryLimit())
            .fetch()
            .collectList()
            .flatMapIterable(list -> {
                java.util.Collections.reverse(list);
                return list;
            });
    }

    private Mono<CsSessionEntity> findHandledByCurrent(String sessionId) {
        return currentUserId()
            .flatMap(userId -> findRequired(sessionId)
                .filter(session -> session.isHandledBy(userId))
                .switchIfEmpty(Mono.error(() -> new BusinessException("error.cs_session_not_yours", 403))));
    }

    // ---------- 系统 ----------

    /**
     * 结束空闲会话, 返回结束数量
     */
    public Mono<Integer> closeIdleBefore(long cutoff) {
        return createQuery()
            .where()
            .in(CsSessionEntity::getState, Arrays.asList(CsSessionState.queued, CsSessionState.active))
            .lt(CsSessionEntity::getLastMessageAt, cutoff)
            .fetch()
            .concatMap(session -> close(session, CLOSED_BY_SYSTEM, null)
                .thenReturn(1)
                .onErrorResume(err -> {
                    log.warn("close idle customer-service session [{}] failed: {}", session.getId(), err.getMessage());
                    return Mono.just(0);
                }))
            .reduce(0, Integer::sum);
    }

    // ---------- 内部 ----------

    private Mono<CsAgentEntity> pickAgent() {
        return Mono
            .zip(agentService.findOnline().collectList(), activeCounts())
            .flatMap(tuple -> Mono.justOrEmpty(pick(tuple.getT1(), tuple.getT2())));
    }

    private Optional<CsAgentEntity> pick(List<CsAgentEntity> online, Map<String, Integer> counts) {
        return CsAgentPicker.pick(online, counts, properties.getChat().getDefaultMaxConcurrent());
    }

    Mono<Map<String, Integer>> activeCounts() {
        return createQuery()
            .where(CsSessionEntity::getState, CsSessionState.active)
            .fetch()
            .filter(session -> session.getAgentId() != null)
            .collect(HashMap::new, (map, session) -> map.merge(session.getAgentId(), 1, Integer::sum));
    }

    private Mono<CsChatMessageEntity> send(CsSessionEntity session, CsChatSender sender, String content) {
        CsSessionStateMachine.assertChat(session.getState());
        return deliver(session, buildMessage(session, sender, content, System.currentTimeMillis()));
    }

    /**
     * 附件: 先按扩展名归类(不在白名单直接拒绝), 存到平台文件服务(公开访问), 超过该类型上限则删掉文件再报错.
     */
    private Mono<CsChatMessageEntity> sendAttachment(CsSessionEntity session, CsChatSender sender, FilePart file) {
        CsSessionStateMachine.assertChat(session.getState());
        CsChatMessageType type = attachmentPolicy.classify(file.filename());
        return fileManager
            .saveFile(file, FileOption.publicAccess)
            .flatMap(info -> attachmentPolicy.allowsSize(type, info.getLength())
                ? Mono.just(info)
                : fileManager.delete(info.getId())
                             .then(Mono.error(() -> new ValidationException.NoStackTrace(CsAttachmentPolicy.ERROR_SIZE))))
            .flatMap(info -> deliver(session, buildAttachmentMessage(session, sender, type, info, System.currentTimeMillis())));
    }

    private Mono<CsChatMessageEntity> deliver(CsSessionEntity session, CsChatMessageEntity message) {
        applyMessage(session, message);
        return messageRepository
            .insert(message)
            .then(persistCounters(session))
            .then(publisher.publish(CsSessionEvent.message(session, message), session.getAgentId()))
            .thenReturn(message);
    }

    private Mono<Void> systemMessage(CsSessionEntity session, String text, long now) {
        CsChatMessageEntity message = buildMessage(session, CsChatSender.system, text, now);
        session.setMessageCount(count(session.getMessageCount()) + 1);
        return messageRepository
            .insert(message)
            .then(createUpdate()
                      .set(CsSessionEntity::getMessageCount, session.getMessageCount())
                      .where(CsSessionEntity::getId, session.getId())
                      .execute())
            .then(publisher.publish(CsSessionEvent.message(session, message), session.getAgentId()));
    }

    private Mono<Void> persistCounters(CsSessionEntity session) {
        return createUpdate()
            .set(CsSessionEntity::getLastMessage, session.getLastMessage())
            .set(CsSessionEntity::getLastMessageAt, session.getLastMessageAt())
            .set(CsSessionEntity::getMessageCount, session.getMessageCount())
            .set(CsSessionEntity::getAgentUnread, session.getAgentUnread())
            .set(CsSessionEntity::getVisitorUnread, session.getVisitorUnread())
            .where(CsSessionEntity::getId, session.getId())
            .execute()
            .then();
    }

    private Mono<Void> close(CsSessionEntity session, String closedBy, List<String> tags) {
        CsSessionStateMachine.assertClose(session.getState());
        long now = System.currentTimeMillis();
        session.setState(CsSessionState.closed);
        session.setClosedAt(now);
        session.setClosedBy(closedBy);
        if (tags != null) {
            session.setTags(tags);
        }
        ReactiveUpdate<CsSessionEntity> update = createUpdate()
            .set(CsSessionEntity::getState, CsSessionState.closed)
            .set(CsSessionEntity::getClosedAt, now)
            .set(CsSessionEntity::getClosedBy, closedBy);
        if (tags != null) {
            update.set(CsSessionEntity::getTags, tags);
        }
        boolean wasQueued = session.getQueuedAt() != null && session.getAcceptedAt() == null;
        return update
            .where(CsSessionEntity::getId, session.getId())
            .execute()
            .then(systemMessage(session, closeText(closedBy), now))
            .then(publisher.publish(CsSessionEvent.of(CsSessionEvent.TYPE_CLOSED, session), session.getAgentId()))
            .then(wasQueued ? Mono.empty() : publisher.publishQueue(CsSessionEvent.of(CsSessionEvent.TYPE_CLOSED, session)));
    }

    static String closeText(String closedBy) {
        if (CLOSED_BY_VISITOR.equals(closedBy)) {
            return "访客已结束会话";
        }
        if (CLOSED_BY_AGENT.equals(closedBy)) {
            return "客服已结束会话";
        }
        return "会话长时间无消息, 已自动结束";
    }

    static CsSessionEntity buildSession(CsSessionOpenRequest request, ClientInfo client, long now) {
        CsSessionEntity session = new CsSessionEntity();
        session.setId(IDGenerator.SNOW_FLAKE_STRING.generate());
        session.setVisitorId(request.getVisitorId());
        session.setVisitorToken(newToken());
        session.setVisitorName(blankToNull(request.getVisitorName()));
        session.setVisitorContact(blankToNull(request.getVisitorContact()));
        session.setState(CsSessionState.queued);
        session.setSourcePage(request.getSourcePage());
        session.setReferrer(request.getReferrer());
        session.setUtm(request.getUtm());
        session.setClientIp(client.getIp());
        session.setQueuedAt(now);
        session.setLastMessageAt(now);
        session.setMessageCount(0);
        session.setAgentUnread(0);
        session.setVisitorUnread(0);
        session.setCreateTime(now);
        return session;
    }

    static CsSessionEntity assignTo(CsSessionEntity session, CsAgentEntity agent, long now) {
        session.setState(CsSessionState.active);
        session.setAgentId(agent.getId());
        session.setAgentName(agent.getName());
        session.setAcceptedAt(now);
        return session;
    }

    static CsChatMessageEntity buildMessage(CsSessionEntity session, CsChatSender sender, String content, long now) {
        CsChatMessageEntity message = new CsChatMessageEntity();
        message.setId(IDGenerator.SNOW_FLAKE_STRING.generate());
        message.setSessionId(session.getId());
        message.setSender(sender);
        message.setSenderName(senderName(session, sender));
        message.setType(CsChatMessageType.text);
        message.setContent(content);
        message.setCreateTime(now);
        return message;
    }

    static CsChatMessageEntity buildAttachmentMessage(CsSessionEntity session,
                                                      CsChatSender sender,
                                                      CsChatMessageType type,
                                                      FileInfo info,
                                                      long now) {
        CsChatMessageEntity message = buildMessage(session, sender, CsAttachmentPolicy.accessPath(info), now);
        message.setType(type);
        message.setFileName(info.getName());
        message.setFileSize(info.getLength());
        return message;
    }

    static String senderName(CsSessionEntity session, CsChatSender sender) {
        if (sender == CsChatSender.agent) {
            return session.getAgentName();
        }
        if (sender == CsChatSender.system) {
            return SYSTEM_SENDER_NAME;
        }
        return session.getVisitorName() == null ? "访客" : session.getVisitorName();
    }

    /**
     * 消息落到会话上: 摘要、时间、计数, 以及对方的未读数.
     */
    static CsSessionEntity applyMessage(CsSessionEntity session, CsChatMessageEntity message) {
        session.setLastMessage(summaryOf(message));
        session.setLastMessageAt(message.getCreateTime());
        session.setMessageCount(count(session.getMessageCount()) + 1);
        if (message.getSender() == CsChatSender.visitor) {
            session.setAgentUnread(count(session.getAgentUnread()) + 1);
        }
        if (message.getSender() == CsChatSender.agent) {
            session.setVisitorUnread(count(session.getVisitorUnread()) + 1);
        }
        return session;
    }

    static String summaryOf(CsChatMessageEntity message) {
        CsChatMessageType type = message.typeOrText();
        if (type == CsChatMessageType.card) {
            return cardSummary(message.getContent());
        }
        return type.isAttachment() ? CsAttachmentPolicy.summaryOf(type, message.getFileName()) : summarize(message.getContent());
    }

    static final String CARD_SUMMARY_PREFIX = "[卡片]";

    /** 会话列表摘要只取卡片标题; 内容损坏时退回类型名, 不影响消息本身落库 */
    static String cardSummary(String content) {
        String title = null;
        try {
            JSONObject card = content == null ? null : JSON.parseObject(content);
            title = card == null ? null : card.getString("title");
        } catch (RuntimeException e) {
            log.debug("card content is not valid json, fallback to type summary", e);
        }
        return title == null || title.isBlank() ? CARD_SUMMARY_PREFIX : summarize(CARD_SUMMARY_PREFIX + " " + title);
    }

    static String summarize(String content) {
        String text = content == null ? "" : content.replaceAll("\\s+", " ").strip();
        return text.length() <= LAST_MESSAGE_MAX_LENGTH ? text : text.substring(0, LAST_MESSAGE_MAX_LENGTH);
    }

    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static int count(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String orKeep(String next, String current) {
        return next == null || next.isBlank() ? current : next.strip();
    }

    Mono<CsSessionEntity> findRequired(String sessionId) {
        return findById(sessionId)
            .switchIfEmpty(Mono.error(() -> new NotFoundException("error.cs_session_not_found")));
    }

    private Mono<Authentication> currentAuth() {
        return Authentication
            .currentReactive()
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.unauthorized", 401)));
    }

    private Mono<String> currentUserId() {
        return currentAuth().map(auth -> auth.getUser().getId());
    }
}
