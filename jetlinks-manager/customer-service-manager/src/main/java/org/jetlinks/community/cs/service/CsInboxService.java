package org.jetlinks.community.cs.service;

import lombok.RequiredArgsConstructor;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.entity.CsInboxMessageEntity;
import org.jetlinks.community.cs.lead.ContactKeys;
import org.jetlinks.community.cs.service.request.CsInboxSubmitRequest;
import org.jetlinks.community.cs.web.ClientInfo;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 官网留言: 落库 → 归入线索 → 通知客服.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class CsInboxService extends GenericReactiveCrudService<CsInboxMessageEntity, String> {

    private final CsProperties properties;
    private final CsLeadService leadService;
    private final CsAgentNotifier notifier;

    /**
     * 留言与线索在同一事务内写入; 通知是旁路(外部渠道失败已在 notifier 内降级), 放在链尾不影响落库.
     */
    @Transactional
    public Mono<CsInboxMessageEntity> submit(CsInboxSubmitRequest request, ClientInfo client) {
        request.validate(properties.getInbox().getContentMaxLength());
        long now = System.currentTimeMillis();
        return leadService
            .findOrCreateForContact(request)
            .flatMap(lead -> {
                CsInboxMessageEntity message = buildMessage(request, client, lead.getId(), now);
                return insert(message)
                    .then(leadService.recordMessage(lead.getId(), request.getContent(), now))
                    .then(notifier.notifyNewMessage(message))
                    .thenReturn(message);
            });
    }

    static CsInboxMessageEntity buildMessage(CsInboxSubmitRequest request, ClientInfo client, String leadId, long now) {
        CsInboxMessageEntity message = new CsInboxMessageEntity();
        message.setId(IDGenerator.SNOW_FLAKE_STRING.generate());
        message.setLeadId(leadId);
        message.setVisitorId(request.getVisitorId());
        message.setName(request.getName());
        message.setPhone(ContactKeys.normalizePhone(request.getPhone()));
        message.setWechat(ContactKeys.normalizeWechat(request.getWechat()));
        message.setCompany(request.getCompany());
        message.setContent(request.getContent());
        message.setSourcePage(request.getSourcePage());
        message.setReferrer(request.getReferrer());
        message.setUtm(request.getUtm());
        message.setTrail(request.getTrail());
        message.setClientIp(client.getIp());
        message.setUserAgent(client.getUserAgent());
        message.setReadFlag(false);
        message.setCreateTime(now);
        return message;
    }

    public Mono<Integer> markRead(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Mono.just(0);
        }
        return createUpdate()
            .set(CsInboxMessageEntity::getReadFlag, true)
            .set(CsInboxMessageEntity::getReadAt, System.currentTimeMillis())
            .where()
            .in(CsInboxMessageEntity::getId, ids)
            .and(CsInboxMessageEntity::getReadFlag, false)
            .execute();
    }

    public Mono<Integer> countUnread() {
        return createQuery()
            .where(CsInboxMessageEntity::getReadFlag, false)
            .count();
    }

    /**
     * 清理保留期之外的留言, 返回删除条数
     */
    public Mono<Integer> deleteBefore(long cutoffTime) {
        return createDelete()
            .where()
            .lt(CsInboxMessageEntity::getCreateTime, cutoffTime)
            .execute();
    }
}
