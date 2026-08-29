# tenant-manager 多租户模块

关系数据行级隔离(方案C) + 时序数据按租户物理分表(方案B), 零侵入挂载, 不修改上游模块任何文件.

## 启用

`application.yml`:

```yaml
tenant:
    enabled: true                        # 总开关, 默认false(关闭时与单租户版本行为完全一致)
    platform-admin-role-id: platform-admin  # 平台管理员角色ID(D5)
    time-series-prefix: true             # 时序表名租户前缀(方案B下沉)
```

## 上线步骤

1. 配置 `tenant.enabled=true` 后启动一次: AutoDDL 自动创建 `s_tenant` 并给 25 张隔离表补 `tenant_id` 列
2. 执行 `docs/migration-postgres.sql`: 创建默认租户/platform-admin角色, 存量数据归入 `default` 租户, 建索引
3. 重启, 用 `docs/tenant-leak-scan.sh` 跑越权扫描验证
4. 回滚: `tenant.enabled=false` 重启即可, 多余的列与数据无副作用

## 关键机制(与方案文档 §3/§4 对应)

| 类 | 职责 |
|---|---|
| `interceptor.TenantEventListener` | easy-orm 全局事件: select/update/delete 注入 `tenant_id` 条件, insert/save 写入租户; fail-closed |
| `config.TenantEntityMappingCustomizer` | 25 张隔离表的实体替换为 `ext.*` 租户子类(AutoDDL 走 `getInstanceType` 解析子类) |
| `dimension.TenantDimensionProvider` | 登录时把租户注入 Authentication 维度 |
| `context.TenantContext` | 三态解析: 租户用户 / 平台管理员直通 / 无租户fail-closed |
| `web.TenantImpersonationFilter` | D5: `X-Tenant-Id` 头显式代理 + 审计日志 |
| `metric.TenantThingsDataCustomizer` | 时序表名前缀 `t{tenantId}_...`, 缓存未命中落 `tunknown_` 隔离区 |
| `messaging.TenantMessagingManager` | WebSocket 订阅鉴权装饰器(@Primary), topic 白名单 + 产品归属校验 |
| `quota.TenantDeviceQuotaListener` | 设备数量配额(`s_tenant.quota.maxDeviceCount`) |
| `cache.ProductTenantCache` | productId→tenantId 预热缓存, 产品事件驱动刷新(热路径不查库) |

## 无登录态链路的租户携带(§4.4)

- 设备激活: `ext.TenantDeviceInstanceEntity#toDeviceInfo` 把租户写入 DeviceOperator 配置
- 时序写入/topic鉴权: 经 `ProductTenantCache` 由 productId 反查
- 平台级白名单表(不隔离): s_menu, dev_product_category, dev_protocol(D4), s_plugin_*, s_config,
  alarm_level, s_user_settings, notify_subscriber_provider/channel(D3平台通道), s_object_relation, rule_task_snapshot

## 已知边界(阶段2人工核查清单)

- `QueryHelper` 手写SQL/join 的接口绕过 ORM 事件, 不会自动注入租户条件 —— 靠 leak-scan 逐个发现后显式加条件
- `createLogMetric(thingType)`(allInOne日志模式)无productId, 无法加前缀 —— 保持默认关闭
- 设备ID全局唯一(D1): 建议产品/设备创建统一走雪花ID; 自定义ID撞车时由数据库主键冲突兜底

## 订阅套餐(V2)

套餐**由数据库管理**(`s_tenant_plan` 表), 平台管理员在「租户管理 → 套餐管理」中可调价格/配额/增删档位;
`TenantPlanConstants` 只是首次启动表为空时的初始化种子, 之后一切以数据库为准。

内置三档种子:

| 套餐 | 月价 | 设备 | 产品 | 数据保留 |
|---|---|---|---|---|
| 免费版 free | ¥0 | 10 | 2 | 7天 |
| 标准版 standard | ¥1800 | 1000 | 50 | 90天 |
| 旗舰版 ultimate | ¥3600 | 10000 | 500 | 365天 |

生效配额优先级(`TenantQuotaResolver`): 租户级覆盖(特批) > 套餐配额 > 不限;
未订阅或**订阅到期(subscribeExpireTime)一律按免费版执行**。
设备/产品创建时经 `TenantQuotaListener` 校验, 超额返回 400。

尚未包含(后续扩展): 支付/续费流水、自动续订、`dataRetentionDays` 自动应用到
TimescaleDB retention policy(当前为存量字段, 需运维按租户前缀表配置)。

## 订阅计费(V2.2)

订单流水表 `s_tenant_order`(AutoDDL 自动建表): 每次开通/续费/变更记录价格快照、月数、金额、
支付渠道、生效后到期时间。**订单为财务流水, 只提供查询与开通接口, 无修改/删除**。

- `POST /tenant/order/_subscribe`: 开通/续费。当前为线下收款模式(下单即已支付即时生效);
  微信/支付宝通过 `payChannel` + `pending` 状态预留, 接入后改为回调置 `paid`
- 到期顺延规则(`TenantOrderService.computeExpireAfter`, 单测覆盖):
  未到期续费从原到期时间按日历月顺延(余期不损失); 已到期/首次从当前时间起算(不为过期时段补时)
- 换套餐: 余期直接顺延, 不做折算(规则简单, 对客户有利)
- 免费套餐: 直接切换、不限期、不产生订单
- 前端: 租户列表「续费」「订单」操作 + 顶部「订单流水」总览(金额/类型/状态/凭证备注)

仍属后续扩展: 在线支付回调、发票、到期自动通知(可挂 notify 体系)。

## 发票 / 对账 / 到期提醒(V2.3)

**发票**(`s_tenant_invoice`, AutoDDL 建表):
- `POST /tenant/invoice/_apply`: 选择同一租户的**已支付且未开票**订单申请开票(抬头快照: 抬头/税号/开户行/账号/地址/电话/邮箱, 普票/专票)
- 申请后订单被锁定(`s_tenant_order.invoice_id` 回填), 不可重复申请
- `POST /tenant/invoice/{id}/_issue`: 平台开具(填发票号) ; `/{id}/_reject`: 驳回并释放订单
- 前端: 租户管理顶部「发票管理」(开具/驳回), 订单流水中勾选订单「申请发票」

**对账导出**: `GET /tenant/order/export.xlsx|csv`(按查询条件导出全部订单, 含金额/类型/状态/发票单号/时间), 前端订单流水弹窗内一键导出。

**到期自动通知**(`notice/TenantExpireNotifier`):
- 周期扫描(默认每小时, `tenant.expire-check-interval`)将到期/已到期租户
- 到期前 N 天(默认 7, `tenant.expire-notify-days`)给租户全部用户发**站内通知**(通知中心铃铛)
- 去重: 以到期时间为锚每个订阅周期只发一次(`expire_notified_at`), 续费后窗口自动重置
- 通知渠道为站内信; 如需短信/邮件, 在通知中心订阅 `tenant-expire` 主题后按平台通知配置转发(后续扩展)
