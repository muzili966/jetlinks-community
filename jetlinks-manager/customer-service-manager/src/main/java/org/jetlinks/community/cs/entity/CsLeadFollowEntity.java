package org.jetlinks.community.cs.entity;

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
import org.jetlinks.community.cs.enums.CsFollowChannel;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;
import java.sql.JDBCType;

/**
 * 线索跟进记录: 每次联系客户写一条, 只增不改.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "cs_lead_follow", indexes = {
    @Index(name = "idx_cs_lead_follow_lead", columnList = "lead_id")
})
@Comment("客服线索跟进记录表")
@EnableEntityEvent
public class CsLeadFollowEntity extends GenericEntity<String> implements RecordCreationEntity {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    @Schema(description = "记录ID(雪花ID)")
    public String getId() {
        return super.getId();
    }

    @Column(name = "lead_id", length = 64, nullable = false, updatable = false)
    @Schema(description = "线索ID")
    private String leadId;

    @Column(length = 32)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("phone")
    @Schema(description = "跟进方式")
    private CsFollowChannel channel;

    @Column
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @Schema(description = "跟进内容")
    private String content;

    @Column(name = "next_follow_at")
    @Schema(description = "约定的下次跟进时间")
    private Long nextFollowAt;

    @Column(updatable = false)
    @Schema(description = "跟进人ID(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private String creatorId;

    @Column(name = "creator_name", length = 64, updatable = false)
    @Schema(description = "跟进人姓名(快照)", accessMode = Schema.AccessMode.READ_ONLY)
    private String creatorName;

    @Column(updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "跟进时间(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createTime;
}
