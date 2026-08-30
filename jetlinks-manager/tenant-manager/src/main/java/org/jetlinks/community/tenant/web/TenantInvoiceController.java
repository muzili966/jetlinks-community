package org.jetlinks.community.tenant.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.hswebframework.web.crud.service.ReactiveCrudService;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceQueryController;
import org.jetlinks.community.tenant.entity.TenantInvoiceEntity;
import org.jetlinks.community.tenant.service.TenantInvoiceService;
import org.jetlinks.community.tenant.service.request.TenantInvoiceApplyRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * 发票管理: 申请(租户/平台) + 开具/驳回(平台).
 *
 * @author tenant-manager
 * @since 2.11
 */
@RestController
@RequestMapping("/tenant/invoice")
@Authorize
@Resource(id = "tenant", name = "租户管理")
@AllArgsConstructor
@Getter
@Tag(name = "租户发票")
public class TenantInvoiceController implements ReactiveServiceQueryController<TenantInvoiceEntity, String> {

    private final TenantInvoiceService service;

    @Override
    public ReactiveCrudService<TenantInvoiceEntity, String> getService() {
        return service;
    }

    @PostMapping("/_apply")
    @SaveAction
    @Operation(summary = "申请开票(选择同一租户的已支付未开票订单)")
    public Mono<TenantInvoiceEntity> apply(@RequestBody @Valid Mono<TenantInvoiceApplyRequest> request) {
        return request.flatMap(service::apply);
    }

    @PostMapping("/{id}/_issue")
    @SaveAction
    @Operation(summary = "开具发票(填写发票号)")
    public Mono<Void> issue(@PathVariable String id,
                            @RequestBody Mono<Map<String, String>> body) {
        return body.flatMap(map -> service.issue(id, map.get("invoiceNo"), map.get("remark")));
    }

    @PostMapping("/{id}/_void")
    @SaveAction
    @Operation(summary = "红冲发票(已开具的作废, 释放订单可重新申请)")
    public Mono<Void> voidInvoice(@PathVariable String id,
                                  @RequestBody Mono<Map<String, String>> body) {
        return body.flatMap(m -> service.voidInvoice(id, m.get("reason")));
    }

    @PostMapping("/{id}/_reject")
    @SaveAction
    @Operation(summary = "驳回申请(释放订单可重新申请)")
    public Mono<Void> reject(@PathVariable String id,
                             @RequestBody Mono<Map<String, String>> body) {
        return body.flatMap(map -> service.reject(id, map.get("reason")));
    }
}
