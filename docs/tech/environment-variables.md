# APS 工程 · 环境变量清单

> 更新：2026-11-20
> 原则：**凭据只从环境变量读，不写进仓库**。配置文件里只放 `${变量名:默认值}` 占位。
> 你不需要把所有值发给我——在自己的环境里设置好即可；只需要告诉我哪些非默认值。

---

## A. 现在必须（P0 持久化第一步）

| 组件 | 变量 | 说明 | 默认值 | 你必须提供 |
|---|---|---|---|---|
| PostgreSQL | `APS_DB_URL` | JDBC 连接串 | `jdbc:postgresql://localhost:5432/aps` | 非本地时提供 |
| PostgreSQL | `APS_DB_USER` | 数据库用户 | `aps` | 非默认时提供 |
| PostgreSQL | `APS_DB_PASSWORD` | 数据库密码 | **无默认** | ✅ 必须 |
| PostgreSQL | `APS_DB_POOL_MAX` | 连接池上限 | `10` | 一般不用改 |
| Flyway | `APS_FLYWAY_ENABLED` | 启动时执行迁移 | `true` | 一般不用改 |
| Flyway | `APS_FLYWAY_LOCATIONS` | 迁移脚本位置 | `file:./db/migration` | 一般不用改 |
| 认证 | `APS_JWT_SECRET` | JWT 签名密钥（≥32 字符） | **无默认** | ✅ 必须 |
| 认证 | `APS_JWT_TTL_MINUTES` | access token 有效期（分钟） | `120` | 一般不用改 |
| 认证 | `APS_JWT_REFRESH_DAYS` | refresh token 有效期（天） | `7` | 一般不用改 |

> `docker-compose.yml` 已提供本地 PostgreSQL（仅容器内部使用的账号密码）。若用它，`APS_DB_*` 可设为
> `jdbc:postgresql://localhost:5432/aps` / `aps` / `aps_local_dev_only`。

---

## B. 建议一次性配好（后续接入不用再改）

| 组件 | 变量 | 说明 | 默认值 |
|---|---|---|---|
| 应用 | `APS_TZ` | 业务时区 | `Asia/Shanghai` |
| 应用 | `SERVER_PORT` | HTTP 端口 | `8080` |
| 应用 | `APS_PROFILE` | Spring profile | `default` |
| 日志 | `APS_LOG_LEVEL` | 日志级别 | `INFO` |
| 排产引擎 | `APS_DEFAULT_HORIZON_DAYS` | 默认计划展望期 | `7` |
| 排产引擎 | `APS_ENGINE_TIME_LIMIT_MS` | 排产超时 | `300000` |
| 排产引擎 | `APS_ENGINE_CANCEL_CHECK_ORDERS` | 取消检查点（每 N 单） | `50` |
| 目标权重 | `APS_WEIGHT_DUE` | 交期权重 | `45` |
| 目标权重 | `APS_WEIGHT_SETUP` | 换型权重 | `25` |
| 目标权重 | `APS_WEIGHT_LOAD` | 负荷均衡权重 | `15` |
| 目标权重 | `APS_WEIGHT_STABILITY` | 计划稳定权重 | `15` |
| ERP | `APS_ERP_ENABLED` | 是否启用 | `false` |
| ERP | `APS_ERP_BASE_URL` | 接口地址 | 空 |
| ERP | `APS_ERP_TOKEN` | 访问令牌 | 空 |
| ERP | `APS_ERP_SYNC_CRON` | 同步定时 | `0 0 2 * * *` |
| MES | `APS_MES_ENABLED` | 是否启用 | `false` |
| MES | `APS_MES_BASE_URL` | 接口地址 | 空 |
| MES | `APS_MES_TOKEN` | 访问令牌 | 空 |
| WMS | `APS_WMS_ENABLED` | 是否启用 | `false` |
| WMS | `APS_WMS_BASE_URL` / `APS_WMS_TOKEN` | 地址 / 令牌 | 空 |
| PLM | `APS_PLM_ENABLED` | 是否启用 | `false` |
| PLM | `APS_PLM_BASE_URL` / `APS_PLM_TOKEN` | 地址 / 令牌 | 空 |
| 文件 | `APS_UPLOAD_DIR` | Excel 导入暂存目录 | `./data/uploads` |
| 文件 | `APS_EXPORT_DIR` | 报表导出目录 | `./data/exports` |
| 通知 | `APS_ALERT_CHANNELS` | 告警渠道（逗号分隔） | `log` |
| 通知 | `APS_SMTP_HOST` / `APS_SMTP_PORT` | 邮件服务器 | 空 / `587` |
| 通知 | `APS_SMTP_USER` / `APS_SMTP_PASSWORD` | 邮件账号 | 空 |
| 通知 | `APS_ALERT_EMAILS` | 告警收件人 | 空 |
| 通知 | `APS_WECOM_WEBHOOK` | 企业微信机器人 | 空 |

---

## C. 以后再说（P1/P2，可先不配）

| 组件 | 变量 | 说明 | 默认值 |
|---|---|---|---|
| Redis | `APS_REDIS_ENABLED` | 分布式锁 / 缓存 | `false` |
| Redis | `APS_REDIS_URL` | 连接串 | `redis://localhost:6379/0` |
| 求解器 | `APS_SOLVER` | `heuristic` / `cpsat` | `heuristic` |
| 求解器 | `APS_SOLVER_TIME_LIMIT_MS` | 二次优化时限 | `60000` |
| 对象存储 | `APS_OBJSTORE_TYPE` | `local` / `s3` | `local` |
| 对象存储 | `APS_S3_ENDPOINT` / `APS_S3_BUCKET` / `APS_S3_KEY` / `APS_S3_SECRET` | S3 参数 | 空 |

---

## 最小可用配置（复制这段，填两个真实值）

```bash
# 必填
export APS_DB_URL='jdbc:postgresql://localhost:5432/aps'
export APS_DB_USER='aps'
export APS_DB_PASSWORD='<<你的数据库密码>>'
export APS_JWT_SECRET='<<至少32字符的随机串>>'

# 建议一起设（非默认值才需要设）
export APS_TZ='Asia/Shanghai'
export APS_ERP_BASE_URL=''
export APS_MES_BASE_URL=''
```

生成 JWT 密钥可以用：

```bash
openssl rand -hex 32
```

---

## 与配置文件的关系

| 文件 | 作用 |
|---|---|
| `aps-api/src/main/resources/application.yml` | 公共默认配置，读取所有环境变量，可提交 |
| `aps-api/src/main/resources/application-local.yml.example` | 本地配置模板，可提交 |
| `aps-api/src/main/resources/application-local.yml` | 本地真实覆盖，**已被 .gitignore 忽略** |

**注意**：当前 API 仍是内存态，数据库/JWT/集成相关配置先就位，
等对应依赖和实现接上后自动生效。现在设了也不会报错（Spring Boot 会忽略未使用的自定义属性）。
