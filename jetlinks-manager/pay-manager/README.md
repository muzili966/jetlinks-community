# pay-manager 支付底座

支付单、渠道扩展点、业务扩展点、到账回调分发。**只管钱**：收到钱之后做什么由业务模块决定。

不依赖任何业务模块，依赖方向是 `业务模块 → pay-manager`。目前接入的业务是租户订阅续费（`tenant-manager` 的 `tenant-subscription`），客服续费卡片通过它发起支付。

## 模型

```
业务订单(如 s_tenant_order)  ←bizType + bizId—  支付单 pay_order  —channel→  支付渠道
                                                   │
                                   pending ──到账──→ paid
                                      └────关闭/超时──→ closed
```

- **支付单与业务订单解耦**：一笔业务可以有多张支付单，上一张超时关闭后可重新发起。
- **金额一律以「分」存 `Long`**。业务侧价格是元时在边界换算一次（`PayAmounts.fenOfYuan`），中途不混用。
- **支付单是财务记录**：不提供修改和删除接口，只有确认到账和关闭。

## 两个扩展点

| 扩展点 | 一个实现对应 | 稳定契约 | 必须遵守 |
| --- | --- | --- | --- |
| `PayChannelProvider` | 一个收款渠道：线下转账、微信支付、支付宝、聚合支付 | `getId()`，写进支付单、回调地址 `/pay/notify/{id}` 和前端 | `parseNotify` 先验签，失败返回 error；凭证从部署配置读，不进源码与日志 |
| `PayBizHandler` | 一种要收钱的业务：租户续费、增购配额、私有化定金 | `getBizType()`，写进支付单 | `onPaid` 必须幂等；`assertPayable` 决定谁能在收银台看到并支付这张单 |

两者都以 Spring Bean 注册，由 `PayChannelRegistry` / `PayBizRegistry` 汇总。**重复 ID 启动即失败**，不会静默覆盖：两个实现抢同一个回调地址，钱进了却记不上账。渠道可按 `pay.channels.<id>.enabled=false` 单独停用。

渠道能力 `PayCapability` 决定核心开放哪些操作：`NOTIFY` 才接受回调，`OFFLINE_CONFIRM` 才允许人工确认到账，`SIMULATE` 才出现沙箱模拟按钮。

## 到账流程

回调和人工确认走同一条路径 `settle`：

1. 渠道验签，得到 `PayNotification`（线下确认由平台构造）。
2. `PaySettleDecision.decide` 按支付单状态、金额、交易号判定。每个分支都有单测：
   - **金额不符**：拒收并告警，已入账的单也先核金额。
   - **失败通知**：只记录。
   - **同交易号重复通知**：直接应答成功。
   - **已入账却来了另一笔交易号**：用户重复付款，应答成功避免网关无限重试，同时告警人工退款。
   - **单已关闭却到账**：保留回执、告警、人工处理。
   - **正常**：入账。
3. 入账先把交易号回执落库（**不进事务**），再在一个事务里做 `where status = pending` 的条件更新并调用 `onPaid` 履约。
4. 事务提交后发事件 `/pay/order/{bizType}/{orderId}/{status}`，客服卡片据此实时变成「已支付」。

**为什么回执不进事务**：履约失败会整体回滚，支付单保持待支付等网关重试。此时交易号仍在库里作为到账证据，超时关单任务会跳过有交易号的单，不会把「钱到了但没履约」的单关掉。支付订单页用感叹号标出这类单。

## 并发与幂等

- 状态变化全部是数据库条件更新，多节点、重复通知都安全，不依赖分布式锁。
- 租户订阅顺延用「到期时间没变才写入」的乐观更新加重试，同一租户两笔订单同时到账不会互相覆盖。
- 超时关单 `PayOrderExpireCloser` 多节点同时跑也只会关一次。

## 安全

- `/pay/notify/{channel}` 是匿名接口，公开原因是调用方为支付网关。防护措施：
  - 渠道必须具备 `NOTIFY` 能力。
  - 渠道验签，失败即拒。
  - 核心再核对金额和状态。
  - 请求体限制 64KB。
  - 失败只记录错误码和来源 IP，不记报文。
