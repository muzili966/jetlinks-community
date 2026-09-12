package org.jetlinks.community.cs.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.reactor.excel.ReactorExcel;
import org.hswebframework.reactor.excel.WriterOperator;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.hswebframework.web.crud.service.ReactiveCrudService;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceCrudController;
import org.jetlinks.community.cs.CsConstants;
import org.jetlinks.community.cs.entity.CsLeadEntity;
import org.jetlinks.community.cs.entity.CsLeadFollowEntity;
import org.jetlinks.community.cs.service.CsLeadService;
import org.jetlinks.community.cs.service.request.CsAssignRequest;
import org.jetlinks.community.cs.service.request.CsConvertRequest;
import org.jetlinks.community.cs.service.request.CsFollowRequest;
import org.jetlinks.community.cs.service.request.CsStateRequest;
import org.jetlinks.community.cs.web.response.CsLeadSummary;
import org.jetlinks.community.tenant.web.ExcelExportSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 线索管理(客服端).
 *
 * @author customer-service-manager
 * @since 2.11
 */
@ConditionalOnProperty(prefix = "customer-service", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/cs/lead")
@Authorize
@Resource(id = CsConstants.RESOURCE_LEAD, name = "客服线索")
@AllArgsConstructor
@Getter
@Tag(name = "客服线索")
public class CsLeadController implements ReactiveServiceCrudController<CsLeadEntity, String> {

    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CsLeadService service;

    @Override
    public ReactiveCrudService<CsLeadEntity, String> getService() {
        return service;
    }

    @GetMapping("/_summary")
    @QueryAction
    @Operation(summary = "线索看板汇总(各状态数量、逾期跟进数)")
    public Mono<CsLeadSummary> summary() {
        return service.summary();
    }

    @PostMapping("/{id}/_claim")
    @SaveAction
    @Operation(summary = "认领线索(无负责人或本人负责时可认领; 主管可改派给自己)")
    public Mono<CsLeadEntity> claim(@PathVariable String id) {
        return service.claim(id);
    }

    @PostMapping("/{id}/_assign")
    @SaveAction
    @Operation(summary = "指派负责人(仅客服主管)")
    public Mono<CsLeadEntity> assign(@PathVariable String id,
                                     @RequestBody @Valid Mono<CsAssignRequest> request) {
        return request.flatMap(body -> service.assign(id, body.getOwnerId()));
    }

    @PostMapping("/{id}/_state")
    @SaveAction
    @Operation(summary = "变更状态(标记无效 / 重新激活)")
    public Mono<Void> changeState(@PathVariable String id,
                                  @RequestBody @Valid Mono<CsStateRequest> request) {
        return request.flatMap(body -> service.changeState(id, body.getState(), body.getReason()));
    }

    @PostMapping("/{id}/_follow")
    @SaveAction
    @Operation(summary = "新增跟进记录")
    public Mono<CsLeadFollowEntity> follow(@PathVariable String id,
                                           @RequestBody @Valid Mono<CsFollowRequest> request) {
        return request.flatMap(body -> service.addFollow(id, body));
    }

    @GetMapping("/{id}/follows")
    @QueryAction
    @Operation(summary = "跟进记录(按时间倒序)")
    public Flux<CsLeadFollowEntity> follows(@PathVariable String id) {
        return service.queryFollows(id);
    }

    @PostMapping("/{id}/_convert")
    @SaveAction
    @Operation(summary = "转化为租户(关联已有租户或新建租户)")
    public Mono<CsLeadEntity> convert(@PathVariable String id,
                                      @RequestBody @Valid Mono<CsConvertRequest> request) {
        return request.flatMap(body -> service.convert(id, body));
    }

    @GetMapping("/export.{format}")
    @QueryAction
    @Operation(summary = "导出线索(xlsx/csv, 按当前查询条件导出全部)")
    public Mono<Void> export(ServerHttpResponse response,
                             @PathVariable @Parameter(description = "文件格式: xlsx 或 csv") String format,
                             @Parameter(hidden = true) QueryParamEntity query) {
        response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=" + URLEncoder.encode("cs-leads." + format, StandardCharsets.UTF_8));
        query.setPaging(false);
        WriterOperator<CsLeadEntity> writer = ReactorExcel
            .<CsLeadEntity>writer(format)
            .header("name", "称呼")
            .header("phone", "手机号")
            .header("wechat", "微信号")
            .header("company", "公司")
            .header("summary", "需求摘要")
            .header("deviceScale", "预估设备规模")
            .header("source", "来源")
            .header("state", "状态")
            .header("ownerName", "负责人")
            .header("tenantName", "转化租户")
            .header("followCount", "跟进次数")
            .header("lastFollowAt", "最近跟进")
            .header("nextFollowAt", "下次跟进")
            .header("createTime", "创建时间")
            .converter(this::toExportRow);
        return ExcelExportSupport
            .writeAll(writer, service.query(query))
            .flatMap(bytes -> response.writeWith(Mono.just(response.bufferFactory().wrap(bytes))));
    }

    private Map<String, Object> toExportRow(CsLeadEntity lead) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", lead.getName());
        row.put("phone", lead.getPhone());
        row.put("wechat", lead.getWechat());
        row.put("company", lead.getCompany());
        row.put("summary", lead.getSummary());
        row.put("deviceScale", lead.getDeviceScale());
        row.put("source", lead.getSource() == null ? "" : lead.getSource().getText());
        row.put("state", lead.getState() == null ? "" : lead.getState().getText());
        row.put("ownerName", lead.getOwnerName());
        row.put("tenantName", lead.getTenantName());
        row.put("followCount", lead.getFollowCount());
        row.put("lastFollowAt", formatTime(lead.getLastFollowAt()));
        row.put("nextFollowAt", formatTime(lead.getNextFollowAt()));
        row.put("createTime", formatTime(lead.getCreateTime()));
        return row;
    }

    private static String formatTime(Long time) {
        if (time == null) {
            return "";
        }
        return EXPORT_TIME_FORMAT.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }
}
