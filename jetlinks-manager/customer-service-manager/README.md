# customer-service-manager 客服模块

一期 A：官网留言 → 线索归并 → 认领 / 指派 / 跟进 → 转化为租户，新留言通知客服。零侵入挂载，不改上游任何文件。

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

线索状态流：`pending 新线索 → following 跟进中 → converted 已转化 / invalid 无效`，无效可重新激活；已转化为终态。

## 上线步骤

1. 部署后启动会自动建表（`cs_lead`、`cs_lead_follow`、`cs_inbox_message`）并创建「客服」「客服主管」角色。
2. 「系统管理 → 菜单管理」导入 `docs/menu-cs.json`，再到「角色管理」给两个角色勾选客服菜单与按钮；转化为租户还需要 `tenant:save`。
3. 把需要接待的用户绑定到客服角色；主管绑定客服主管角色。
4. 官网 `.env` 配置 `PUBLIC_CS_ENDPOINT` 指向平台 API 地址（浏览器可访问），官网浮窗即可提交留言。
5. 生产环境把 `hsweb.cors` 的 `allowed-origins` 从 `*` 收紧为官网域名与控制台域名。

## 已知边界

- 限流按节点内存计数，多节点各自计数；需要全局精确限流时放到网关。
- 外部渠道通知失败只记 warn 日志，不影响留言落库与站内通知。
- 二期（坐席工作台、实时会话）未包含，数据模型见设计文档。
