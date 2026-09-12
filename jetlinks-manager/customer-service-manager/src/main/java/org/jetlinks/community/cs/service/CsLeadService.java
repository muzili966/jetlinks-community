package org.jetlinks.community.cs.service;

import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.ezorm.rdb.mapping.ReactiveUpdate;
import org.hswebframework.ezorm.rdb.operator.dml.query.SortOrder;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.DefaultDimensionType;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.hswebframework.web.exception.BusinessException;
import org.hswebframework.web.exception.NotFoundException;
import org.hswebframework.web.id.IDGenerator;
import org.hswebframework.web.system.authorization.api.entity.UserEntity;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.entity.CsLeadEntity;
import org.jetlinks.community.cs.entity.CsLeadFollowEntity;
import org.jetlinks.community.cs.enums.CsLeadSource;
import org.jetlinks.community.cs.enums.CsLeadState;
import org.jetlinks.community.cs.lead.ContactKeys;
import org.jetlinks.community.cs.lead.CsLeadStateMachine;
import org.jetlinks.community.cs.service.request.CsConvertRequest;
import org.jetlinks.community.cs.service.request.CsFollowRequest;
import org.jetlinks.community.cs.service.request.CsInboxSubmitRequest;
import org.jetlinks.community.cs.web.response.CsLeadSummary;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.service.TenantService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 线索: 留言归并、认领 / 指派、跟进、状态流转、转化为租户.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Slf4j
public class CsLeadService extends GenericReactiveCrudService<CsLeadEntity, String> {

    private final CsProperties properties;
    private final ReactiveRepository<CsLeadFollowEntity, String> followRepository;
    private final ReactiveRepository<UserEntity, String> userRepository;
    private final ObjectProvider<TenantService> tenantService;

    public CsLeadService(CsProperties properties,
                         ReactiveRepository<CsLeadFollowEntity, String> followRepository,
                         ReactiveRepository<UserEntity, String> userRepository,
                         ObjectProvider<TenantService> tenantService) {
        this.properties = properties;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.tenantService = tenantService;
    }

    // ---------- 留言归并 ----------

    /**
     * 同一联系方式的留言归到同一条线索: 先按手机号找, 再按微信号找, 都没有则新建.
     * 已标记无效的线索再次留言时重新激活为新线索.
     */
    @Transactional
    public Mono<CsLeadEntity> findOrCreateForContact(CsInboxSubmitRequest request) {
        String phone = ContactKeys.normalizePhone(request.getPhone());
        String wechat = ContactKeys.normalizeWechat(request.getWechat());
        return findLatestByPhone(phone)
            .switchIfEmpty(Mono.defer(() -> findLatestByWechat(wechat)))
            .flatMap(this::reactivateIfInvalid)
            .switchIfEmpty(Mono.defer(() -> insertLead(buildLead(request, phone, wechat))));
    }

    private Mono<CsLeadEntity> findLatestByPhone(String phone) {
        if (phone == null) {
            return Mono.empty();
        }
        return createQuery()
            .where(CsLeadEntity::getPhone, phone)
            .orderBy(SortOrder.desc(CsLeadEntity::getCreateTime))
            .fetchOne();
    }

    private Mono<CsLeadEntity> findLatestByWechat(String wechat) {
        if (wechat == null) {
            return Mono.empty();
        }
        return createQuery()
            .where(CsLeadEntity::getWechat, wechat)
            .orderBy(SortOrder.desc(CsLeadEntity::getCreateTime))
            .fetchOne();
    }

    private Mono<CsLeadEntity> reactivateIfInvalid(CsLeadEntity lead) {
        if (lead.getState() != CsLeadState.invalid) {
            return Mono.just(lead);
        }
        lead.setState(CsLeadState.pending);
        lead.setStateReason(null);
        return createUpdate()
            .set(CsLeadEntity::getState, CsLeadState.pending)
            .setNull(CsLeadEntity::getStateReason)
            .where(CsLeadEntity::getId, lead.getId())
            .execute()
            .thenReturn(lead);
    }

    static CsLeadEntity buildLead(CsInboxSubmitRequest request, String phone, String wechat) {
        CsLeadEntity lead = new CsLeadEntity();
        lead.setId(IDGenerator.SNOW_FLAKE_STRING.generate());
        lead.setName(request.getName());
        lead.setPhone(phone);
        lead.setWechat(wechat);
        lead.setCompany(request.getCompany());
        lead.setSummary(request.getContent());
        lead.setSource(CsLeadSource.website);
        lead.setSourcePage(request.getSourcePage());
        lead.setUtm(request.getUtm());
        lead.setState(CsLeadState.pending);
        lead.setFollowCount(0);
        lead.setMessageCount(0);
        return lead;
    }

    private Mono<CsLeadEntity> insertLead(CsLeadEntity lead) {
        return insert(lead).thenReturn(lead);
    }

