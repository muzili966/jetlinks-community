package org.jetlinks.community.tenant.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.reactor.excel.ReactorExcel;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.hswebframework.web.crud.service.ReactiveCrudService;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceQueryController;
import org.jetlinks.community.tenant.entity.TenantOrderEntity;
import org.jetlinks.community.tenant.service.TenantOrderService;
import org.jetlinks.community.tenant.service.request.TenantSubscribeRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 租户订阅订单: 流水查询(只读) + 开通/续费.
 * 订单为财务流水, 不提供修改/删除接口.
 *
 * @author tenant-manager
 * @since 2.11
 */
@RestController
@RequestMapping("/tenant/order")
@Authorize
@Resource(id = "tenant", name = "租户管理")
@AllArgsConstructor
@Getter
@Tag(name = "租户订阅订单")
public class TenantOrderController implements ReactiveServiceQueryController<TenantOrderEntity, String> {

    private final TenantOrderService service;

    @Override
    public ReactiveCrudService<TenantOrderEntity, String> getService() {
        return service;
    }

    @PostMapping("/_subscribe")
    @SaveAction
    @Operation(summary = "开通/续费套餐(线下收款即时生效, 免费套餐直接切换不产生订单)")
    public Mono<TenantOrderEntity> subscribe(@RequestBody @Valid Mono<TenantSubscribeRequest> request) {
        return request.flatMap(service::subscribe);
    }

    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/export.{format}")
    @QueryAction
    @Operation(summary = "对账导出(xlsx/csv, 按当前查询条件导出全部订单)")
    public Mono<Void> export(ServerHttpResponse response,
                             @PathVariable @Parameter(description = "文件格式: xlsx 或 csv") String format,
                             @Parameter(hidden = true) QueryParamEntity query) {
        response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=" + URLEncoder.encode("tenant-orders." + format, StandardCharsets.UTF_8));
        query.setPaging(false);
        return ReactorExcel
            .<TenantOrderEntity>writer(format)
            .header("id", "订单号")
            .header("tenantName", "租户")
            .header("planName", "套餐")
            .header("months", "月数")
            .header("totalAmount", "金额(元)")
            .header("orderType", "类型")
            .header("status", "状态")
            .header("payChannel", "支付渠道")
            .header("invoiceId", "发票单号")
            .header("expireTimeAfter", "生效后到期")
            .header("createTime", "下单时间")
            .header("remark", "备注")
            .converter(this::toExportRow)
            .writeBuffer(service.query(query), 512)
            .map(response.bufferFactory()::wrap)
            .as(response::writeWith);
    }

    private Map<String, Object> toExportRow(TenantOrderEntity order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("tenantName", order.getTenantName());
        row.put("planName", order.getPlanName());
        row.put("months", order.getMonths());
        row.put("totalAmount", order.getTotalAmount());
        row.put("orderType", order.getOrderType());
        row.put("status", order.getStatus() == null ? "" : order.getStatus().getText());
        row.put("payChannel", order.getPayChannel());
        row.put("invoiceId", order.getInvoiceId());
        row.put("expireTimeAfter", formatTime(order.getExpireTimeAfter()));
        row.put("createTime", formatTime(order.getCreateTime()));
        row.put("remark", order.getRemark());
        return row;
    }

    private String formatTime(Long time) {
        if (time == null) {
            return "";
        }
        return EXPORT_TIME_FORMAT.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }
}
