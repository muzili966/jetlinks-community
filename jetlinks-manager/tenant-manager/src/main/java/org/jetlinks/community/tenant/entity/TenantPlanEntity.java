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
import org.hswebframework.web.validator.CreateGroup;
import org.jetlinks.community.tenant.enums.TenantState;

import javax.persistence.Column;
import javax.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.sql.JDBCType;
import java.util.Map;

/**
 * 订阅套餐: 价格 + 配额模板. 内置免费版/标准版/旗舰版三档,
 * 租户通过 {@code planId} 订阅, 生效配额见 TenantQuotaResolver.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "s_tenant_plan")
@Comment("租户订阅套餐表")
@EnableEntityEvent
public class TenantPlanEntity extends GenericEntity<String> implements RecordCreationEntity, QuotaHolder {

    @Override
    @Pattern(regexp = "^[0-9a-zA-Z_\\-]+$", message = "ID只能由数字,字母,下划线和中划线组成", groups = CreateGroup.class)
    @Schema(description = "套餐ID")
    public String getId() {
        return super.getId();
    }

    @Column
    @NotBlank(message = "套餐名称不能为空", groups = CreateGroup.class)
    @Schema(description = "套餐名称")
    private String name;

    @Column(name = "monthly_price")
    @DefaultValue("0")
    @Schema(description = "月价(元/月)")
    private Long monthlyPrice;

    @Column
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @JsonCodec
    @Schema(description = "配额模板,如: {\"maxDeviceCount\":1000,\"maxProductCount\":50,\"dataRetentionDays\":90}")
    private Map<String, Object> quota;

    @Column(name = "sort_index")
    @DefaultValue("0")
    @Schema(description = "排序号")
    private Integer sortIndex;

    @Column(length = 32)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("enabled")
    @Schema(description = "状态", defaultValue = "enabled")
    private TenantState state;

    @Column
    @Schema(description = "说明")
    private String describe;

    @Column(updatable = false)
    @Schema(description = "创建者ID(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private String creatorId;

    @Column(updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "创建时间(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createTime;
}
