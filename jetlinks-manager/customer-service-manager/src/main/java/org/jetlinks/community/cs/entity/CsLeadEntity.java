package org.jetlinks.community.cs.entity;

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
import org.hswebframework.web.api.crud.entity.RecordModifierEntity;
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.hswebframework.web.crud.generator.Generators;
import org.jetlinks.community.cs.enums.CsLeadSource;
import org.jetlinks.community.cs.enums.CsLeadState;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.sql.JDBCType;
import java.util.Map;

/**
 * 售前线索: 官网留言按联系方式归并而成, 也可手动录入; 转化后关联租户.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "cs_lead", indexes = {
    @Index(name = "idx_cs_lead_phone", columnList = "phone"),
    @Index(name = "idx_cs_lead_wechat", columnList = "wechat"),
    @Index(name = "idx_cs_lead_state", columnList = "state"),
    @Index(name = "idx_cs_lead_owner", columnList = "owner_id")
})
@Comment("客服线索表")
@EnableEntityEvent
public class CsLeadEntity extends GenericEntity<String> implements RecordCreationEntity, RecordModifierEntity {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    @Schema(description = "线索ID(雪花ID)")
    public String getId() {
        return super.getId();
    }

    @Column(length = 64)
    @NotBlank(message = "称呼不能为空")
    @Size(max = 64)
    @Schema(description = "称呼")
    private String name;

    @Column(length = 32)
    @Schema(description = "手机号(去重键之一, 存归一化后的纯数字)")
    private String phone;

    @Column(length = 64)
    @Schema(description = "微信号(去重键之一)")
    private String wechat;

    @Column(length = 128)
    @Schema(description = "公司")
    private String company;

    @Column
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @Schema(description = "需求摘要(首条留言内容, 可编辑)")
    private String summary;

    @Column(name = "device_scale")
    @Schema(description = "预估设备规模(台)")
    private Integer deviceScale;

    @Column(length = 32)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("website")
    @Schema(description = "来源", defaultValue = "website")
    private CsLeadSource source;

    @Column(name = "source_page", length = 512)
    @Schema(description = "首次留言所在的官网页面")
    private String sourcePage;

    @Column
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @JsonCodec
    @Schema(description = "来源渠道参数(utm_source 等)")
    private Map<String, String> utm;

    @Column(length = 32)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("pending")
    @Schema(description = "状态", defaultValue = "pending")
    private CsLeadState state;

    @Column(name = "state_reason", length = 512)
    @Schema(description = "标记无效等状态变更的原因")
    private String stateReason;

    @Column(name = "owner_id", length = 64)
    @Schema(description = "负责人用户ID")
    private String ownerId;

    @Column(name = "owner_name", length = 64)
    @Schema(description = "负责人姓名(快照)")
    private String ownerName;

    @Column(name = "tenant_id", length = 64)
    @Schema(description = "转化后关联的租户ID")
    private String tenantId;

    @Column(name = "tenant_name", length = 64)
    @Schema(description = "转化后关联的租户名称(快照)")
    private String tenantName;

    @Column(name = "next_follow_at")
    @Schema(description = "下次跟进时间(毫秒时间戳)")
    private Long nextFollowAt;

    @Column(name = "last_follow_at")
    @Schema(description = "最近一次跟进时间")
    private Long lastFollowAt;

    @Column(name = "follow_count")
    @DefaultValue("0")
    @Schema(description = "跟进次数")
    private Integer followCount;

    @Column(name = "message_count")
    @DefaultValue("0")
    @Schema(description = "归入本线索的留言条数")
    private Integer messageCount;

    @Column(name = "last_message_at")
    @Schema(description = "最近一条留言时间")
    private Long lastMessageAt;

    @Column(updatable = false)
    @Schema(description = "创建者ID(只读, 官网留言产生的线索为空)", accessMode = Schema.AccessMode.READ_ONLY)
    private String creatorId;

    @Column(updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "创建时间(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createTime;

    @Column(name = "modifier_id", length = 64)
    @Schema(description = "修改人ID(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private String modifierId;

    @Column(name = "modify_time")
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "修改时间(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long modifyTime;

    public boolean isOwnedBy(String userId) {
        return userId != null && userId.equals(ownerId);
    }

    public boolean hasOwner() {
        return ownerId != null && !ownerId.isBlank();
    }
}
