package org.jetlinks.community.cs.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.ezorm.rdb.mapping.annotation.ColumnType;
import org.hswebframework.ezorm.rdb.mapping.annotation.Comment;
import org.hswebframework.ezorm.rdb.mapping.annotation.DefaultValue;
import org.hswebframework.ezorm.rdb.mapping.annotation.EnumCodec;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.jetlinks.community.cs.enums.CsAgentStatus;

import javax.persistence.Column;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * 坐席: 主键即平台用户ID, 第一次上线时自动创建.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "cs_agent", indexes = {
    @Index(name = "idx_cs_agent_status", columnList = "status")
})
@Comment("客服坐席表")
@EnableEntityEvent
public class CsAgentEntity extends GenericEntity<String> {

    @Column(length = 64)
    @Schema(description = "坐席姓名(快照)")
    private String name;

    @Column(length = 32)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("offline")
    @Schema(description = "状态", defaultValue = "offline")
    private CsAgentStatus status;

    @Column(name = "max_concurrent")
    @DefaultValue("5")
    @Schema(description = "同时接待上限")
    private Integer maxConcurrent;

    @Column(name = "last_active_at")
    @Schema(description = "最近活跃时间")
    private Long lastActiveAt;

    public boolean isOnline() {
        return status == CsAgentStatus.online;
    }

    public int maxConcurrentOrDefault(int fallback) {
        return maxConcurrent == null || maxConcurrent <= 0 ? fallback : maxConcurrent;
    }
}
