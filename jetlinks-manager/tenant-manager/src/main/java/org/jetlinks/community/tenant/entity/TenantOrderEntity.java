package org.jetlinks.community.tenant.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.ezorm.rdb.mapping.annotation.ColumnType;
import org.hswebframework.ezorm.rdb.mapping.annotation.Comment;
import org.hswebframework.ezorm.rdb.mapping.annotation.DefaultValue;
import org.hswebframework.ezorm.rdb.mapping.annotation.EnumCodec;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.hswebframework.web.api.crud.entity.RecordCreationEntity;
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.hswebframework.web.crud.generator.Generators;
import org.jetlinks.community.tenant.TenantAware;
import org.jetlinks.community.tenant.enums.TenantOrderStatus;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * 租户订阅订单流水: 记录每次开通/续费/变更套餐的价格快照与支付状态.
 * <p>
 * 实现 {@link TenantAware}: 租户用户查询自动过滤到本租户;
 * 平台管理员操作时由服务层显式赋值租户归属.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "s_tenant_order", indexes = {
    @Index(name = "idx_tenant_order_tenant", columnList = "tenant_id"),
    @Index(name = "idx_tenant_order_status", columnList = "status")
})
@Comment("租户订阅订单表")
@EnableEntityEvent
public class TenantOrderEntity extends GenericEntity<String> implements RecordCreationEntity, TenantAware {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    @Schema(description = "订单号(雪花ID)")
    public String getId() {
        return super.getId();
    }

    @Column(name = "tenant_id", length = 64, updatable = false)
    @Schema(description = "租户ID")
    private String tenantId;

    @Column(name = "tenant_name", updatable = false)
    @Schema(description = "租户名称(下单时快照)")
    private String tenantName;

    @Column(name = "plan_id", length = 64, updatable = false)
    @Schema(description = "套餐ID")
    private String planId;

    @Column(name = "plan_name", updatable = false)
    @Schema(description = "套餐名称(下单时快照)")
    private String planName;

    @Column(name = "monthly_price", updatable = false)
    @Schema(description = "月价快照(元/月)")
    private Long monthlyPrice;

    @Column(updatable = false)
    @Schema(description = "购买月数")
    private Integer months;

    @Column(name = "total_amount", updatable = false)
    @Schema(description = "订单金额(元)")
    private Long totalAmount;

    @Column(name = "order_type", length = 32, updatable = false)
    @Schema(description = "订单类型: subscribe(首次开通)/renew(续费)/change(变更套餐)")
    private String orderType;

    @Column(length = 32)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("paid")
    @Schema(description = "状态")
    private TenantOrderStatus status;

    @Column(name = "pay_channel", length = 32)
    @Schema(description = "支付渠道: offline(线下)/wechat/alipay(预留)")
    private String payChannel;

    @Column(name = "pay_time")
    @Schema(description = "支付时间")
    private Long payTime;

    @Column(name = "expire_time_after")
    @Schema(description = "本单生效后的订阅到期时间")
    private Long expireTimeAfter;

    @Column(name = "invoice_id", length = 64)
    @Schema(description = "关联发票ID(已申请开票的订单不可重复申请)", accessMode = Schema.AccessMode.READ_ONLY)
    private String invoiceId;

    @Column
    @Schema(description = "备注")
    private String remark;

    @Column(updatable = false)
    @Schema(description = "操作人ID(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private String creatorId;

    @Column(updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "创建时间(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createTime;
}
