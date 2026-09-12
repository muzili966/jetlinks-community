package org.jetlinks.community.cs.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.ezorm.rdb.mapping.annotation.ColumnType;
import org.hswebframework.ezorm.rdb.mapping.annotation.Comment;
import org.hswebframework.ezorm.rdb.mapping.annotation.DefaultValue;
import org.hswebframework.ezorm.rdb.mapping.annotation.JsonCodec;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.hswebframework.web.crud.generator.Generators;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;
import java.sql.JDBCType;
import java.util.List;
import java.util.Map;

/**
 * 官网留言原文: 每次提交一条, 归入线索后保留访客侧的上下文(来源页、浏览轨迹、UA).
 * 留言按保留期自动清理, 线索不清理.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "cs_inbox_message", indexes = {
    @Index(name = "idx_cs_inbox_lead", columnList = "lead_id"),
    @Index(name = "idx_cs_inbox_read", columnList = "read_flag"),
    @Index(name = "idx_cs_inbox_create_time", columnList = "create_time")
})
@Comment("客服官网留言表")
@EnableEntityEvent
public class CsInboxMessageEntity extends GenericEntity<String> {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    @Schema(description = "留言ID(雪花ID)")
    public String getId() {
        return super.getId();
    }

    @Column(name = "lead_id", length = 64, updatable = false)
    @Schema(description = "归入的线索ID")
    private String leadId;

    @Column(name = "visitor_id", length = 64, updatable = false)
    @Schema(description = "官网访客ID(浏览器本地生成)")
    private String visitorId;

    @Column(length = 64, updatable = false)
    @Schema(description = "称呼")
    private String name;

    @Column(length = 32, updatable = false)
    @Schema(description = "手机号(归一化)")
    private String phone;

    @Column(length = 64, updatable = false)
    @Schema(description = "微信号")
    private String wechat;

    @Column(length = 128, updatable = false)
    @Schema(description = "公司")
    private String company;

    @Column(updatable = false)
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @Schema(description = "留言内容")
    private String content;

    @Column(name = "source_page", length = 512, updatable = false)
    @Schema(description = "留言所在页面")
    private String sourcePage;

    @Column(length = 512, updatable = false)
    @Schema(description = "来源站点(referrer)")
    private String referrer;

    @Column(updatable = false)
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @JsonCodec
    @Schema(description = "来源渠道参数")
    private Map<String, String> utm;

    @Column(updatable = false)
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @JsonCodec
    @Schema(description = "访客浏览轨迹, 每项含 path 与 at(毫秒时间戳)")
    private List<Map<String, Object>> trail;

    @Column(name = "client_ip", length = 64, updatable = false)
    @Schema(description = "客户端IP")
    private String clientIp;

    @Column(name = "user_agent", length = 512, updatable = false)
    @Schema(description = "浏览器标识")
    private String userAgent;

    @Column(name = "read_flag")
    @DefaultValue("false")
    @Schema(description = "是否已读")
    private Boolean readFlag;

    @Column(name = "read_at")
    @Schema(description = "已读时间")
    private Long readAt;

    @Column(name = "create_time", updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "提交时间(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createTime;
}
