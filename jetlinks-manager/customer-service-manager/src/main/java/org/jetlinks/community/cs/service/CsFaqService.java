package org.jetlinks.community.cs.service;

import org.hswebframework.ezorm.rdb.operator.dml.query.SortOrder;
import org.hswebframework.web.crud.service.GenericReactiveCrudService;
import org.jetlinks.community.cs.entity.CsFaqEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 常见问题维护; 官网只读启用中的条目.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public class CsFaqService extends GenericReactiveCrudService<CsFaqEntity, String> {

    public Flux<CsFaqEntity> findEnabled(int limit) {
        return createQuery()
            .where(CsFaqEntity::getEnabled, true)
            .orderBy(SortOrder.asc(CsFaqEntity::getSortIndex), SortOrder.desc(CsFaqEntity::getHits))
            .paging(0, limit)
            .fetch();
    }

    /**
     * 官网点击计数; 条目不存在或已停用时静默忽略, 不给匿名接口暴露存在性
     */
    public Mono<Void> hit(String id) {
        return findById(id)
            .filter(CsFaqEntity::isEnabledOrDefault)
            .flatMap(faq -> createUpdate()
                .set(CsFaqEntity::getHits, (faq.getHits() == null ? 0 : faq.getHits()) + 1)
                .where(CsFaqEntity::getId, id)
                .execute())
            .then();
    }
}
