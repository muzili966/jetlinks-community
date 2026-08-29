package org.jetlinks.community.tenant.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.ezorm.rdb.mapping.annotation.ColumnType;
import org.hswebframework.ezorm.rdb.mapping.annotation.Comment;
import org.hswebframework.ezorm.rdb.mapping.annotation.DefaultValue;
import org.hswebframework.ezorm.rdb.mapping.annotation.EnumCodec;
import org.hswebframework.ezorm.rdb.mapping.annotation.JsonCodec;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.hswebframework.web.api.crud.entity.RecordCreationEntity;
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.hswebframework.web.crud.generator.Generators;
import org.jetlinks.community.tenant.TenantAware;
import org.jetlinks.community.tenant.enums.TenantInvoiceStatus;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;
import java.sql.JDBCType;
import java.util.List;

/**
 * 发票申请单: 针对已支付订单的开票记录(抬头快照 + 状态流转).
 * 流程: 申请(pending) → 平台开具(issued, 填发票号) / 驳回(rejected, 释放订单可重新申请).
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "s_tenant_invoice", indexes = {
    @Index(name = "idx_tenant_invoice_tenant", columnList = "tenant_id"),
    @Index(name = "idx_tenant_invoice_status", columnList = "status")
})
@Comment("租户发票申请表")
@EnableEntityEvent
public class TenantInvoiceEntity extends GenericEntity<String> implements RecordCreationEntity, TenantAware {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    @Schema(description = "发票申请单号(雪花ID)")
    public String getId() {
        return super.getId();
    }

    @Column(name = "tenant_id", length = 64, updatable = false)
    @Schema(description = "租户ID")
    private String tenantId;

    @Column(name = "tenant_name", updatable = false)
    @Schema(description = "租户名称(快照)")
    private String tenantName;

    @Column(name = "order_id_list", updatable = false)
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @JsonCodec
    @Schema(description = "包含的订单号列表")
    private List<String> orderIdList;

    @Column(name = "total_amount", updatable = false)
    @Schema(description = "开票金额合计(元)")
    private Long totalAmount;

    @Column(name = "invoice_type", length = 32)
    @DefaultValue("normal")
    @Schema(description = "发票类型: normal(增值税普通发票)/special(增值税专用发票)")
    private String invoiceType;

    @Column
    @Schema(description = "发票抬头")
    private String title;

    @Column(name = "tax_no", length = 64)
    @Schema(description = "纳税人识别号")
    private String taxNo;

    @Column(name = "bank_name")
    @Schema(description = "开户银行")
    private String bankName;

    @Column(name = "bank_account", length = 64)
    @Schema(description = "银行账号")
    private String bankAccount;

    @Column
    @Schema(description = "注册地址")
    private String address;

    @Column(length = 32)
    @Schema(description = "注册电话")
    private String phone;

    @Column(length = 128)
    @Schema(description = "接收邮箱(电子发票)")
    private String email;

    @Column(length = 32)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("pending")
    @Schema(description = "状态")
    private TenantInvoiceStatus status;

    @Column(name = "invoice_no", length = 64)
    @Schema(description = "发票号码(开具后填写)")
    private String invoiceNo;

    @Column(name = "reject_reason")
    @Schema(description = "驳回原因")
    private String rejectReason;

    @Column(name = "issue_time")
    @Schema(description = "开具时间")
    private Long issueTime;

    @Column
    @Schema(description = "备注")
    private String remark;

    @Column(updatable = false)
    @Schema(description = "申请人ID(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private String creatorId;

    @Column(updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "申请时间(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createTime;
}
