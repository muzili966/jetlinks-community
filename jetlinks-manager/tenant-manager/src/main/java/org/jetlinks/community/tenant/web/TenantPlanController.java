package org.jetlinks.community.tenant.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.crud.service.ReactiveCrudService;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceCrudController;
import org.jetlinks.community.tenant.entity.TenantPlanEntity;
import org.jetlinks.community.tenant.enums.TenantState;
import org.jetlinks.community.tenant.service.TenantPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 订阅套餐管理接口: 套餐由数据库维护, 平台管理员可调价格/配额/增删档位.
 *
 * @author tenant-manager
 * @since 2.11
 */
@RestController
@RequestMapping("/tenant/plan")
@Authorize
@Resource(id = "tenant", name = "租户管理")
@AllArgsConstructor
@Getter
@Tag(name = "租户订阅套餐")
public class TenantPlanController implements ReactiveServiceCrudController<TenantPlanEntity, String> {

    private final TenantPlanService service;

    @Override
    public ReactiveCrudService<TenantPlanEntity, String> getService() {
        return service;
    }

    @GetMapping("/_enabled")
    @QueryAction
    @Operation(summary = "查询启用中的套餐(按排序号)")
    public Flux<TenantPlanEntity> enabledPlans() {
        return QueryParamEntity
            .newQuery()
            .where(TenantPlanEntity::getState, TenantState.enabled)
            .orderByAsc(TenantPlanEntity::getSortIndex)
            .execute(service::query);
    }
}