    /**
     * 留言归入线索后刷新计数; 首次留言的内容作为需求摘要.
     */
    @Transactional
    public Mono<Void> recordMessage(String leadId, String content, long now) {
        return findRequired(leadId)
            .flatMap(lead -> {
                CsLeadEntity next = applyMessage(lead, content, now);
                return createUpdate()
                    .set(CsLeadEntity::getMessageCount, next.getMessageCount())
                    .set(CsLeadEntity::getLastMessageAt, next.getLastMessageAt())
                    .set(CsLeadEntity::getSummary, next.getSummary())
                    .set(CsLeadEntity::getModifyTime, now)
                    .where(CsLeadEntity::getId, leadId)
                    .execute();
            })
            .then();
    }

    static CsLeadEntity applyMessage(CsLeadEntity lead, String content, long now) {
        int count = lead.getMessageCount() == null ? 0 : lead.getMessageCount();
        lead.setMessageCount(count + 1);
        lead.setLastMessageAt(now);
        if (lead.getSummary() == null || lead.getSummary().isBlank()) {
            lead.setSummary(content);
        }
        return lead;
    }

    // ---------- 认领与指派 ----------

    @Transactional
    public Mono<CsLeadEntity> claim(String leadId) {
        return Authentication
            .currentReactive()
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.unauthorized", 401)))
            .flatMap(auth -> findRequired(leadId)
                .flatMap(lead -> {
                    if (lead.hasOwner() && !lead.isOwnedBy(auth.getUser().getId()) && !isSupervisor(auth)) {
                        return Mono.error(new BusinessException("error.cs_lead_owned_by_other", 400, lead.getOwnerName()));
                    }
                    return setOwner(lead, auth.getUser().getId(), auth.getUser().getName(), auth);
                }));
    }

    @Transactional
    public Mono<CsLeadEntity> assign(String leadId, String ownerId) {
        return Authentication
            .currentReactive()
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.unauthorized", 401)))
            .filter(this::isSupervisor)
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.cs_lead_assign_supervisor_only", 403)))
            .flatMap(auth -> Mono
                .zip(findRequired(leadId), findUser(ownerId))
                .flatMap(tuple -> setOwner(tuple.getT1(), ownerId, tuple.getT2().getName(), auth)));
    }

    private Mono<UserEntity> findUser(String userId) {
        return userRepository
            .findById(userId)
            .switchIfEmpty(Mono.error(() -> new NotFoundException("error.user_not_exist")));
    }

    /**
     * 设置负责人; 新线索被认领即进入跟进中.
     */
    private Mono<CsLeadEntity> setOwner(CsLeadEntity lead, String ownerId, String ownerName, Authentication auth) {
        long now = System.currentTimeMillis();
        CsLeadState nextState = lead.getState() == CsLeadState.pending ? CsLeadState.following : lead.getState();
        return touch(createUpdate(), auth, now)
            .set(CsLeadEntity::getOwnerId, ownerId)
            .set(CsLeadEntity::getOwnerName, ownerName)
            .set(CsLeadEntity::getState, nextState)
            .where(CsLeadEntity::getId, lead.getId())
            .execute()
            .then(findRequired(lead.getId()));
    }

    // ---------- 状态与跟进 ----------

    @Transactional
    public Mono<Void> changeState(String leadId, CsLeadState state, String reason) {
        return Authentication
            .currentReactive()
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.unauthorized", 401)))
            .flatMap(auth -> findRequired(leadId)
                .flatMap(lead -> {
                    CsLeadStateMachine.assertTransit(lead.getState(), state);
                    return touch(createUpdate(), auth, System.currentTimeMillis())
                        .set(CsLeadEntity::getState, state)
                        .set(CsLeadEntity::getStateReason, state == CsLeadState.pending ? null : reason)
                        .where(CsLeadEntity::getId, leadId)
                        .execute();
                }))
            .then();
    }

    @Transactional
    public Mono<CsLeadFollowEntity> addFollow(String leadId, CsFollowRequest request) {
        return Authentication
            .currentReactive()
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.unauthorized", 401)))
            .flatMap(auth -> findRequired(leadId)
                .flatMap(lead -> {
                    long now = System.currentTimeMillis();
                    CsLeadFollowEntity follow = buildFollow(leadId, request, auth, now);
                    return followRepository
                        .insert(follow)
                        .then(afterFollow(lead, request, auth, now))
                        .thenReturn(follow);
                }));
    }

    static CsLeadFollowEntity buildFollow(String leadId, CsFollowRequest request, Authentication auth, long now) {
        CsLeadFollowEntity follow = new CsLeadFollowEntity();
        follow.setId(IDGenerator.SNOW_FLAKE_STRING.generate());
        follow.setLeadId(leadId);
        follow.setChannel(request.getChannel());
        follow.setContent(request.getContent());
        follow.setNextFollowAt(request.getNextFollowAt());
        follow.setCreatorId(auth.getUser().getId());
        follow.setCreatorName(auth.getUser().getName());
        follow.setCreateTime(now);
        return follow;
    }

