package org.jetlinks.community.tenant.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.hswebframework.web.crud.service.ReactiveCrudService;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceCrudController;
import org.jetlinks.community.tenant.context.TenantContext;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.service.TenantService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 租户管理接口(仅平台管理端使用).
 *
 * @author tenant-manager
 * @since 2.11
 */
@RestController
@RequestMapping("/tenant")
@Authorize
@Resource(id = "tenant", name = "租户管理")
@AllArgsConstructor
@Getter
@Tag(name = "租户管理")
public class TenantController implements ReactiveServiceCrudController<TenantEntity, String> {

    private final TenantService service;

    @Override
    public ReactiveCrudService<TenantEntity, String> getService() {
        return service;
    }

    @PostMapping("/{tenantId}/users/_bind")
    @SaveAction
    @Operation(summary = "绑定用户到租户(全量替换用户原有租户)")
    public Mono<Void> bindUser(@PathVariable String tenantId,
                               @RequestBody Mono<List<String>> userIdList) {
        return userIdList.flatMap(users -> service.bindUser(tenantId, users));
    }

    @PostMapping("/{tenantId}/users/_unbind")
    @SaveAction
    @Operation(summary = "解绑租户用户")
    public Mono<Integer> unbindUser(@PathVariable String tenantId,
                                    @RequestBody Mono<List<String>> userIdList) {
        return userIdList.flatMap(users -> service.unbindUser(tenantId, users));
    }

    @GetMapping("/_current")
    @Authorize(merge = false)
    @Operation(summary = "获取当前用户所属租户")
    public Mono<TenantEntity> currentTenant() {
        return Authentication
            .currentReactive()
            .flatMap(auth -> TenantContext
                .currentTenant(auth)
                .map(service::findById)
                .orElse(Mono.empty()));
    }
}
