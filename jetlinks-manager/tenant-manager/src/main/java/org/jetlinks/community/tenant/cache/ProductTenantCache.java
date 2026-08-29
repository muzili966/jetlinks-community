package org.jetlinks.community.tenant.cache;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.crud.events.EntityCreatedEvent;
import org.hswebframework.web.crud.events.EntityDeletedEvent;
import org.hswebframework.web.crud.events.EntityModifyEvent;
import org.hswebframework.web.crud.events.EntitySavedEvent;
import org.jetlinks.community.device.entity.DeviceProductEntity;
import org.jetlinks.community.tenant.TenantAware;
import org.jetlinks.community.tenant.TenantConstants;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * productId → tenantId 本地缓存.
 * <p>
 * 时序写入与 topic 鉴权都在热路径上, 不允许查库;
 * 启动时全量预热, 之后靠产品实体变更事件维护.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
@AllArgsConstructor
public class ProductTenantCache {

    private final ReactiveRepository<DeviceProductEntity, String> productRepository;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String get(String productId) {
        return find(productId).orElse(TenantConstants.UNKNOWN_TENANT);
    }

    public Optional<String> find(String productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(productId));
    }

    public boolean belongsTo(String productId, String tenantId) {
        return find(productId).map(tenantId::equals).orElse(false);
    }

    @EventListener
    public void preload(ApplicationReadyEvent event) {
        productRepository
            .createQuery()
            .fetch()
            .doOnNext(this::put)
            .count()
            .subscribe(
                count -> log.info("preloaded {} product-tenant mappings", count),
                error -> log.error("preload product-tenant cache failed", error)
            );
    }

    @EventListener
    public void handleCreated(EntityCreatedEvent<DeviceProductEntity> event) {
        putAll(event.getEntity());
    }

    @EventListener
    public void handleSaved(EntitySavedEvent<DeviceProductEntity> event) {
        putAll(event.getEntity());
    }

    @EventListener
    public void handleModified(EntityModifyEvent<DeviceProductEntity> event) {
        putAll(event.getAfter());
    }

    @EventListener
    public void handleDeleted(EntityDeletedEvent<DeviceProductEntity> event) {
        for (DeviceProductEntity entity : event.getEntity()) {
            if (entity.getId() != null) {
                cache.remove(entity.getId());
            }
        }
    }

    private void putAll(Collection<DeviceProductEntity> entities) {
        for (DeviceProductEntity entity : entities) {
            put(entity);
        }
    }

    private void put(DeviceProductEntity entity) {
        if (!(entity instanceof TenantAware) || entity.getId() == null) {
            return;
        }
        String tenantId = ((TenantAware) entity).getTenantId();
        cache.put(entity.getId(), tenantId == null ? TenantConstants.UNKNOWN_TENANT : tenantId);
    }
}