    /**
     * 跟进后刷新线索: 计数、最近 / 下次跟进时间; 新线索首次跟进即进入跟进中.
     */
    private Mono<Integer> afterFollow(CsLeadEntity lead, CsFollowRequest request, Authentication auth, long now) {
        int count = lead.getFollowCount() == null ? 0 : lead.getFollowCount();
        CsLeadState nextState = lead.getState() == CsLeadState.pending ? CsLeadState.following : lead.getState();
        ReactiveUpdate<CsLeadEntity> update = touch(createUpdate(), auth, now)
            .set(CsLeadEntity::getFollowCount, count + 1)
            .set(CsLeadEntity::getLastFollowAt, now)
            .set(CsLeadEntity::getState, nextState);
        if (request.getNextFollowAt() == null) {
            update.setNull(CsLeadEntity::getNextFollowAt);
        } else {
            update.set(CsLeadEntity::getNextFollowAt, request.getNextFollowAt());
        }
        return update.where(CsLeadEntity::getId, lead.getId()).execute();
    }

    public Flux<CsLeadFollowEntity> queryFollows(String leadId) {
        return followRepository
            .createQuery()
            .where(CsLeadFollowEntity::getLeadId, leadId)
            .orderBy(SortOrder.desc(CsLeadFollowEntity::getCreateTime))
            .fetch();
    }

    // ---------- 转化 ----------

    /**
     * 转化为租户: 关联已有租户或新建租户, 线索进入已转化(终态).
     * 租户模块未启用时不能转化, 明确报错而不是静默跳过.
     */
    @Transactional
    public Mono<CsLeadEntity> convert(String leadId, CsConvertRequest request) {
        request.validate();
        TenantService tenants = tenantService.getIfAvailable();
        if (tenants == null) {
            return Mono.error(new BusinessException("error.cs_tenant_module_disabled", 400));
        }
        return Authentication
            .currentReactive()
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.unauthorized", 401)))
            .flatMap(auth -> findRequired(leadId)
                .flatMap(lead -> {
                    CsLeadStateMachine.assertTransit(lead.getState(), CsLeadState.converted);
                    return resolveTenant(tenants, request, lead)
                        .flatMap(tenant -> touch(createUpdate(), auth, System.currentTimeMillis())
                            .set(CsLeadEntity::getTenantId, tenant.getId())
                            .set(CsLeadEntity::getTenantName, tenant.getName())
                            .set(CsLeadEntity::getState, CsLeadState.converted)
                            .where(CsLeadEntity::getId, leadId)
                            .execute());
                }))
            .then(findRequired(leadId));
    }

    private Mono<TenantEntity> resolveTenant(TenantService tenants, CsConvertRequest request, CsLeadEntity lead) {
        if (request.isLinkExisting()) {
            return tenants
                .findById(request.getTenantId())
                .switchIfEmpty(Mono.error(() -> new BusinessException("error.tenant_not_exist", 404, request.getTenantId())));
        }
        TenantEntity tenant = buildTenant(request, lead);
        return tenants.insert(tenant).thenReturn(tenant);
    }

    static TenantEntity buildTenant(CsConvertRequest request, CsLeadEntity lead) {
        TenantEntity tenant = new TenantEntity();
        tenant.setId(request.getNewTenantId());
        String name = request.getNewTenantName();
        if (name == null || name.isBlank()) {
            name = lead.getCompany() == null || lead.getCompany().isBlank() ? lead.getName() : lead.getCompany();
        }
        tenant.setName(name);
        tenant.setPlanId(request.getPlanId());
        tenant.setDescribe("由客服线索转化, 线索ID: " + lead.getId());
        return tenant;
    }

    // ---------- 看板 ----------

    public Mono<CsLeadSummary> summary() {
        long now = System.currentTimeMillis();
        return Mono
            .zip(countByState(CsLeadState.pending),
                 countByState(CsLeadState.following),
                 countByState(CsLeadState.converted),
                 countByState(CsLeadState.invalid),
                 countOverdue(now))
            .map(counts -> new CsLeadSummary(counts.getT1(), counts.getT2(), counts.getT3(), counts.getT4(), counts.getT5()));
    }

    private Mono<Long> countByState(CsLeadState state) {
        return createQuery()
            .where(CsLeadEntity::getState, state)
            .count()
            .map(Integer::longValue);
    }

    private Mono<Long> countOverdue(long now) {
        return createQuery()
            .where(CsLeadEntity::getState, CsLeadState.following)
            .lt(CsLeadEntity::getNextFollowAt, now)
            .count()
            .map(Integer::longValue);
    }

    // ---------- 通用 ----------

    Mono<CsLeadEntity> findRequired(String leadId) {
        return findById(leadId)
            .switchIfEmpty(Mono.error(() -> new NotFoundException("error.cs_lead_not_found")));
    }

    boolean isSupervisor(Authentication auth) {
        String supervisorRoleId = properties.getSupervisorRoleId();
        return auth
            .getDimensions()
            .stream()
            .anyMatch(dimension -> DefaultDimensionType.role.getId().equals(dimension.getType().getId())
                && supervisorRoleId.equals(dimension.getId()));
    }

    private ReactiveUpdate<CsLeadEntity> touch(ReactiveUpdate<CsLeadEntity> update, Authentication auth, long now) {
        return update
            .set(CsLeadEntity::getModifierId, auth.getUser().getId())
            .set(CsLeadEntity::getModifyTime, now);
    }
}
