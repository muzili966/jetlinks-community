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
import org.hswebframework.web.authorization.Dimension;
import org.hswebframework.web.authorization.simple.SimpleDimension;
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.hswebframework.web.crud.generator.Generators;
import org.hswebframework.web.validator.CreateGroup;
import org.jetlinks.community.tenant.TenantDimensionType;
import org.jetlinks.community.tenant.enums.TenantState;

import javax.persistence.Column;
import javax.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.sql.JDBCType;
import java.util.Collections;
import java.util.Map;

/**
 * 租户.
 *
 * @author tenant-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "s_tenant")
@Comment("租户信息表")
@EnableEntityEvent
public class TenantEntity extends GenericEntity<String> implements RecordCreationEntity, QuotaHolder {

    @Override
    @Pattern(regexp = "^[0-9a-zA-Z_\\-]+$", message = "ID只能由数字,字母,下划线和中划线组成", groups = CreateGroup.class)
    @Schema(description = "租户ID(只能由数字,字母,下划线和中划线组成)")
    public String getId() {
        return super.getId();
    }

    @Column
    @NotBlank(message = "租户名称不能为空", groups = CreateGroup.class)
    @Schema(description = "租户名称")
    private String name;

    @Column
    @Schema(description = "说明")
    private String describe;

    @Column(length = 32)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("enabled")
    @Schema(description = "状态", defaultValue = "enabled")
    private TenantState state;

    @Column
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @JsonCodec
    @Schema(description = "租户级配额覆盖(留空按套餐),如: {\"maxDeviceCount\":1000}")
    private Map<String, Object> quota;

    @Column(name = "plan_id", length = 64)
    @Schema(description = "订阅套餐ID(免费版/标准版/旗舰版等)")
    private String planId;

    @Column(name = "subscribe_expire_time")
    @Schema(description = "订阅到期时间(毫秒时间戳), 到期后按免费版配额执行; 留空不限期")
    private Long subscribeExpireTime;

    @Column(name = "expire_notified_at")
    @Schema(description = "本订阅周期到期提醒发送时间(内部去重用)", hidden = true)
    private Long expireNotifiedAt;

    @Column(updatable = false)
    @Schema(description = "创建者ID(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private String creatorId;

    @Column(updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "创建时间(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createTime;

    public Dimension toDimension() {
        return SimpleDimension.of(getId(), getName(), TenantDimensionType.tenant, Collections.emptyMap());
    }

    /**
     * 订阅是否已到期(到期后按免费版配额执行)
     */
    public boolean isSubscribeExpired() {
        return subscribeExpireTime != null && subscribeExpireTime < System.currentTimeMillis();
    }
}
