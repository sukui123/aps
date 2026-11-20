# 实施记录 · 工程骨架（scaffold）

> 本轮目标：生成目录齐全、配置正确、可编译可启动的 APS 工程骨架，不实现 P0 全部业务功能。
> 最高依据：`docs/tech/technical-design.md` v0.5。工作区原有 docs/ 产品文档、原型、技术方案**未改动**。

## 1. 生成内容

### 后端 `server/`（Maven 多模块，单进程单 jar）
- 根 `pom.xml`：Spring Boot 3.2.5 parent、Java 17、dependencyManagement（MyBatis-Plus 3.5.5 / jjwt 0.12.6 / springdoc 2.3.0）。
- 5 个子模块（各含 `pom.xml` + 标准 `src/main/java` 包结构）：
  - `aps-common`：`Result` 统一返回体、`ErrorCode`/`CommonErrorCode`、`BusinessException`、`GlobalExceptionHandler`、`TraceIdFilter`、常量。
  - `aps-domain`：`Resource` 示例实体、`ResourceService` 端口、`Scheduler`/`SolverContext`/`ScheduleResult`/`Constraint` 求解接口、`ObjectStorage` 抽象。
  - `aps-infra`：`ResourceMapper`(MyBatis-Plus)、`ResourceServiceImpl`、`LocalFsObjectStorage`（本地 FS，无 MinIO）。
  - `aps-engine`：`FullScheduler`、`ScheduleWorker`(`@Scheduled` 占位)、`ConstraintRegistry`、`DecisionTraceCollector`（随主应用打包，不独立部署）。
  - `aps-api`（主启动）：`ApsApplication`；配置类 `MybatisPlusConfig`(PG 分页)、`CacheConfig`(Caffeine)、`SecurityConfig`+`JwtProperties`+`JwtTokenProvider`+`JwtAuthenticationFilter`(JWT 骨架)、`SseConfig`、`OpenApiConfig`；Controller `HealthController`/`ResourceController`/`SchedulingEventsController`；`application.yml`(仅 `${APS_*}` 占位符) + `application-dev.yml`。
- 示例垂直切片：Resource 实体 → Mapper → Service → Controller，证明分层可跑通。

### 数据库 `db/migration/V1__init.sql`
- PostgreSQL 15 方言 Flyway 迁移：平台域(tenant/sys_*/audit_log/token_blacklist)、资源域(resource/resource_skill/setup_matrix/downtime)、日历域(work_calendar/shift_template/calendar_exception/resource_calendar_binding)、工艺域(item/routing/operation/operation_resource_group)、约束域(constraint_rule/reason_code)、需求域(sales_order/production_order/order_change_log/material_availability)、计划域(scheduling_run/plan_snapshot/plan_version/scheduled_task/decision_trace/plan_diff)、集成任务(integration_task)。
- 通用字段 `id/tenant_id/created_at/by/updated_at/by/version/is_deleted`；JSONB + GIN 索引；甘特/订单池/补偿扫描复合索引；幂等唯一索引；RLS 策略（会话变量 `app.tenant_id` 未设置时放行）。
- `aps-api` 的 `maven-resources-plugin` 在 `process-resources` 阶段把仓库根 `db/migration` 复制到 `target/classes/db/migration`，Flyway 只需认 `classpath:db/migration` 一处。

### 前端 `frontend/`（pnpm + Vite5 + TS）
- `package.json`/`vite.config.ts`（`/api` 与 `/actuator` 代理到 8080）/`tsconfig.json`/`index.html`。
- `src/main.tsx`、`App.tsx`（QueryClientProvider + AntD ConfigProvider 中文主题 + BrowserRouter）、`app/AppLayout.tsx`（侧边栏菜单）。
- `router/index.tsx`：`/dashboard`、`/orders`、`/orders/:id`、`/scheduling`、`/plan`、`/unscheduled`、`/publish`、`/master-data`、`/analysis` 共 9 个路由 + `/gantt→/plan` 兼容重定向；`features/` 下各页占位组件（导入 AntD 证明依赖可用，gantt 页使用 Zustand，analysis 页导入 ECharts）。
- `api/`（axios client 统一解包 `Result`，token 拦截器）、`store/`（appStore/ganttStore）、`styles/`（对齐原型的 token）、`shared/`（PageShell 等）。

### 工程配套
- `.gitignore`、`.env.example`（仅变量名/占位符）、`.editorconfig`、`deploy/docker-compose.yml`（仅 PG15）、`deploy/nginx.conf`（可选）、`.github/workflows/ci.yml`、`scripts/dev.sh`、`solver-python/`（README + requirements + 占位子目录）、更新 `README.md`（快速开始/工程结构）、本文件。

## 2. 实际执行的验证命令与结果

