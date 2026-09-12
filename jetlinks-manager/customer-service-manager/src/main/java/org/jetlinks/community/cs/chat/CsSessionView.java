package org.jetlinks.community.cs.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.cs.enums.CsSessionState;

/**
 * 对访客暴露的会话视图: 不含坐席内部字段, 凭证只在创建时附带.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsSessionView {

    @Schema(description = "会话ID")
    private String id;

    @Schema(description = "访客凭证, 仅创建会话时返回, 后续请求用 token 参数携带")
    private String token;

    @Schema(description = "状态")
    private CsSessionState state;

    @Schema(description = "接待坐席用户ID")
    private String agentId;

    @Schema(description = "接待坐席姓名")
    private String agentName;

    @Schema(description = "访客联系方式")
    private String visitorContact;

    @Schema(description = "所属租户名称")
    private String tenantName;

    @Schema(description = "访客称呼")
    private String visitorName;

    @Schema(description = "访客未读数")
    private Integer visitorUnread;

    @Schema(description = "坐席未读数")
    private Integer agentUnread;

    @Schema(description = "最后一条消息摘要")
    private String lastMessage;

    @Schema(description = "最后一条消息时间")
    private Long lastMessageAt;

    @Schema(description = "结束方")
    private String closedBy;

    @Schema(description = "当前是否有坐席在线; 无人在线时前端引导访客改为留言")
    private Boolean agentsOnline;

    public static CsSessionView of(CsSessionEntity entity) {
        CsSessionView view = new CsSessionView();
        view.setId(entity.getId());
        view.setState(entity.getState());
        view.setAgentId(entity.getAgentId());
        view.setAgentName(entity.getAgentName());
        view.setVisitorName(entity.getVisitorName());
        view.setVisitorContact(entity.getVisitorContact());
        view.setTenantName(entity.getTenantName());
        view.setVisitorUnread(entity.getVisitorUnread());
        view.setAgentUnread(entity.getAgentUnread());
        view.setLastMessage(entity.getLastMessage());
        view.setLastMessageAt(entity.getLastMessageAt());
        view.setClosedBy(entity.getClosedBy());
        return view;
    }

    public CsSessionView withToken(String token) {
        this.token = token;
        return this;
    }

    public CsSessionView withAgentsOnline(boolean online) {
        this.agentsOnline = online;
        return this;
    }
}
