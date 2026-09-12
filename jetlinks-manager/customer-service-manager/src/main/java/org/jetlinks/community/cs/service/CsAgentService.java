package org.jetlinks.community.cs.service;

import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.entity.CsAgentEntity;
import org.jetlinks.community.cs.enums.CsAgentStatus;
import org.jetlinks.community.cs.service.request.CsAgentStatusRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 坐席状态: 上线 / 忙碌 / 下线. 坐席记录以用户ID为主键, 首次上线时创建.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public class CsAgentService extends GenericReactiveCrudService<CsAgentEntity, String> {

    private final CsProperties properties;

    public CsAgentService(CsProperties properties) {
        this.properties = properties;
    }

    public Mono<CsAgentEntity> current() {
        return Authentication
            .currentReactive()
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.unauthorized", 401)))
            .flatMap(auth -> findById(auth.getUser().getId())
                .defaultIfEmpty(offlineAgent(auth, properties.getChat().getDefaultMaxConcurrent())));
    }

    public Mono<CsAgentEntity> updateStatus(CsAgentStatusRequest request) {
        return Authentication
            .currentReactive()
            .switchIfEmpty(Mono.error(() -> new BusinessException("error.unauthorized", 401)))
            .flatMap(auth -> findById(auth.getUser().getId())
                .defaultIfEmpty(offlineAgent(auth, properties.getChat().getDefaultMaxConcurrent()))
                .map(agent -> applyStatus(agent, request, auth.getUser().getName(), System.currentTimeMillis()))
                .flatMap(agent -> save(agent).thenReturn(agent)));
    }

    static CsAgentEntity offlineAgent(Authentication auth, int defaultMaxConcurrent) {
        CsAgentEntity agent = new CsAgentEntity();
        agent.setId(auth.getUser().getId());
        agent.setName(auth.getUser().getName());
        agent.setStatus(CsAgentStatus.offline);
        agent.setMaxConcurrent(defaultMaxConcurrent);
        return agent;
    }

    static CsAgentEntity applyStatus(CsAgentEntity agent, CsAgentStatusRequest request, String name, long now) {
        agent.setName(name);
        agent.setStatus(request.getStatus());
        if (request.getMaxConcurrent() != null) {
            agent.setMaxConcurrent(request.getMaxConcurrent());
        }
        agent.setLastActiveAt(now);
        return agent;
    }

    public Flux<CsAgentEntity> findOnline() {
        return createQuery()
            .where(CsAgentEntity::getStatus, CsAgentStatus.online)
            .fetch();
    }

    public Mono<Boolean> hasOnline() {
        return createQuery()
            .where(CsAgentEntity::getStatus, CsAgentStatus.online)
            .count()
            .map(count -> count > 0);
    }

    public Mono<Void> touch(String agentId) {
        return createUpdate()
            .set(CsAgentEntity::getLastActiveAt, System.currentTimeMillis())
            .where(CsAgentEntity::getId, agentId)
            .execute()
            .then();
    }
}