- **收银台** `/pay/checkout/{id}` 登录即可访问，每次都调 `assertPayable`。租户用户只能付自己租户的单，别人的单一律返回「不存在」，不给人拿单号试探。
- **金额不接受前端传入**：`PayCreateRequest` 只供服务端业务模块调用，不暴露成接口。
- **沙箱模拟到账**：服务端按网关格式拼好回调、用密钥签名，再交给和真实回调完全相同的验签与入账流程，并去掉当前登录态。密钥不出服务端，浏览器伪造不了到账。

## 配置

```yaml
pay:
  enabled: true
  order-ttl: 24h                  # 支付单有效期
  expire-check-interval: 1m
  notify-base-url: https://api.example.com   # 网关能访问到的平台地址；为空时渠道拿不到回调地址
  channels:
    offline:
      enabled: true
  offline:
    instructions:                 # 线下转账页展示的收款信息，真实账户在部署侧配置
      收款户名: 天马物联科技有限公司
  sandbox:
    enabled: false                # 仅开发联调；开启但未配置密钥时启动失败
    secret: ${PAY_SANDBOX_SECRET}
    notify-max-age: 5m            # 回调时间戳允许偏差，防重放
```

## 接口

| 路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /pay/checkout/{id}` | 登录 + 业务校验 | 支付单、业务明细、可选渠道 |
| `POST /pay/checkout/{id}/_prepare` | 登录 + 业务校验 | 选渠道发起支付，返回下一步动作 `redirect / qrcode / offline / simulate` |
| `POST /pay/checkout/{id}/_simulate` | 登录 + 业务校验 | 沙箱模拟到账，仅沙箱开启时可用 |
| `POST /pay/notify/{channel}` | 匿名 | 网关异步通知，按渠道要求回写应答 |
| `POST /pay/order/_query` | `pay-order:query` | 支付单分页 |
| `GET /pay/order/_channels` | `pay-order:query` | 已启用渠道 |
| `POST /pay/order/{id}/_confirm-offline` | `pay-order:save` | 确认线下到账，凭证号作为交易号 |
| `POST /pay/order/{id}/_close` | `pay-order:save` | 关闭待支付单（有到账回执的不能关） |

## 接入一个新渠道

1. 实现 `PayChannelProvider`，声明能力；`prepare` 调网关预下单并返回动作，`parseNotify` 验签后返回结果，`notifyAck` 按网关要求返回应答。
2. 凭证用 `@ConfigurationProperties` 绑定，由环境变量覆盖。
3. 以 `@Bean` 注册；按需加 `@ConditionalOnProperty` 开关。
4. 在网关后台把回调地址配成 `{notify-base-url}/pay/notify/{渠道ID}`。

核心、收银台、支付订单页都不用改。

## 接入一个新业务

1. 实现 `PayBizHandler`：`assertPayable` 定义谁能付，`onPaid` 幂等履约，`onClosed` 释放占用，`describe` 提供收银台明细。
2. 业务下单时用自己的价目算出金额（分），调 `PayOrderService.create` 生成支付单，把支付单号给付款人（链接到 `/pay/checkout/{id}`）。
3. 需要实时感知状态的模块订阅 `/pay/order/{bizType}/*/*`。

## 上线步骤

1. 部署后自动建表 `pay_order`。
2. 执行 `docs/menu-pay.sql`，重启 jetlinks-api；需要操作支付单的非管理员角色在「角色管理」里勾选「支付订单」。
3. 配置 `pay.offline.instructions` 的真实收款账户与 `pay.notify-base-url`；生产不要打开 `pay.sandbox`。

## 已知边界

- **没有接真实网关**：微信支付、支付宝没有实现，需要商户号与证书；接入按上面「新渠道」步骤进行，核心不用改。
- **没有退款**：租户订单原有的线下退款流程（回退到期时间）照旧可用，但不经过支付单。在线退款需要给渠道加退款能力，并在业务扩展点里增加 `onRefunded`。
- **没有定时对账**：只依赖回调和人工确认。网关支持主动查单时，建议补一个按交易号补单的任务，处理长期待支付但有回执的单。
- **二维码只返回文本**：收银台暂时显示二维码内容供复制，没有渲染成图片。
