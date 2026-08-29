package org.jetlinks.community.tenant.service;

import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.crud.service.GenericReactiveCacheSupportCrudService;
import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantPlanConstants;
import org.jetlinks.community.tenant.entity.TenantPlanEntity;
import org.jetlinks.community.tenant.enums.TenantState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 订阅套餐管理: 数据库维护(s_tenant_plan), 平台管理员可调价格/配额/增删档位;
 * 首次启动表为空时写入三档内置种子(免费版/标准版/旗舰版), 之后不再覆盖.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Slf4j
public class TenantPlanService extends GenericReactiveCacheSupportCrudService<TenantPlanEntity, String> {

    @EventListener
    public void initBuiltinPlans(ApplicationReadyEvent event) {
        createQuery()
            .count()
            .filter(count -> count == 0)
            .flatMapMany(ignore -> save(Flux.fromIterable(builtinPlans())))
            .subscribe(
                result -> log.info("initialized builtin tenant plans: {}", result.getTotal()),
                error -> log.error("initialize builtin tenant plans failed", error)
            );
    }

    private java.util.List<TenantPlanEntity> builtinPlans() {
        return Arrays.asList(
            plan(TenantPlanConstants.PLAN_FREE, "免费版", TenantPlanConstants.PRICE_FREE, 0,
                 quota(10L, 2L, 7L), "体验用途: 10设备/2产品/数据保留7天"),
            plan(TenantPlanConstants.PLAN_STANDARD, "标准版", TenantPlanConstants.PRICE_STANDARD, 1,
                 quota(1000L, 50L, 90L), "中小规模: 1000设备/50产品/数据保留90天"),
            plan(TenantPlanConstants.PLAN_ULTIMATE, "旗舰版", TenantPlanConstants.PRICE_ULTIMATE, 2,
                 quota(10000L, 500L, 365L), "生产规模: 10000设备/500产品/数据保留365天")
        );
    }

    private TenantPlanEntity plan(String id,
                                  String name,
                                  long monthlyPrice,
                                  int sortIndex,
                                  Map<String, Object> quota,
                                  String describe) {
        TenantPlanEntity entity = new TenantPlanEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setMonthlyPrice(monthlyPrice);
        entity.setSortIndex(sortIndex);
        entity.setQuota(quota);
        entity.setDescribe(describe);
        entity.setState(TenantState.enabled);
        return entity;
    }

    private Map<String, Object> quota(Long maxDevice, Long maxProduct, Long retentionDays) {
        Map<String, Object> quota = new HashMap<>();
        quota.put(TenantConstants.QUOTA_MAX_DEVICE, maxDevice);
        quota.put(TenantPlanConstants.QUOTA_MAX_PRODUCT, maxProduct);
        quota.put(TenantPlanConstants.QUOTA_DATA_RETENTION_DAYS, retentionDays);
        return quota;
    }
}
