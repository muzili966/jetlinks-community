package org.jetlinks.community.pay.entity;

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
import org.jetlinks.community.pay.enums.PayOrderStatus;
import org.jetlinks.community.pay.spi.PayOrderInfo;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * 支付单: 一次收款尝试. 与业务订单解耦, 通过 bizType + bizId 指回业务;
 * 同一笔业务可以有多张支付单(上一张超时关闭后重新发起).
 * <p>
 * 金额以「分」存储, 避免浮点误差. 支付单是财务记录, 不提供修改与删除接口.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "pay_order", indexes = {
    @Index(name = "idx_pay_order_biz", columnList = "biz_type,biz_id"),
    @Index(name = "idx_pay_order_status_expire", columnList = "status,expire_at"),
    @Index(name = "idx_pay_order_owner", columnList = "owner_id")
})
@Comment("支付单表")
@EnableEntityEvent
public class PayOrderEntity extends GenericEntity<String> implements RecordCreationEntity {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    @Schema(description = "支付单号(雪花ID)")
    public String getId() {
        return super.getId();
    }

    @Column(name = "biz_type", length = 64, nullable = false, updatable = false)
    @Schema(description = "业务类型, 如 tenant-subscription")
    private String bizType;

    @Column(name = "biz_id", length = 64, nullable = false, updatable = false)
    @Schema(description = "业务单号")
    private String bizId;

    @Column(length = 256, nullable = false, updatable = false)
    @Schema(description = "付款人看到的商品描述")
    private String subject;

    @Column(nullable = false, updatable = false)
    @Schema(description = "金额(分)")
    private Long amount;

    @Column(length = 8, updatable = false)
    @DefaultValue("CNY")
    @Schema(description = "币种")
    private String currency;

    @Column(length = 16)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("pending")
    @Schema(description = "状态")
    private PayOrderStatus status;

    @Column(length = 32)
    @Schema(description = "支付渠道; 发起支付时写入, 未发起为空")
    private String channel;

    @Column(name = "channel_trade_no", length = 128)
    @Schema(description = "网关交易号 / 线下转账凭证; 收到有效到账通知即写入, 超时关单会跳过有交易号的单")
    private String channelTradeNo;

    @Column(name = "owner_id", length = 64, updatable = false)
    @Schema(description = "应付款方, 由业务定义(租户订阅为租户ID)")
    private String ownerId;

    @Column(name = "expire_at")
    @Schema(description = "过期时间, 过期未付自动关闭")
    private Long expireAt;

    @Column(name = "paid_at")
    @Schema(description = "到账时间")
    private Long paidAt;

    @Column(name = "closed_at")
    @Schema(description = "关闭时间")
    private Long closedAt;

    @Column(name = "close_reason", length = 256)
    @Schema(description = "关闭原因")
    private String closeReason;

    @Column(name = "notify_count")
    @DefaultValue("0")
    @Schema(description = "收到的到账通知次数(含重复)")
    private Integer notifyCount;

    @Column(name = "last_notify_at")
    @Schema(description = "最近一次收到到账通知的时间")
    private Long lastNotifyAt;

    @Column(length = 512)
    @Schema(description = "备注")
    private String remark;

    @Column(name = "creator_id", length = 64, updatable = false)
    @Schema(description = "发起人ID(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private String creatorId;

    @Column(name = "create_time", updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "创建时间(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createTime;

    public boolean isExpiredAt(long now) {
        return expireAt != null && expireAt <= now;
    }

    public PayOrderInfo toInfo() {
        return PayOrderInfo
            .builder()
            .id(getId())
            .bizType(bizType)
            .bizId(bizId)
            .subject(subject)
            .amount(amount == null ? 0 : amount)
            .currency(currency)
            .status(status)
            .channel(channel)
            .channelTradeNo(channelTradeNo)
            .ownerId(ownerId)
            .expireAt(expireAt)
            .paidAt(paidAt)
            .build();
    }
}
