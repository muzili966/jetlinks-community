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
import org.hswebframework.web.crud.annotation.EnableEntityEvent;
import org.hswebframework.web.crud.generator.Generators;
import org.jetlinks.community.cs.enums.CsSessionState;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;
import java.sql.JDBCType;
import java.util.List;
import java.util.Map;

/**
 * 访客与坐席的一次在线会话.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "cs_session", indexes = {
    @Index(name = "idx_cs_session_visitor", columnList = "visitor_id"),
    @Index(name = "idx_cs_session_state", columnList = "state"),
    @Index(name = "idx_cs_session_agent", columnList = "agent_id"),
    @Index(name = "idx_cs_session_last_message", columnList = "last_message_at")
})
@Comment("客服在线会话表")
@EnableEntityEvent
public class CsSessionEntity extends GenericEntity<String> {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    @Schema(description = "会话ID(雪花ID)")
    public String getId() {
        return super.getId();
    }

    @Column(name = "visitor_id", length = 64, updatable = false)
    @Schema(description = "访客ID(浏览器本地生成)")
    private String visitorId;

    @Column(name = "visitor_token", length = 64, updatable = false)
    @Schema(description = "访客凭证, 只在创建会话时返回一次", hidden = true)
    private String visitorToken;

    @Column(name = "visitor_name", length = 64)
    @Schema(description = "访客称呼")
    private String visitorName;

    @Column(name = "visitor_contact", length = 64)
    @Schema(description = "访客联系方式(手机或微信)")
    private String visitorContact;

    @Column(length = 32)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("queued")
    @Schema(description = "状态", defaultValue = "queued")
    private CsSessionState state;

    @Column(name = "agent_id", length = 64)
    @Schema(description = "接待坐席用户ID")
    private String agentId;

    @Column(name = "agent_name", length = 64)
    @Schema(description = "接待坐席姓名(快照)")
    private String agentName;

    @Column(name = "source_page", length = 512, updatable = false)
    @Schema(description = "发起会话的页面")
    private String sourcePage;

    @Column(length = 512, updatable = false)
    @Schema(description = "来源站点")
    private String referrer;

    @Column(updatable = false)
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @JsonCodec
    @Schema(description = "来源渠道参数")
    private Map<String, String> utm;

    @Column(name = "client_ip", length = 64, updatable = false)
    @Schema(description = "客户端IP")
    private String clientIp;

    @Column(name = "last_message", length = 512)
    @Schema(description = "最后一条消息摘要")
    private String lastMessage;

    @Column(name = "last_message_at")
    @Schema(description = "最后一条消息时间")
    private Long lastMessageAt;

    @Column(name = "message_count")
    @DefaultValue("0")
    @Schema(description = "消息总数")
    private Integer messageCount;

    @Column(name = "agent_unread")
    @DefaultValue("0")
    @Schema(description = "坐席未读数")
    private Integer agentUnread;

    @Column(name = "visitor_unread")
    @DefaultValue("0")
    @Schema(description = "访客未读数")
    private Integer visitorUnread;

    @Column(name = "queued_at")
    @Schema(description = "进入排队时间")
    private Long queuedAt;

    @Column(name = "accepted_at")
    @Schema(description = "坐席接入时间")
    private Long acceptedAt;

    @Column(name = "closed_at")
    @Schema(description = "结束时间")
    private Long closedAt;

    @Column(name = "closed_by", length = 16)
    @Schema(description = "结束方: visitor / agent / system")
    private String closedBy;

    @Column
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @JsonCodec
    @Schema(description = "结束时打的标签")
    private List<String> tags;

    @Column(name = "lead_id", length = 64)
    @Schema(description = "转出的线索ID")
    private String leadId;

    @Column
    @Schema(description = "访客评分 1-5")
    private Integer rating;

    @Column(name = "rating_comment", length = 512)
    @Schema(description = "评价内容")
    private String ratingComment;

    @Column(name = "create_time", updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "创建时间(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createTime;

    public boolean isClosed() {
        return state == CsSessionState.closed;
    }

    public boolean isHandledBy(String userId) {
        return userId != null && userId.equals(agentId);
    }

    public boolean hasToken(String token) {
        return token != null && token.equals(visitorToken);
    }
}