| 命令 | 结果 |
|---|---|
| `cd server && mvn -q -DskipTests compile` | ✅ BUILD SUCCESS（5 模块全部编译通过；`target/classes/db/migration/V1__init.sql` 已生成） |
| `cd frontend && corepack pnpm install` | ✅ 安装 169 个包（pnpm 12.21.0；提示 esbuild 构建脚本被忽略，但未影响后续 build） |
| `cd frontend && corepack pnpm run typecheck`（`tsc --noEmit`） | ✅ 通过，无类型错误 |
| `cd frontend && corepack pnpm run build`（`tsc --noEmit && vite build`） | ✅ built in ~17s；仅有 >500kB chunk 体积告警（非错误） |
| 静态自检 | ✅ 迁移文件进入 classpath；pom 模块与依赖一致；占位符无真实值（见 §3） |

## 3. 安全自检
- `application.yml`：数据库 URL/账号/密码、JWT secret 均为 `${APS_DB_URL}` / `${APS_DB_USER}` / `${APS_DB_PASSWORD}` / `${APS_JWT_SECRET}` 占位符，无默认值、无真实值。
- `.env.example`、`docker-compose.yml`：仅占位符/默认非敏感值（如 PG 端口 5432、库名 aps）；`APS_DB_PASSWORD` 缺省时 compose 要求显式提供，不内置密码。
- 全仓库 grep 未发现真实 JWT secret、数据库密码或内网 IP（详见任务末自检输出）。日志/异常不打印敏感变量值（`GlobalExceptionHandler` 仅记录 traceId + path）。

## 4. 启动方式
- 平台沙箱：`./scripts/dev.sh all`（或分别 `backend`/`frontend`），见 README「快速开始·情形 A」。
- 裸机兜底：`cp .env.example .env` → `./scripts/dev.sh db`（仅 PG15）→ 导出四个 `APS_*` 变量 → `./scripts/dev.sh all`。
- 健康检查：`GET http://localhost:8080/api/v1/health`；OpenAPI：`/v3/api-docs`、`/swagger-ui.html`；SSE 骨架：`/api/v1/scheduling/runs/{id}/events`。

## 5. 运行时冒烟验证（APS 测试沙箱，mvn package + java -jar）
- 已读沙箱注入的四个 `APS_*` 环境变量（存在性确认，值不落盘/不回显），用 `java -jar aps-api-0.1.0-SNAPSHOT.jar` 实际启动。
- **修复了一个编译期发现不了的 bug**：`ApsApplication` 默认只扫描 `com.aps.api`，导致 `aps-infra` 的 `ResourceServiceImpl` 未被注册，`resourceController` 启动即失败。已加 `@ComponentScan("com.aps")` 覆盖全部模块包，重新打包后该错误消失。
- **PG 首次不可达、重试后恢复**：第一次冒烟时环境变量里的 PG 地址（由平台注入，此处不记录真实地址）连接被拒绝（当时 PG 未就绪）；用户要求重试后地址可达。
- **✅ 完整冒烟通过（重试）**：Flyway 连上 `PostgreSQL 15.12`，创建 `flyway_schema_history`，成功执行 `V1__init.sql`（0.38s，now at version v1）；应用 6.27s 启动完成。接口验证：`GET /actuator/health` = `{"status":"UP"}`，业务 `GET /api/v1/health` 返回标准 Result（traceId 正常），`GET /v3/api-docs` = 200；业务接口 `/api/v1/master/resources` 需 JWT（Security 骨架按预期拦截，401/403 属正常）。JDBC 直查数据库确认建出 **33 张业务表**（audit_log/calendar_exception/constraint_rule/decision_trace/downtime/integration_task/item/material_availability/operation/plan_diff 等），RLS 与索引随迁移生效。
- 静态验证全部通过：`mvn -DskipTests package` 成功（41MB fat jar）、前端 `tsc --noEmit` + `vite build` 成功、无真实密钥/IP 泄露。

## 6. 已知未验证项 / 待决策点
1. **Flyway 迁移已在真实 PG 验证通过**（v1，33 张表）；后续新增迁移仍需按流程验证。
2. **Flyway 9.x 依赖修正**：初版误加 `flyway-database-postgresql:9.22.3`（该 artifact 在 9.x 不存在，PG 支持在 `flyway-core` 内）；已移除并重新编译通过。若后续升级 Flyway 10+ 需重新引入该模块。
3. **RLS 生效条件**：迁移启用了 RLS + 策略，但表属主默认绕过策略；生产需以非属主账号连接或 `FORCE ROW LEVEL SECURITY`，并由应用层 `SET LOCAL app.tenant_id`。本轮未接入租户上下文注入（待 RBAC/RLS 迭代）。
4. **业务功能均为占位**：求解、硬约束、甘特虚拟滚动/拖拽、发布下达、分析图表、集成任务 worker 等仅留接口/空实现，按 P0 backlog 后续迭代。
5. **前端 chunk 体积**：AntD/ECharts 全量引入导致单包 >500kB，仅为构建告警；后续可做路由级 code-split。
6. **未执行 git 操作**：按任务边界，未 `git init`/`commit`/`push`、未发布产物。
