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
import org.hswebframework.web.crud.generator.Generators;
import org.jetlinks.community.cs.enums.CsChatMessageType;
import org.jetlinks.community.cs.enums.CsChatSender;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Index;
import javax.persistence.Table;
import java.sql.JDBCType;

/**
 * 会话消息, 只增不改. 文本消息 content 是正文; 图片 / 视频 / 文件消息 content 是文件访问地址.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
@Table(name = "cs_chat_message", indexes = {
    @Index(name = "idx_cs_chat_message_session", columnList = "session_id,create_time")
})
@Comment("客服会话消息表")
@EnableEntityEvent
public class CsChatMessageEntity extends GenericEntity<String> {

    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    @Schema(description = "消息ID(雪花ID)")
    public String getId() {
        return super.getId();
    }

    @Column(name = "session_id", length = 64, nullable = false, updatable = false)
    @Schema(description = "会话ID")
    private String sessionId;

    @Column(length = 16, updatable = false)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("visitor")
    @Schema(description = "发送方")
    private CsChatSender sender;

    @Column(name = "sender_name", length = 64, updatable = false)
    @Schema(description = "发送方名称")
    private String senderName;

    @Column(length = 16, updatable = false)
    @EnumCodec
    @ColumnType(javaType = String.class)
    @DefaultValue("text")
    @Schema(description = "消息类型", defaultValue = "text")
    private CsChatMessageType type;

    @Column(updatable = false)
    @ColumnType(jdbcType = JDBCType.LONGVARCHAR)
    @Schema(description = "文本正文, 或附件访问地址")
    private String content;

    @Column(name = "file_name", length = 256, updatable = false)
    @Schema(description = "附件原始文件名")
    private String fileName;

    @Column(name = "file_size", updatable = false)
    @Schema(description = "附件大小(字节)")
    private Long fileSize;

    @Column(name = "create_time", updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "发送时间(只读)", accessMode = Schema.AccessMode.READ_ONLY)
    private Long createTime;

    public CsChatMessageType typeOrText() {
        return type == null ? CsChatMessageType.text : type;
    }
}
