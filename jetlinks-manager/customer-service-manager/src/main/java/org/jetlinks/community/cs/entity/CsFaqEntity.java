package org.jetlinks.community.cs.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.ezorm.rdb.mapping.annotation.ColumnType;
import org.hswebframework.ezorm.rdb.mapping.annotation.Comment;
import org.hswebframework.ezorm.rdb.mapping.annotation.DefaultValue;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.hswebframework.web.api.crud.entity.RecordCreationEntity;
import org.hswebframework.web.api.crud.entity.RecordModifierEntity;
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.hswebframework.web.crud.generator.Generators;
import org.hswebframework.web.validator.CreateGroup;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;
import java.sql.JDBCType;

/**
 * 常见问题: 官网聊天窗里访客点一下就能看到答案, 坐席工作台里也作为常用回复.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "cs_faq", indexes = {
    @Index(name = "idx_cs_faq_enabled_sort", columnList = "enabled,sort_index")
})
@Comment("客服常见问题表")
@EnableEntityEvent
public class CsFaqEntity extends GenericEntity<String> implements RecordCreationEntity, RecordModifierEntity {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    @Schema(description = "ID")
    public String getId() {
        return super.getId();
    }

    @Column(length = 256, nullable = false)
    @NotBlank(message = "问题不能为空", groups = CreateGroup.class)
    @Schema(description = "问题")
    private String question;

    @Column(nullable = false)
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @NotBlank(message = "答案不能为空", groups = CreateGroup.class)
    @Schema(description = "答案(纯文本, 支持换行)")
    private String answer;

    @Column(length = 64)
    @Schema(description = "分类, 如 接入 / 套餐 / 部署")
    private String category;

    @Column(name = "sort_index")
    @DefaultValue("0")
    @Schema(description = "排序, 越小越靠前")
    private Integer sortIndex;

    @Column
    @DefaultValue("true")
    @Schema(description = "是否启用; 只有启用的问题会出现在官网")
    private Boolean enabled;

    @Column
    @DefaultValue("0")
    @Schema(description = "官网点击次数", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer hits;

    @Column(name = "creator_id", updatable = false)
    @Schema(description = "创建人ID", accessMode = Schema.AccessMode.READ_ONLY)
    private String creatorId;

    @Column(name = "creator_name", updatable = false, length = 64)
    @Schema(description = "创建人", accessMode = Schema.AccessMode.READ_ONLY)
    private String creatorName;

    @Column(name = "create_time", updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createTime;

    @Column(name = "modifier_id")
    @Schema(description = "修改人ID", accessMode = Schema.AccessMode.READ_ONLY)
    private String modifierId;

    @Column(name = "modifier_name", length = 64)
    @Schema(description = "修改人", accessMode = Schema.AccessMode.READ_ONLY)
    private String modifierName;

    @Column(name = "modify_time")
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "修改时间", accessMode = Schema.AccessMode.READ_ONLY)
    private Long modifyTime;

    public boolean isEnabledOrDefault() {
        return enabled == null || enabled;
    }
}
