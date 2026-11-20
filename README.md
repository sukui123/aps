# APS 排产系统

面向离散制造的有限产能、工序级排产系统。

> 本轮交付为**工程骨架（scaffold）**：目录齐全、配置正确、可编译可启动；P0 业务功能为后续迭代。
> 技术最高依据：[技术方案 v0.5](docs/tech/technical-design.md)（选型/架构/数据/API/前端/工程规范）。

## 文档

- [APS 排产系统 · 产品设计](docs/product/aps-product-design.md) — 产品定位、功能架构、数据模型、引擎设计、迭代路线
- [P0 功能清单与验收标准](docs/product/p0-backlog.md) — 可开发粒度的功能拆解、工作量、接口与里程碑
- [技术方案设计 v0.5](docs/tech/technical-design.md) — 技术选型、架构、数据落地、API、前端与工程规范（**开发对齐依据**）

## 原型

- [P0 交互原型](docs/prototype/p0-prototype.html) — 工作台、订单池、排产任务、计划甘特、未排清单、发布审批、主数据、负荷分析（单文件，浏览器直接打开；前端配色参考）

---

## 快速开始

### 前提

JDK 17、Maven 3.8.7+、Node 20+、pnpm（`corepack pnpm` 可用）。数据库为 PostgreSQL 15。

### 情形 A：平台 APS 测试沙箱（推荐）

平台已注入 `APS_DB_URL` / `APS_DB_USER` / `APS_DB_PASSWORD` / `APS_JWT_SECRET` 四个环境变量，
应用直接读取，**无需自行启动 PG 容器**（只用变量名，不打印值）：

```bash
# 1) 确认 4 个变量已注入（不打印值）
# 2) 后端编译 + 启动（单进程，引擎 worker 随应用启动，Flyway 自动迁移）
cd server && mvn -q -DskipTests compile
mvn -q -pl aps-api -am spring-boot:run
# 3) 另开终端：前端
cd frontend && corepack pnpm install && corepack pnpm dev
# 4) 访问 http://localhost:5173 ；健康检查 http://localhost:8080/api/v1/health
```

也可用一键脚本：`./scripts/dev.sh all`（`backend`/`frontend`/`db` 可单独启动）。

### 情形 B：纯本地裸机兜底

```bash
cp .env.example .env          # 仅占位符，按本地 PG 填写；.env 不提交
cd deploy && docker compose up -d   # 仅 PostgreSQL 15，无 Redis/MinIO/MQ
# 回到情形 A 的第 2 步（先 source .env 或由 shell 导出四个变量）
```

> 配置优先级：`环境变量 > .env（不提交）> application.yml 默认值`。
> `application.yml` 与 `.env.example` 仅含占位符，任何真实密钥/密码/IP 不得入库（技术方案 §8.6）。

## 工程结构

```
aps/
├── docs/                     # 产品文档 / 技术方案 / 原型（本轮不改动）
├── server/                   # Maven 多模块，最终打包为单一可执行 jar（aps）
│   ├── pom.xml               # 父 POM：Spring Boot parent、Java 17、dependencyManagement
│   ├── aps-common/           # 统一返回体 Result、错误码、全局异常、traceId 过滤器
│   ├── aps-domain/           # 领域实体（Resource 示例）、服务端口、约束/求解接口、存储抽象
│   ├── aps-infra/            # MyBatis-Plus mapper、服务实现、LocalFsObjectStorage
│   ├── aps-engine/           # 全量排产策略 + 应用内 worker 骨架（随主应用打包）
│   └── aps-api/              # 主启动模块：Security/JWT、Caffeine、MyBatis-Plus、SSE、OpenAPI、
│                             #   Resource/Health/SSE 示例 Controller、application.yml
├── db/migration/             # Flyway PG15 迁移 V1__init.sql（build 时复制到 aps-api classpath）
├── frontend/                 # React18 + TS + Vite5 + Zustand + TanStack Query + AntD5 + ECharts5
│   └── src/
│       ├── router/           # 9 个页面路由（含 /gantt → /plan 兼容重定向）
│       ├── features/         # dashboard/orders/scheduling/gantt/unscheduled/publish/master-data/analysis/auth
│       ├── api/ store/ styles/ shared/ app/
├── deploy/                   # docker-compose.yml（仅 PG15）、nginx.conf（可选）
├── scripts/dev.sh            # 本地/沙箱启动脚本
├── solver-python/            # OR-Tools Python 参考实现/tooling 占位（不进生产链路）
├── .github/workflows/ci.yml  # 后端 mvn verify + 前端 typecheck/build
├── .env.example              # 变量名与占位符（无真实值）
└── implementation.md         # 本轮变更、验证命令与待办记录
```

## 当前骨架状态

- ✅ 可编译：后端 `mvn -DskipTests compile`、前端 `pnpm typecheck` / `pnpm build` 均通过（详见 [implementation.md](implementation.md)）。
- ✅ 分层可跑通：Resource 示例实体 → Mapper → Service → Controller；健康检查与 SSE 骨架。
- ⏳ 后续迭代：排产引擎求解、硬约束校验、甘特内核/拖拽、发布下达、分析看板、RBAC/RLS 注入、集成任务 worker 等 P0 业务功能。
