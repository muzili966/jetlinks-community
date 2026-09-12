# customer-service-manager 客服模块

一期 A：官网留言 → 线索归并 → 认领 / 指派 / 跟进 → 转化为租户，新留言通知客服。
二期：官网在线聊天 + 坐席工作台（自动分配、排队、转接、结束打标签、会话转线索、访客评价）。零侵入挂载，不改上游任何文件。

## 启用与关闭

默认启用。整体关闭（表保留、接口与后台任务停用）：

```yaml
customer-service:
  enabled: false
```

## 配置

```yaml
customer-service:
  agent-role-id: cs-agent            # 客服坐席角色，启动时自动创建
  supervisor-role-id: cs-supervisor  # 客服主管角色，可指派线索
  inbox:
    rate-limit-per-hour: 5           # 同一 IP 每小时最多留言数
    content-max-length: 2000
    retention-days: 365              # 留言原文保留天数，线索不清理
  notify:
    in-app: true                     # 新留言站内通知全部坐席与主管
    type: dingTalk                   # 可选：再推一条到外部渠道（通知管理里配置的通知）
    notifier-id: xxx
    template-id: xxx                 # 模板变量 name / contact / company / content / sourcePage / time
  chat:
    idle-timeout: 30m                # 会话多久没有新消息后由系统自动结束
    idle-check-interval: 1m
    default-max-concurrent: 5        # 坐席未单独设置时的同时接待上限
    session-rate-limit-per-hour: 20  # 同一 IP 每小时最多发起的会话数
    message-rate-limit-per-minute: 30
    message-max-length: 1000
    history-limit: 200
    heartbeat-interval: 25s          # 访客 SSE 心跳，需小于反向代理的空闲超时
```

## 接口

| 路径 | 权限 | 说明 |
| --- | --- | --- |
| `POST /cs/public/inbox` | 匿名 | 官网提交留言。需带 `verifyKey` / `verifyCode`（来自 `GET /authorize/captcha/image`）；同 IP 每小时限 5 次，超限返回 429 |
| `POST /cs/inbox/_query` | `cs-inbox:query` | 留言箱分页 |
| `POST /cs/inbox/_read` | `cs-inbox:save` | 标记已读，body 为 ID 数组 |
| `GET /cs/inbox/_unread-count` | `cs-inbox:query` | 未读数 |
| `POST /cs/lead/_query` 等 | `cs-lead:*` | 线索标准 CRUD |
| `GET /cs/lead/_summary` | `cs-lead:query` | 各状态数量与逾期跟进数 |
| `POST /cs/lead/{id}/_claim` | `cs-lead:save` | 认领 |
| `POST /cs/lead/{id}/_assign` | `cs-lead:save` | 指派（仅主管） |
| `POST /cs/lead/{id}/_state` | `cs-lead:save` | 标记无效 / 重新激活 |
| `POST /cs/lead/{id}/_follow` `GET /cs/lead/{id}/follows` | `cs-lead:save` / `query` | 跟进记录 |
| `POST /cs/lead/{id}/_convert` | `cs-lead:save` | 转化为租户（关联已有或新建），需租户模块启用 |
| `GET /cs/lead/export.xlsx` | `cs-lead:query` | 导出 |

### 在线会话（访客，匿名）

访客凭证 `token` 在发起会话时一次性返回，之后每个请求以 query 参数携带（EventSource 不能带请求头）。

| 路径 | 说明 |
| --- | --- |
| `GET /cs/public/session/_status` | 是否有坐席在线，官网据此决定默认展示聊天还是留言 |
| `POST /cs/public/session` | 发起会话（可带 `firstMessage`）；有空闲坐席直接分配，否则排队。同 IP 每小时限 20 次 |
| `GET /cs/public/session/{id}?token=` | 刷新页面后恢复会话 |
| `GET /cs/public/session/{id}/messages?token=` | 历史消息 |
| `POST /cs/public/session/{id}/message?token=` | 发送消息，每会话每分钟限 30 条 |
| `GET /cs/public/session/{id}/events?token=` | SSE 订阅会话事件（message / accepted / transferred / closed / contact），25s 心跳 |
| `POST /cs/public/session/{id}/_read` `_contact` `_close` `_rate` | 已读、补充联系方式、结束、评价 |

### 在线会话（坐席）

| 路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /cs/session/agent/_me` `POST /cs/session/agent/_status` | `cs-session:query` / `save` | 坐席状态：online / busy / offline 与同时接待上限；只有 online 参与自动分配 |
| `GET /cs/session/agent/_online` | `cs-session:query` | 在线坐席（供转接） |
| `GET /cs/session/_queue` `GET /cs/session/_mine` | `cs-session:query` | 排队中 / 我正在接待的会话 |
| `POST /cs/session/_query` | `cs-session:query` | 会话历史分页 |
| `GET /cs/session/{id}/messages` | `cs-session:query` | 会话消息 |
| `POST /cs/session/{id}/_accept` | `cs-session:save` | 接入排队会话 |
| `POST /cs/session/{id}/message` `_read` | `cs-session:save` | 回复、已读 |
| `POST /cs/session/{id}/_transfer` | `cs-session:save` | 转给其他在线坐席（本人或主管） |
| `POST /cs/session/{id}/_close` | `cs-session:save` | 结束并打标签 |
| `POST /cs/session/{id}/_lead` | `cs-session:save` + `cs-lead:save` | 会话转线索，同一联系方式归入已有线索 |

坐席工作台实时事件走平台 WebSocket `/messaging/{token}`，订阅 topic `/cs/workbench`：收到 `/cs/agent/{自己}`（分配给自己的会话事件）与 `/cs/queue`（排队变化）两类，消息里的 `topic` 字段可区分。

会话状态流：`queued 排队中 → active 接待中 → closed 已结束`，已结束为终态，访客再次咨询新开会话。自动分配取在线且未达上限的坐席里当前接待数最少的一位。

线索状态流：`pending 新线索 → following 跟进中 → converted 已转化 / invalid 无效`，无效可重新激活；已转化为终态。

## 上线步骤

1. 部署后启动会自动建表（`cs_lead`、`cs_lead_follow`、`cs_inbox_message`、`cs_agent`、`cs_session`、`cs_chat_message`）并创建「客服」「客服主管」角色。
2. 「系统管理 → 菜单管理」导入 `docs/menu-cs.json`（或直接执行 `docs/menu-cs.sql`），再到「角色管理」给两个角色勾选客服菜单与按钮；转化为租户还需要 `tenant:save`。
3. 把需要接待的用户绑定到客服角色；主管绑定客服主管角色。
4. 官网 `.env` 配置 `PUBLIC_CS_ENDPOINT` 指向平台 API 地址（浏览器可访问），官网浮窗即可在线聊天与留言。反向代理需允许 SSE（`proxy_buffering off`，读超时大于心跳间隔）。
5. 坐席登录控制台进入「客服中心 → 坐席工作台」，把状态切到「在线」才会分配会话。
6. 生产环境把 `hsweb.cors` 的 `allowed-origins` 从 `*` 收紧为官网域名与控制台域名。

## 已知边界

- 限流按节点内存计数，多节点各自计数；需要全局精确限流时放到网关。
- 外部渠道通知失败只记 warn 日志，不影响留言落库与站内通知。
- 会话事件通过 EventBus 分发，集群部署时依赖平台的 broker 特性跨节点转发；访客 SSE 与坐席 WebSocket 可以落在不同节点。
- 未包含：工单、知识库 / 常用语、坐席统计报表。
