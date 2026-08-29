# JetLinks 部署说明

参考 wxxpay devops 流程编写。JetLinks 是**单体应用**（非微服务），因此不引入
Nacos / XXL-Job / 服务依赖传播，Jenkinsfile 自包含、不依赖 `wxxpay-ci` Shared Library。

## 端口规划（与 wxxpay 完全错开）

| 用途 | dev | test | 说明 |
|---|---|---|---|
| 平台 API | `8858` | `8868` | 容器内固定 8848；**宿主机 8848 被 Nacos 占用**，故对外错开 |
| 管理前端 | `3200` | `3210` | wxxpay 前端占 3000/3001/3100-3102 |
| MQTT 设备接入 | `1883-1890` | `1893-1900` | 预留区间，在平台「网络组件」中分配 |
| TCP/HTTP 设备接入 | `8800-8810` | `8820-8830` | 预留区间 |

外部占用（勿侵入）：Nacos `8848`；
wxxpay `3000` `3001` `3100-3102` / `8000` `8081-8108` / `10091-10107`。
新增 JetLinks 设备接入端口时，从 `1883-1890`、`8800-8810` 区间内取，并同步修改
compose 的 `ports` 与 `network.resources[*]`——两者必须一致，否则平台分配了端口但容器没映射。

## 依赖（均使用宿主机已有实例，不在 compose 内）

- **PostgreSQL**：必须已安装 `timescaledb` 扩展
  ```sql
  CREATE DATABASE jetlinks;
  \c jetlinks
  CREATE EXTENSION IF NOT EXISTS timescaledb;
  ```
  需在 `postgresql.conf` 中配置 `shared_preload_libraries = 'timescaledb'` 并重启。
  还需放开容器网段访问：`pg_hba.conf` 加 `host all all 172.16.0.0/12 scram-sha-256`（Docker 默认网段）。
- **Redis**：`.env.*` 中的 `REDIS_DATABASE` 要与 wxxpay 等系统错开，避免 key 互相污染。

## 首次部署

```bash
# 1. 部署服务器准备目录
mkdir -p /opt/jetlinks/compose
# 复制 docker-compose.*.yaml 过去；.env 由模板生成后填入真实口令：
#   cp .env.dev.example /opt/jetlinks/compose/.env.dev  然后编辑
# 注意：.env.dev / .env.test 已在 .gitignore 中，含口令不要提交回仓库

# 2. 启动（Jenkins 首次构建推镜像后执行）
cd /opt/jetlinks/compose
docker compose -f docker-compose.dev.yaml --env-file .env.dev up -d

# 3. 首次启动会自动建表 + 创建 admin 用户，观察日志直到就绪
docker logs -f jetlinks-api

# 4. 执行多租户迁移脚本（必须在第 3 步之后，脚本要绑定的 admin 用户此时才存在）
psql -h <PG> -U postgres -d jetlinks -f ../../jetlinks-manager/tenant-manager/docs/migration-postgres.sql

# 5. 重启使租户配置生效
docker compose -f docker-compose.dev.yaml --env-file .env.dev restart jetlinks-api
```

访问 `http://<服务器>:3200`，用 `admin` / `.env` 中的 `ADMIN_USER_PASSWORD` 登录。
首次登录后在「系统管理 → 菜单管理」导入前端菜单，租户菜单片段见
`jetlinks-ui-vue/src/modules/authentication-manager-ui/views/system/Tenant/menu-tenant.json`。

## Jenkins Job

> ✅ **已创建完成**：`jetlinks-api`、`jetlinks-ui` 两个 Multibranch Pipeline
> 已在 Jenkins 根目录建好，SCM 指向对应 GitHub 仓库，分支索引已发现 `dev` 分支。
> 后续重建或新增 Job 走工作区技能 `.harness/skills/create-jenkins-job`。

**注意**：JetLinks 的 **Job 名与仓库名不同**（`jetlinks-api` ← `jetlinks-community`，
`jetlinks-ui` ← `jetlinks-ui-vue`），与 wxxpay「Job 名即仓库名」的约定不一样，
写 `config.xml` 或 Groovy 时 `<remote>` 必须单独指定。

### 重建方式 A：复制现有 Job 配置（插件版本一定匹配）

```bash
JENKINS=http://<jenkins地址>
AUTH='admin:<API_TOKEN>'      # 用户 → 配置 → API Token 生成

# 1. 导出一个现有 wxxpay 前端 Job 作为模板
curl -u $AUTH $JENKINS/job/auth-service/config.xml -o template.xml

# 2. 改仓库地址（把 template.xml 里的 <remote> 换成 JetLinks 仓库），
#    分支保留 dev/test/uat/main，Script Path 保持 Jenkinsfile

# 3. 创建两个 Job
curl -u $AUTH -X POST "$JENKINS/createItem?name=jetlinks-api" \
     -H 'Content-Type: application/xml' --data-binary @jetlinks-api.xml
curl -u $AUTH -X POST "$JENKINS/createItem?name=jetlinks-ui" \
     -H 'Content-Type: application/xml' --data-binary @jetlinks-ui.xml
```

### 重建方式 B：UI 手工创建（四步）

**B1. 凭据**（`Manage Jenkins → Credentials`）
- `deploy-ssh-key`（SSH Username with private key）— 部署服务器 SSH 私钥
- `github-ssh-key`（SSH Username with private key）— 仓库访问密钥（可复用 wxxpay 已有的）

**B2. Agent**：需有 `java17-docker` 标签的节点（可复用 wxxpay 的 Agent），
前端 Job 额外需要 Node.js 18+ 与 pnpm（`corepack enable` 已写在 Jenkinsfile 里）。

**B3. 创建 Multibranch Pipeline**

| | 后端 Job | 前端 Job |
|---|---|---|
| 名称 | `jetlinks-api` | `jetlinks-ui` |
| 仓库 | jetlinks-community | jetlinks-ui-vue |
| Script Path | `Jenkinsfile` | `Jenkinsfile` |
| 凭据 | `github-ssh-key` | `github-ssh-key` |
| Branch Sources | 只保留 `dev` `test` `uat` `main` | 同左 |

**B4. 构建参数**：Jenkinsfile 已声明 `ENV`（dev/test/staging/prod）、`TAG`（留空取 git short hash）、
后端额外有 `SKIP_TESTS`。首次构建会因参数未初始化而跳过部署，**再构建一次**即可。

## 流水线阶段

| 阶段 | 后端 | 前端 |
|---|---|---|
| 构建 | `mvnw package -pl jetlinks-standalone -am -DjacocoArgLine=` | `pnpm install` + `pnpm vite build` |
| 测试 | surefire 报告归档（`SKIP_TESTS` 可跳过） | — |
| 镜像 | `jetlinks-standalone/Dockerfile`（JDK21 分层） | 根目录 `Dockerfile`（nginx + dist） |
| 部署 | SSH + `docker compose up -d --no-deps` | 同左 |
| 验证 | `curl :8858(dev)` 期望 200/302/401，重试 10×15s | `curl :3200` 期望 200，重试 5×10s |

> 后端构建必须带 `-DjacocoArgLine=`：jacoco 的 argLine 占位符在非 verify 生命周期不会被填充，
> 否则 surefire fork 会因 `${jacocoArgLine}` 字面量而崩溃。

## 环境晋级

按团队分支规范 `feature/* → dev → test → uat → main` 单向晋级。
Job 的 `ENV` 参数与分支对应关系由触发时手工选择；`prod` 环境流水线不自动部署，
需人工在部署服务器执行 compose（避免误触发生产发布）。
