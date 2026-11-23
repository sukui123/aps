# APS 排产系统 · 技术方案设计

> 版本：v0.5（供评审）
> 修订记录：
> - v0.5 —— 对齐运行环境：PG 连接与 JWT 密钥由平台环境变量注入（`APS_DB_URL`/`APS_DB_USER`/`APS_DB_PASSWORD`/`APS_JWT_SECRET`），补充配置管理、接入方式与本地/沙箱两种启动情形。
> - v0.4 —— 按用户要求精简架构：P0 单进程单体（一个 jar，引擎为应用内 worker），移除 Redis 与 MinIO，仅保留 PostgreSQL；进度经 DB 落表后 SSE 推送，锁用 PG advisory/行锁，文件用本地 FS。
> - v0.3 —— 按用户要求移除 RabbitMQ，外部集成改为"应用内异步任务 + DB 集成任务表 + 幂等键 + 定时补偿"，P0 不引入消息中间件。
> - v0.2 —— 确认数据库为 PostgreSQL 15、部署形态为私有化部署（其余假设维持默认）。
> 对应产品文档：[APS 排产系统 · 产品设计](../product/aps-product-design.md) v0.1
> 对应功能清单：[P0 功能清单与验收标准](../product/p0-backlog.md) v0.1
> 对应原型：[P0 交互原型](../prototype/p0-prototype.html)
> 范围：面向 **P0（可用闭环）可落地**，为 P1/P2 预留扩展点
> 说明：产品文档明确"不包含技术选型细节"，本文档负责补齐**技术选型与架构设计**，所有条目尽量可追溯到具体 Epic / 功能编号。

---

## 0. 结论速览（TL;DR）

### 0.1 技术选型一句话结论表

| 领域 | 结论（P0 默认） | 关键备选 | 取舍要点 |
|---|---|---|---|
| 前端框架 | **React 18 + TypeScript + Vite 5** | Vue 3 + TS | 复杂交互生态、甘特自研可控、招人容易 |
| 状态管理 | **Zustand（UI 态）+ TanStack Query（服务端态）** | Redux Toolkit | 区分服务端/客户端状态，甘特高频交互轻量 |
| UI 组件 | **Ant Design 5** | Element Plus | 中后台表格/表单/审批组件齐全，贴合原型 |
| 甘特图 | **自研轻量内核（虚拟滚动 + 视口裁剪 + DOM 任务块）** | dhtmlxGantt（商业）、vis-timeline | 万级块性能可控、拖拽校验可定制、无授权费 |
| 图表 | **Apache ECharts 5** | AntV G2 | 负荷曲线/KPI 成熟，主题统一 |
| 实时进度 | **SSE**（取消/操作走 POST） | WebSocket | 单向推送场景更轻、代理友好 |
| 后端语言 | **Java 17（LTS）** | Go | 制造领域生态、引擎计算稳定、可维护 |
| 后端框架 | **Spring Boot 3 + MyBatis-Plus + Spring Security** | Spring Data JPA | 复杂 SQL（负荷/甘特/diff）可控 |
| 服务拆分 | **单进程模块化单体（一个 jar，引擎为应用内 worker）** | 水平扩展时拆引擎为独立进程 | P0 极致精简：一个 Spring Boot 应用 + PostgreSQL |
| 数据库 | **PostgreSQL 15（已确认，见 §1.5/§3.6）** | — | JSONB/窗口函数/CTE/MVCC 契合排产场景；原生 RLS 支撑数据权限 |
| 迁移工具 | **Flyway（PG 单一方言）** | Liquibase | 版本化、可回滚审计 |
| 缓存 | **进程内 Caffeine（可选）+ 直读 DB，无 Redis** | — | P0 不引入外部缓存，甘特草稿用前端状态 |
| 外部集成 | **应用内异步任务 + DB 集成任务表 + 幂等键 + 定时补偿（无消息中间件）** | — | OR-01/PB-04 幂等、可重试；P0 技术栈精简 |
| 任务调度 | **DB 任务表 + 应用内 worker + PG advisory lock/行锁防重（集成与排产任务统一模式）** | XXL-JOB | 可追溯、易取消、P0 足够简单 |
| 搜索引擎 | **P0 不引入**（DB 索引实现结构化筛选/关键词） | Elasticsearch | 万级数据 DB 足够，避免运维负担 |
| 文件存储 | **本地文件系统 + ObjectStorage 抽象层（无 MinIO）** | 未来可替换云 OSS | 私有化单进程零额外组件，实现可替换 |
| 求解器 | **P0 自研启发式（默认）；P1 OR-Tools CP-SAT 增强；P2 评估 Timefold** | — | 产品已定"启发式默认 + 开源增强" |
| Python 角色 | **参考实现/算法基准（tooling，不进生产链路）** | — | 用 OR-Tools Python 快速验证建模 |
| 鉴权 | **JWT Bearer + RBAC + 行级数据权限** | Session | 前后端分离、无状态、可水平扩展 |

### 0.2 本文档与里程碑的对应

| 里程碑 | 技术交付重点 | 主要章节 |
|---|---|---|
| M1（1–4 周） | monorepo/CI/本地环境、主数据 + 订单 + 登录权限骨架 | §6 §8 §5 |
| M2（5–9 周） | 排产引擎 + 硬约束 + 任务异步框架 + 版本化 | §4 §3 §2.4 |
| M3（8–12 周） | 甘特内核、拖拽校验、锁定 | §6.5 |
| M4（12–15 周） | 发布下达、分析看板、集成 | §5 §7 |
| M5（15–16 周） | 压测、可观测、试点调优与回滚演练 | §7 §10 |

---

## 1. 技术选型与理由

### 1.1 选型原则

1. **配置驱动优先**：产品决策"规则不写死"，技术选型需支持约束/权重/参数配置化（对应 SC-09、MD 约束规则）。
2. **P0 可落地优先**：拒绝为 P2 的规模化提前引入重组件（如 K8s、ES、分布式搜索引擎）；用最简技术栈跑通闭环，不引入消息中间件。
3. **可解释/可复现为硬约束**：选型必须支持输入快照、确定性求解、决策轨迹持久化（产品 §1.3、§3.3）。
4. **阶段化演进**：每个选型标注 P0/P1/P2 演进路径，避免未来推倒重来。
5. **结论明确**：每项给结论 + 备选 + 理由，不含糊罗列。

### 1.2 前端框架与状态管理

**结论：React 18 + TypeScript + Vite 5；状态用 Zustand + TanStack Query；UI 用 Ant Design 5。**

| 维度 | 结论 | 备选 | 理由 |
|---|---|---|---|
| 框架 | React 18 + TS | Vue 3 + TS | 甘特/虚拟滚动/拖拽等复杂交互的生态与范例更丰富；团队招人面广；与 ECharts/AntD 集成成熟 |
| 构建 | Vite 5 | Webpack | 冷启动与 HMR 快，P0 迭代效率优先 |
| 客户端状态 | Zustand | Redux Toolkit | 甘特缩放/选中/拖拽是高频细粒度更新，Zustand 轻量、无样板代码；Redux 过重，P0 不需要 |
| 服务端状态 | TanStack Query v5 | 手写 useEffect | 统一处理请求缓存、轮询任务进度（SSE 补充）、失效重取、乐观更新 |
| UI 组件 | Ant Design 5 | Element Plus | 表格（虚拟滚动）、表单、Steps（审批流 PF/PB-03）、Drawer（任务详情）齐全，视觉贴合原型 |
| 表单校验 | AntD Form + Zod | Yup | 前后端共用 Zod schema 可复用规则定义 |
| 路由 | React Router v6 | — | 成熟稳定 |

> 若团队 Vue 栈更熟，可整体切 Vue 3 + Pinia + Element Plus，架构分层不变；但甘特内核需自研，框架切换成本主要在组件层。

### 1.3 UI / 甘特图组件选型（关键）

**结论：P0 自研轻量甘特内核（虚拟滚动 + 视口裁剪 + 绝对定位 DOM 任务块），不引商业组件。**

| 方案 | 优点 | 缺点 | 判定 |
|---|---|---|---|
| **自研内核（采用）** | 万级块性能可控（只渲染视口内块）；拖拽吸附/冲突校验与后端协议完全自定义；零授权成本；契合原型结构 | 需投入专项（甘特 Must 已估 34 人日，见 P0 E4）；需自行处理边界细节 | ✅ P0 采用 |
| dhtmlxGantt（商业） | 功能全、开箱即用 | 授权费、体积大、深度定制受限、与自研校验协议耦合难 | ❌ 备选，P1 评估 |
| vis-timeline / react-calendar-timeline | 开源轻量 | 万级性能与复杂拖拽/泳道能力不足，冲突高亮定制受限 | ❌ 不满足 E4 验收 |
| 纯 Canvas 绘制 | 万级渲染性能最佳 | 交互（hover/拖拽命中/无障碍）实现复杂、调试成本高 | ⏸ P2 性能兜底预留 |

实现要点（详见 §6.5）：时间轴像素映射函数 + 纵向行虚拟化（TanStack Virtual）+ 视口内任务块裁剪 + Pointer Events 拖拽 + 班次边界吸附 + 前端本地冲突预检（300ms 内返回，复杂约束走后端）。

### 1.4 后端语言与框架

**结论：Java 17（LTS）+ Spring Boot 3.x + MyBatis-Plus + Spring Security + JWT。**

| 维度 | 结论 | 备选 | 理由 |
|---|---|---|---|
| 语言 | Java 17 LTS | Go / Kotlin | 制造行业存量系统多、生态成熟；引擎长计算 GC 可控（G1/ZGC）；团队与运维体系常见 |
| Web 框架 | Spring Boot 3 | Quarkus | 生态与组件（Security/Validation/Actuator）完备 |
| 持久层 | MyBatis-Plus | Spring Data JPA | 负荷分析、版本 Diff、甘特查询等复杂 SQL 需直接可控；JPA 在复杂报表下易失控 |
| 校验 | Jakarta Validation + 规则引擎 | — | DTO 基础校验 + 业务规则表驱动 |
| 对象映射 | MapStruct | BeanUtils | 编译期生成、零反射开销 |
| API 文档 | SpringDoc OpenAPI | — | 自动生成契约，对齐 §5 |
| Python 参考实现 | 独立 `solver-python/` tooling 模块 | — | 用 OR-Tools Python 做算法验证与基准回放；**不进生产链路**（对应产品 §6"业务层自研 + 开源求解器"） |

### 1.5 数据库选型（**已确认：PostgreSQL 15**）

> ✅ 用户已确认数据库采用 PostgreSQL 15。本节为**定稿口径**，不再保留方言分歧。

**结论：PostgreSQL 15（单一方言）。** 选型理由及相对 MySQL 8.0 的优势：

| 维度 | PostgreSQL 15（采用） | 说明 |
|---|---|---|
| 决策轨迹/参数快照 | **JSONB**（可索引、可查询） | 决策轨迹、参数快照以 JSONB 存储并建 GIN 索引，能力优于 JSON |
| 负荷分析/版本 Diff | **窗口函数 + CTE + 物化视图** | 物化视图用于负荷预聚合（P1），MySQL 无此能力 |
| 全文/关键词检索 | pg_trgm / tsvector（P0 够用） | 避免引入搜索引擎 |
| 行级数据权限 | **原生 RLS**（行安全策略） | 私有化多工厂/车间数据隔离（§3.5），无需视图/应用层兜底 |
| 并发读写（排产读写混合） | MVCC，长查询不阻塞写 | 更适合排产长查询与写入并存 |
| 运维 | 私有化现场由客户 IT 统一运维 PostgreSQL | — |

**落地约定（定稿）**：
1. 所有表结构按 PostgreSQL 15 方言设计与实现（JSONB、GIN 索引、声明式分区、原生 RLS）。
2. Flyway migration 统一维护于 `db/migration/`，版本化、可回滚审计。
3. 领域层经 MyBatis mapper 访问，按 PG 方言优化复杂 SQL（详见 §3.6）。
4. **数据库实例由平台提供**：生产/测试环境的 PG 由平台以环境变量注入连接信息（`APS_DB_URL` / `APS_DB_USER` / `APS_DB_PASSWORD`），应用只连接、不自管数据库实例、不自行起 PG 容器（配置接入见 §8.6）。

### 1.6 缓存策略（进程内 Caffeine，无 Redis）

**结论：P0 不引入 Redis 等外部缓存。仅用进程内 Caffeine 做可选本地缓存，其余直读 DB。**

| 原用途 | P0 替代实现 | 对应功能 |
|---|---|---|
| 排产进度/阶段/日志 | **直接写 PostgreSQL 任务表/进度日志表**，SSE 从 DB 读取（断线可退化为 `GET /runs/{id}` 轮询，见 §2.4） | SC-03 |
| 分布式锁（资源锁定、任务领取防重、发布互斥） | PostgreSQL **Advisory Lock**（`pg_advisory_xact_lock`）+ 行锁 `SELECT ... FOR UPDATE` / 乐观锁 version 字段（见 §1.7、§3.2） | SC-02、GB-07、PB-02 |
| 幂等键去重 | 已落表唯一键：`integration_task(biz_type,biz_key)` + 幂等表，不依赖 Redis（见 §1.7） | OR-01、PB-04 |
| 字典/约束规则/权重配置 | 进程内 **Caffeine** 本地缓存（可选）+ 直读 DB | SC-09、MD |
| 甘特草稿视图 | **前端本地状态**（Zustand），不上服务端缓存 | GB-01 |
| 限流 | P0 不做专门组件 | — |

**边界**：本地缓存失效不影响正确性（可直读 DB 重建）；进度与幂等以 DB 为唯一真相源。

### 1.7 异步任务方案：集成任务与排产任务（不引入消息中间件）

**结论：P0 不引入 RabbitMQ/Kafka 等任何消息中间件，也不引入 Redis。** 所有异步工作统一采用"**DB 任务表 + 应用内 worker（线程池）+ PG 锁防重 + 幂等键 + 定时补偿**"模式，均在同一个 `aps` 进程内运行，分两类任务：

| 类别 | 任务载体 | worker（均在 `aps` 单进程内） | 对应功能 |
|---|---|---|---|
| 集成任务（ERP/MES/WMS 同步、下达与回执） | DB 集成任务表 `integration_task` | 应用内线程池定时轮询领取 | OR-01、PB-04、PF-04 |
| 排产长任务调度 | DB 任务表 `scheduling_run` | 应用内排产 worker 线程池轮询领取 | SC-02/03 |

两类任务共用同一套机制（任务表为唯一真相源 + PG advisory lock/行锁防重 + 幂等键 + 状态机 + 定时补偿），仅业务处理不同。**进度写 DB 任务表，前端经 SSE 从 DB 读取推送（断线退化为 `GET /runs/{id}` 轮询，见 §2.4）。**

**为什么用 DB 任务表**：任务需持久化、可追溯、可取消、失败可重试（SC-03、产品 §8 可用性与 PF-04），DB 表天然满足；单进程单工厂并发低，轮询 + PG 锁足够；避免引入 MQ/Redis 带来的部署运维负担，P0 技术栈极简。

**并发防重（无 Redis）**：任务领取用 PostgreSQL **Advisory Lock**（`pg_advisory_xact_lock(hash(taskId))`）或 `SELECT ... FOR UPDATE SKIP LOCKED` 抢占，保证同一任务只被一个线程处理；状态变更带乐观锁 version 字段；事务结束锁自动释放，进程崩溃由 DB 回滚，无需 TTL 兜底。

**幂等与失败重试机制（满足 OR-01 / PB-04 / PF-04 验收）**：
1. **任务唯一键 + 业务幂等键**：`integration_task` 对 `(biz_type, biz_key)`（如 `ORDER_SYNC+外部订单号`、`DISPATCH+计划版本+工序`）建唯一索引；请求幂等键 `Idempotency-Key` 落表，重复提交直接返回首次结果，杜绝重复工单。
2. **状态机**：`PENDING → RUNNING → SUCCESS / FAILED → (RETRY 重试中) → SUCCESS / DEAD(超限)`；领取用上述 PG 锁保证单线程处理，状态变更带乐观锁版本号。
3. **失败重试 + 上限退避**：FAILED 由定时补偿任务重新入队，指数退避（如 1/2/4/8 分钟）；超过上限（默认 5 次）置 DEAD 并告警（PF-05），支持人工重试。
4. **定时补偿扫描**：`@Scheduled` 定时扫描超时 PENDING/RUNNING（线程异常残留）与 FAILED 任务，重置或重试。
5. **下游幂等**：调用 ERP/MES 时携带幂等键，并在 adapter 层做结果确认，配合本地唯一键确保"下达失败可重试且不产生重复工单"（PB-04 验收）。

> 本方案全程不依赖消息中间件与外部缓存；未来需要水平扩展时，可将排产 worker 拆为独立进程、多个实例仍以 DB 任务表 + PG 锁协调，不改变上层接口。

### 1.8 搜索引擎

**结论：P0 不引入独立搜索引擎。** 订单池筛选是结构化条件（交期/状态/客户/优先级/齐套，OR-02），万级数据用复合索引 + 游标分页即可；关键词搜索用 DB 前缀索引/PG 的 trgm。P2 若需订单备注/知识库全文检索（产品 §4.7 案例库）再引入 Elasticsearch/OpenSearch。

### 1.9 文件存储

**结论：本地文件系统 + 统一 `ObjectStorage` 抽象层（无 MinIO）。** P0 用 `LocalFsObjectStorage` 实现，存 Excel 导入文件与错误报告（MD-09/OR-01）、导出报表（AN-05）、审批/工单附件（PB-03/04），挂载目录持久化。接口与实现隔离，未来需要时替换为云 OSS 或 MinIO 仅需新增一个实现、不改上层代码。

### 1.10 求解器

**结论分层（对齐产品 §4.4.3 / §6）**：

| 阶段 | 结论 | 说明 |
|---|---|---|
| P0 | **自研启发式（Java，默认路径）** | 正排、硬约束优先、快速可解释；满足 SC-02/04/05，1 万工序 ≤5 分钟 |
| P1 | **OR-Tools CP-SAT 增强（局部改进/换型优化）** | 启发式初始解 → CP-SAT 局部优化 → 硬约束兜底；Java 经独立求解服务/绑定调用 |
| P2 | **评估 Timefold（JVM 原生 CP/本地搜索）** | 与 OR-Tools 二选一，依据增量重排/可解释/运维实测决定 |
| 全程 | **Python 参考实现（OR-Tools Python）** | 独立 tooling：算法原型、基准回放、结果对照；不进生产链路 |

混合策略：启发式生成初始解 → 优化器局部改进 → 硬约束校验兜底（产品 §4.4.3）。任何阶段超时/取消都返回当前最优可行解并标注"未收敛"（§4.7）。

---

## 2. 系统架构

### 2.1 总体分层架构（对齐产品 §4.1）

```
┌──────────────────────────────────────────────────────────────────────┐
│ 接入层   Web(React SPA) · Nginx(静态/反向代理) · ERP/MES/PLM 集成适配   │
├──────────────────────────────────────────────────────────────────────┤
│ 应用层   aps 单进程（Spring Boot，一个 jar）                          │
│  主数据建模  │ 需求订单  │ 排产工作台  │ 方案/版本  │ 发布下达  │ 分析看板 │
│  E1(MD)     │ E2(OR)   │ SC 任务编排 │ PB/SC     │ E5(PB)   │ E6(AN)   │
│  引擎（应用内 worker）：约束求解 · 目标优化 · 增量重排(P1) · 决策轨迹    │
│  可行性校验 · 未排归因  E3(SC-02~09)                                  │
├──────────────────────────────────────────────────────────────────────┤
│ 平台层   多组织/多工厂 · 权限(RBAC+RLS) · 审计 · 任务调度 · 监控 · 开放API │
│  E7(PF-01~05)                                                         │
├──────────────────────────────────────────────────────────────────────┤
│ 基础设施 PostgreSQL 15（业务库/任务表/进度/锁）；本地 FS（文件）        │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.2 服务拆分：单进程模块化单体

**结论：P0 为单一应用进程 `aps`（一个 Spring Boot 可执行 jar），排产引擎作为应用内后台 worker（线程池 + DB 任务表）运行。唯一基础设施是 PostgreSQL 15。**

| 阶段 | 形态 | 说明 |
|---|---|---|
| **P0** | 单进程 `aps`（一个 jar），引擎为应用内 worker 线程池 | 极致精简：一个应用 + 一个数据库；无独立引擎进程、无 MQ、无 Redis、无对象存储 |
| **P1/P2** | 需要水平扩展时再把引擎拆为独立进程 | 多个 worker 实例仍以 DB 任务表 + PG advisory lock/行锁协调，不改上层接口 |

**Maven 模块（逻辑模块，最终打包为单一可执行应用）**：`aps-common`（通用/错误码/工具）、`aps-domain`（领域模型/规则接口）、`aps-infra`（持久化/集成适配/文件存储）、`aps-api`（Web/应用编排）、`aps-engine`（求解实现/应用内 worker）。模块间通过接口解耦，但同属一个 jar、同一进程。

### 2.3 部署视图

```
[Browser] ──HTTPS──> [Nginx] ──静态──> React SPA
                         │
                         └─/api──> [aps（单进程一个 jar）]
                                    │  · Web/应用 + 应用内排产/集成 worker（线程池）
                                    │  · 进度、任务、锁均在 DB
                                    │
                                    ├──> PostgreSQL 15（业务库/任务表/进度日志/锁）
                                    └──> 本地 FS（导入导出文件，ObjectStorage 抽象层）
                         SSE <──────┘（从 DB 读进度后推送，断线退化 GET 轮询）
```

### 2.4 关键时序

#### 2.4.1 异步排产执行 + 进度回传（SC-02/03）

```
（下列 Web 请求与应用内 worker 同属 aps 单进程，仅逻辑角色分开）
计划员           aps(Web)         PostgreSQL/任务表        aps(排产worker)     SSE通道
  │   POST /runs    │                  │                        │              │
  │───────────────>│ 校验参数/权限      │                        │              │
  │                │ 幂等检查(唯一键)   │                        │              │
  │                │ INSERT run(PENDING)│                        │              │
  │                │──────────────────>│                        │              │
  │  202 + runId    │                  │                        │              │
  │<───────────────│                  │<───────────────────────│ 领取(advisory lock)│
  │                │                  │  run=RUNNING, 快照冻结   │              │
  │                │                  │<───────────────────────│ 阶段进度写 DB    │
  │  SSE 订阅 runId │                  │                        │              │
  │<───────────────│ 从 DB 读进度 ─────│                        │              │
  │<───────────────│<─────────────────────────────────────────────────────────│ push
  │  进度% / 阶段   │                  │                        │ 写入 PlanRun/    │
  │                │                  │<───────────────────────│ PlanVersion/     │
  │                │                  │                        │ ScheduledTask/   │
  │                │                  │                        │ Trace            │
  │                │                  │  run=SUCCEEDED         │              │
  │  完成事件       │<────────────────────────────────────────────────────────│
  │<───────────────│                  │                        │              │
```

要点：① DB 任务表为唯一真相源，进度/阶段/日志直接落表，SSE 从 DB 读取后推送；② 引擎按阶段上报进度（加载/展开/候选/求解/校验/落库），对应原型 §scheduling 的 6 段；③ 前端 SSE 订阅 runId，断线可退化为 `GET /runs/{id}` 轮询；④ 结果落库与状态置位在**同一事务**，避免"成功但无数据"；⑤ 任务领取用 PG advisory lock / `FOR UPDATE SKIP LOCKED` 防重，无 Redis。

#### 2.4.2 取消任务（SC-03，验收：资源立即释放不残留锁）

```
用户 POST /runs/{id}/cancel → aps(Web) 校验状态 → run 标记 CANCELLING（DB 状态位=取消标志）
→ 排产 worker 在每个检查点（每处理 N 道工序/每阶段）从 DB 读取 cancel 状态
→ 中断求解：回滚本批次未提交写入（事务回滚即释放 advisory/行锁，无需 TTL）
→ run=CANCELLED，写取消原因与耗时 → SSE 从 DB 读状态后推送取消事件
```

关键点：取消不是杀线程，而是**协作式中断 + 检查点**；取消标志落 DB（`scheduling_run.status`），worker 检查点读取；锁由 PG 事务承载，事务结束/回滚即自动释放，无 TTL 残留问题；取消后产生的半成品数据不进入正式 PlanVersion。

---

## 3. 数据模型落地

### 3.1 三条硬要求的落地（产品 §3.3）

| 硬要求 | 技术方案 |
|---|---|
| 版本化 | 主数据/订单/计划核心表带 `valid_from/valid_to/version`；生效版本用 `is_active` 或生效视图；订单变更不覆盖历史 |
| 快照化 | 每次 PlanRun 冻结输入快照（订单/资源/日历/规则/权重 JSONB + 关联版本 id），快照只增不改 |
| 可追溯 | ScheduledTask 关联 PlanVersion、来源订单/工序、命中规则/约束、DecisionTrace、上一版 Diff |

### 3.2 核心实体表设计要点（基于 PostgreSQL 15）

> 以下为**设计要点**而非可执行 DDL。通用字段：`id`(主键)、`tenant_id`(租户/工厂)、`created_at/by`、`updated_at/by`、`version`、`is_deleted`。审计字段全表覆盖（支撑 PF-03）。

#### 需求域（E2，OR-01~07）

| 表 | 关键字段 | 设计要点 |
|---|---|---|
| `sales_order` | 客户、下单日、交期、状态、优先级 | 状态机字段用枚举字典（待排/已排/已发布/执行中/完成/取消，OR-07） |
| `production_order` | `sales_order_id`、物料、数量、需求日、齐套状态、优先级、冻结标志 | 与销售订单 1:n；优先级单独字段便于加权 |
| `order_change_log` | 订单、变更类型（改期/改量/拆分/合并/撤销）、前后值、影响评估 | OR-05 留痕 + 影响提示 |
| `material_availability` | 物料、可用量、到货日、缺口量 | OR-06 齐套检查与未排原因 MAT-001 数据来源 |

#### 工艺域（E1，MD-03~05）

| 表 | 关键字段 | 设计要点 |
|---|---|---|
| `item` | 编码、单位、批量规则、提前期 | 与工艺路线按版本关联 |
| `routing` | 物料、`version`、`is_active`、生效期 | 版本化；排产快照记录所用 routing 版本 |
| `operation` | `routing_id`、工序序号、名称、标准工时、准备时间、可选资源组 | 工序网络（串/并/汇聚）用 `predecessor_ids` 或单独关系表 |
| `operation_resource_group` | 工序、候选资源组、能力标签 | 支撑候选资源过滤（SC-04） |

#### 资源域（E1，MD-01/02）

| 表 | 关键字段 | 设计要点 |
|---|---|---|
| `resource` | 编码、名称、类型（车间/工作中心/设备/线体/模具/班组）、`parent_id`、能力标签、并行度、效率系数、启停状态 | 自关联树（层级）；停用资源不进候选（E1 验收） |
| `resource_skill` | 资源、技能、等级 | 技能矩阵 |
| `setup_matrix` | 资源、从产品族、到产品族、换型时间、同族免换型 | MD-06；软约束"换型次数"计分来源 |
| `downtime` | 资源、开始、结束、原因 | MD-08；日历过滤硬约束 |

#### 日历域（E1，MD-07）

| 表 | 关键字段 | 设计要点 |
|---|---|---|
| `calendar` / `shift_template` | 工作时段、班次、加班、节假日 | 日历改动后新排产结果随之变化（E1 验收） |
| `resource_calendar_binding` | 资源、日历 | 资源与日历解耦复用 |

#### 约束域（E3，SC-09 / 产品 §4.4.1）

| 表 | 关键字段 | 设计要点 |
|---|---|---|
| `constraint_rule` | 类型、级别（HARD/SOFT/PREFERENCE）、启用、参数(JSON)、作用域（工厂/车间/全局） | 配置驱动核心；规则启停/权重参数化 |
| `reason_code` | 码（如 MAT-001）、名称、层级、建议处置 | 未排原因码与订单追踪七层漏斗（原型未排清单） |

#### 计划域（E3，SC-06~08 / 版本化核心）

| 表 | 关键字段 | 设计要点 |
|---|---|---|
| `scheduling_run` | run_no(如 RUN-20241118-03)、参数快照(JSON)、状态(PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLING/CANCELLED)、阶段、进度、耗时、引擎版本、触发人、幂等键 | **任务表**：任务调度/取消/进度/追溯的唯一真相源 |
| `plan_snapshot` | 输入快照(JSONB：订单、资源、日历、规则、权重 + 各版本 id) | 只增不改，回放与确定性校验依据 |
| `plan_version` | 版本号(V2024.47)、`run_id`、基线 id、状态(DRAFT/APPROVED/PUBLISHED/ARCHIVED)、冻结标志、指标(准时率/得分/未排数) | 发布后不可编辑（PB-05）；回滚=切换激活版本指针 |
| `scheduled_task` | `plan_version_id`、订单/工序、资源、开始、结束、锁定标志、状态、来源 routing 版本 | 工序级计划行；核心查询表 |
| `decision_trace` | `scheduled_task_id`、阶段、候选资源集、排除原因码、命中规则、上一版差异、未排卡点 | 可解释一等输出（产品 §4.7）；P0 建表并埋点，P1 完整展示 |
| `plan_diff` | 两版本、变动任务、变动类型、归因 | AN-04 / 计划稳定性 |

#### 平台域（E7，PF-01~05）

`sys_user`/`sys_role`/`sys_permission`（RBAC）、`audit_log`（操作留痕，谁/何时/改前改后）、`integration_config`/`sync_job`（PF-04）、`alert_rule`（PF-05）。

集成异步任务载体 `integration_task`（替代消息中间件，见 §1.7）：字段含 `biz_type`、`biz_key`（与 `biz_type` 联合唯一键，保证 OR-01/PB-04 幂等）、`idempotency_key`、`payload(JSONB)`、`status`(PENDING/RUNNING/SUCCESS/FAILED/RETRY/DEAD)、`retry_count`、`next_retry_at`、失败原因、版本(乐观锁并发防重，锁本身用 PG advisory lock 不落表)；排产进度单独落 `scheduling_run`(阶段/进度) 与进度日志表，供 SSE 读取。索引：`(status, next_retry_at)` 供定时补偿扫描；`(biz_type, biz_key)` 唯一索引防重复。

### 3.3 版本化与快照化策略

| 对象 | 策略 | 说明 |
|---|---|---|
| 主数据/工艺/日历 | 生效期 + 版本 + `is_active` | 修改生成新版本，旧版本保留供历史回放 |
| 订单 | 变更追加日志，不覆盖 | 快照冻结当时的订单状态 |
| 每次排产 | `plan_snapshot` + `scheduling_run.参数快照` + 引擎版本 + 配置版本 | 三元组保证"同样输入 + 同样引擎 = 同样结果"可验证 |
| 计划版本 | DRAFT→APPROVED→PUBLISHED→ARCHIVED | 发布后冻结；调整基于新版本（PB-05） |
| 回滚 | 记录 `baseline_version_id` | 回滚不删数据，切换激活指针 + 审计 |

### 3.4 索引与分区

| 表/场景 | 索引/分区 | 目的 |
|---|---|---|
| `scheduled_task(plan_version_id, resource_id, start_at)` | 复合索引 | 甘特按资源+时间窗查询（GB-01） |
| `scheduled_task(plan_version_id, order_id)` | 复合索引 | 订单视图/订单详情排产结果（OR-03） |
| `production_order(status, due_date, priority)` | 复合索引 | 订单池筛选（OR-02） |
| `decision_trace(scheduled_task_id)`、`(reason_code)` | 索引 | 未排清单/原因分布（AN-03） |
| `audit_log(created_at)`、`scheduling_run(created_at)` | 时间索引 / **按月分区**（P1） | 审计与批次量增长 |
| `plan_snapshot` | 主键查询为主 | 大 JSONB，避免冗余索引 |

分区策略：P0 单表 + 索引足够；P1 对 `audit_log`/`scheduling_run`/`decision_trace` 按时间分区（PostgreSQL 声明式分区），历史数据归档冷存储。

### 3.5 行级数据权限（RLS）

需求：功能权限 + 数据权限（工厂/车间，PF-02，产品 §4.8）。

| 方案 | 结论 | 实现 |
|---|---|---|
| PostgreSQL 原生 RLS | **采用** | 表启用 `ROW LEVEL SECURITY`，策略基于会话变量 `app.tenant_id`/`app.factory_ids`；应用连接每次请求设置 `SET LOCAL` |
| 应用层 ABAC 过滤器 | **叠加双保险** | 从 JWT claims 解析可见工厂/车间范围，注入查询上下文，与 RLS 形成双保险 |
| 兜底 | 审计与越权检测 | 查询无权限范围直接拒绝；定期扫描无租户隔离谓词的 mapper |

### 3.6 PostgreSQL 15 落地要点（定稿）

> 数据库已确认为 PostgreSQL 15，以下为统一方言下的实现约定。

| 能力 | PostgreSQL 15 实现 |
|---|---|
| JSON 快照/轨迹 | `JSONB` + GIN 索引，`domain` 层统一 JSON 访问封装 |
| 关键词模糊 | `ILIKE` / `pg_trgm` |
| 行级权限 | 原生 RLS + 应用层 ABAC 双保险（§3.5） |
| 时间分区 | 声明式分区（P1） |
| UPSERT | `ON CONFLICT ... DO UPDATE` |
| 物化视图（负荷预聚合 P1） | 原生物化视图 + 定期刷新 |
| 主键 | `IDENTITY` / 序列 |

落地规则：Flyway 脚本统一维护于 `db/migration/`；CI 用 Testcontainers(PostgreSQL) 跑迁移 + 冒烟。

---

## 4. 排产引擎实现方案

### 4.1 引擎定位与代码结构

引擎为 `aps` 单进程内的后台 worker 模块（Maven 逻辑模块 `aps-engine`，依赖 `aps-domain`/`aps-infra`），由应用内线程池通过 DB 任务表领取任务。核心包结构（位于 `aps/aps-engine/`）：

```
aps-engine/
  scheduler/
    Scheduler.java              # 门面：编排求解流程
    strategy/
      FullScheduler.java        # 全量（P0）
      IncrementalScheduler.java # 增量（P1，扩展点）
      PartialScheduler.java     # 局部重排（P1）
      SimulationScheduler.java  # 模拟/CTP（P1）
    SolverContext.java          # 求解上下文（快照/索引/取消标志/进度）
  constraint/
    Constraint.java             # 接口：level(HARD/SOFT/PREFERENCE)、evaluate(ctx)、reasonCode
    hard/  OperationOrderConstraint, ResourceExclusiveConstraint,
           CalendarConstraint, MaterialReadyConstraint, ToolUniqueConstraint   # P0 五类硬约束
    soft/  DueDateDelayConstraint, SetupCountConstraint, LoadBalanceConstraint
    pref/  SameFamilyFirst, LowLoadFirst, NearestOperation
    ConstraintRegistry.java     # 从 constraint_rule 表装载，支持启停/权重/参数
  heuristic/
    OperationDispatcher.java    # 工序分派：优先级排序 + 最早可用时间插入
    ResourceSelector.java       # 候选资源选择（能力/日历/齐套/占用过滤 + tie-break）
    TimelineIndex.java          # 资源时间轴索引（快速找可用窗口）
    ScoreCalculator.java        # 目标函数计分
  trace/
    DecisionTraceCollector.java # 埋点收集候选/排除原因/命中规则/差异/卡点
  worker/
    ScheduleWorker.java         # 领取任务、检查点、取消、进度上报、事务落库
```

### 4.2 三级约束如何落到代码（产品 §4.4.1，SC-04/05/09）

| 级别 | 代码表达 | 处理时机 | 可配置 |
|---|---|---|---|
| **硬约束**（违反即不可行） | `Constraint.level=HARD`；求解时在 `ResourceSelector`/`TimelineIndex` 中**前置规避**，最终由 `HardConstraintValidator` 全量校验兜底 | 求解中 + 落库前 | 启停/作用域（P0 固定实现 5 类，SC-09 为 S 可延后参数化） |
| **软约束**（违反计惩罚分） | `Constraint.level=SOFT`；`ScoreCalculator` 汇总惩罚分 | 解评价 | 权重来自参数快照 |
| **偏好**（影响排序/选择） | `Constraint.level=PREFERENCE`；作为候选排序的 tie-break 比较器链 | 选择资源/工序时 | 规则表配置 |

P0 五类硬约束（对齐 SC-04 与 P0 风险"先做 5 类"）：工序顺序、资源互斥、日历不可用、物料齐套、模具唯一。`HardConstraintValidator` 对最终解全量扫描，违反数必须为 0（E3 验收）。

### 4.3 四种排产模式如何落地（产品 §4.4.2）

| 模式 | 类 | P0/P1 | 实现要点 |
|---|---|---|---|
| 全量排产 | `FullScheduler` | **P0** | 按快照重排全部选中订单 |
| 增量排产 | `IncrementalScheduler` | P1 | 保留已排结果，仅排新单/变更单；依赖传播范围计算 |
| 局部重排 | `PartialScheduler` | P1 | 指定资源/时间窗/订单集合重排；冻结区处理 |
| 模拟排产 | `SimulationScheduler` | P1 | 沙箱执行，结果不落正式 PlanVersion（支撑 CTP/方案比选） |

统一接口：`ScheduleResult solve(SolverContext ctx)`；模式由 `scheduling_run.params.mode` 决定，P1 通过策略模式扩展，不改引擎主流程。

### 4.4 启发式求解策略（P0 默认，SC-02）

```
1 加载快照        plan_snapshot → 内存模型（订单/工序/资源/日历/规则/权重）
2 可行性过滤      → 未排订单（缺料/无资源/工艺缺失/超期）打原因码并剔除
3 工序展开        → 生成工序节点 DAG（前后置关系、批量折算）
4 工序排序        → 优先级 desc → 交期 asc → 订单号 → 工序序号（全序、确定性）
5 逐工序分派      → 候选资源集（能力/技能/日历/停用过滤）
                    → 每个候选算最早可用开始时间（含换型时间）
                    → tie-break 偏好排序 → 选资源、定时段
                    → 写入 TimelineIndex（占位）
6 硬约束校验      → HardConstraintValidator 全量扫描（应满足；不满足则记录并修正/标冲突）
7 软约束计分      → ScoreCalculator → 方案得分（原型 86.4）
8 轨迹组装        → DecisionTraceCollector 输出每个任务的决策依据 + 未排清单
9 事务落库        → PlanRun/PlanVersion/ScheduledTask/DecisionTrace 同事务写
```

复杂度：工序排序 O(n log n)，每工序候选数小（资源组有限），时间窗查找经 `TimelineIndex` 近似 O(log m)；整体满足 1 万工序分钟级（§7.1 佐证）。

### 4.5 目标函数落地（产品 §4.4.4）

```
score_penalty = w1·Σ延期时长 + w2·换型次数 + w3·WIP + w4·负荷方差 + w5·计划变动量(nervousness)
方案得分 = normalize(score_penalty)   // P0 输出 0~100，对齐原型"方案得分"
s.t. 硬约束全部满足
```

- 权重 `w1..w5` 来自 SC-01 参数快照（原型默认 45/25/15/15，可配置）。
- `计划变动量` 需与上一发布版本比较（P0 简单实现：基于 `plan_diff` 统计变动任务数占比；P1 精细化）。
- `ScoreCalculator` 为策略接口，P1 增补目标不改主流程。

### 4.6 可解释输出（产品 §4.7，DecisionTrace）

每个 `ScheduledTask` 输出（P0 埋点建表，P1 完整 UI 展示）：

| 输出项 | 数据来源 | 对应原型 |
|---|---|---|
| 候选资源集 + 排除原因（能力不符/无日历/物料未齐/被高优先级占用） | `DecisionTraceCollector` 在 `ResourceSelector` 埋点 | 任务详情"候选资源" |
| 命中的约束与规则 | `ConstraintRegistry` 命中记录 | 任务详情"约束" |
| 与上一版差异及原因 | `plan_diff` | 订单追踪/版本对比 |
| 未排卡点层级 + 原因码 + 建议 | `reason_code` + 漏斗阶段 | 未排清单（MAT-001 等） |

订单追踪七层漏斗（需求准入→齐套→工艺→资源候选→时间窗→优化决策→后处理）作为 trace 的阶段枚举，P0 先埋前 5 层，P1 补齐并做 UI。

### 4.7 任务拆分 / 可中断 / 确定性（E3 验收关键）

| 要求 | 实现方案 |
|---|---|
| **任务拆分** | 按订单集合/时间窗/资源范围分片；求解流程按阶段拆解，每阶段可单独计时与上报；P1 分片并行（按稳定 key 划分、结果按 key 合并） |
| **可中断** | 协作式取消：`SolverContext.cancelFlag`（从 DB `scheduling_run.status` 读取），在阶段边界与每 N 道工序的检查点检测；超时/取消返回当前最优可行解并标 `未收敛`；事务 `try/finally` 回滚释放 PG advisory/行锁（无需 TTL） |
| **确定性**（同输入结果稳定） | ① 排序使用全序比较键（优先级→交期→订单号→工序序号），不依赖 HashMap 迭代顺序；② 随机数固定 seed 并纳入快照；③ 候选选择 tie-break 全确定；④ 并行结果按稳定 key 归并；⑤ 输入快照 + 引擎版本 + 配置版本冻结；⑥ 提供"同快照重跑比对"自动化测试（§8.4） |

验收映射：SC-02/03/04/08 与 E3 四条验收（≤5 分钟、零违反、取消无残锁、未排 100% 带原因码）均在引擎层闭环。

### 4.8 未排原因码体系（SC-08，原型未排清单）

采用枚举字典 `reason_code`，P0 至少覆盖原型 5 类：

| 码 | 含义 | 卡点层级 | 建议处置 |
|---|---|---|---|
| MAT-001 | 缺料 | 齐套 | 顺延到货后重排 |
| RES-002 | 无可用资源 | 资源候选 | 增班/替代资源 |
| HOR-003 | 超出展望期 | 时间窗 | 纳入下周计划 |
| BOM-004 | 工艺缺失 | 工艺 | 指派主数据维护 |
| CAP-005 | 产能不足 | 时间窗/优化 | 换机/加班 |

未排订单 100% 带原因码（E3 验收）；原因码可枚举、可下钻（AN-03）。

---

## 5. API 设计

### 5.1 统一约定

| 项 | 约定 |
|---|---|
| 风格 | RESTful，资源名复数；JSON，UTF-8；前缀 `/api/v1`（兼容 P0 清单的 `/api/...`，网关可做无版本映射） |
| HTTP 语义 | GET 查询、POST 新建/动作、PATCH 部分更新（甘特任务调整）、PUT 全量更新；删除用逻辑删除 |
| 响应结构 | `{ "code": 0, "message": "ok", "data": {...}, "traceId": "...", "timestamp": "..." }`；`code=0` 成功 |
| 分页 | 请求 `page`（从 1）+ `pageSize`（默认 20，上限 200）；响应 `{list,total,page,pageSize}`；深翻用游标（`cursor`） |
| 幂等键 | 写操作带 `Idempotency-Key` 头；服务端幂等表存 key→结果，重复请求返回首次结果。适用：订单同步/新建、发起排产、发布、下达、拆分 |
| 鉴权 | `Authorization: [redacted]
| 错误码 | 见 §5.4；HTTP 状态与业务码并存 |
| 追踪 | 每请求生成 traceId（MDC + 响应头），贯通 api→engine（§7.3） |
| 限流 | 网关按用户/接口限流（集成同步、发起排产重点） |

### 5.2 P0 接口清单对齐（逐条，源自 P0 §5）

| # | 接口 | 方法 | 说明 | 幂等 | 对应功能 | 服务 |
|---|---|---|---|---|---|---|
| 1 | `/api/orders` | GET/POST | 订单查询（分页/筛选）/新建 | POST 是 | OR-01/02 | aps-api |
| 2 | `/api/orders/sync` | POST | 触发 ERP 同步 | 是 | OR-01/PF-04 | aps-api→DB 集成任务表 |
| 3 | `/api/orders/{id}/split` | POST | 订单拆分（改量/拆分 OR-05） | 是 | OR-05 | aps-api |
| 4 | `/api/master/resources` | GET/POST/PUT | 资源维护（层级/能力 MD-01/02） | POST 是 | MD-01/02 | aps-api |
| 5 | `/api/master/routings` | GET/POST/PUT | 工艺路线（MD-03/04/05） | POST 是 | MD-03~05 | aps-api |
| 6 | `/api/master/calendars` | GET/POST/PUT | 日历班次（MD-07/08） | POST 是 | MD-07/08 | aps-api |
| 7 | `/api/scheduling/runs` | POST | 发起排产（参数 SC-01） | 是 | SC-01/02 | aps-api→task |
| 8 | `/api/scheduling/runs/{id}` | GET | 进度与结果（SC-03） | — | SC-03 | aps-api |
| 9 | `/api/scheduling/runs/{id}/cancel` | POST | 取消（SC-03） | 是 | SC-03 | aps-api |
| 10 | `/api/plans/{id}/tasks` | GET/PATCH | 甘特任务查询与调整（GB-01/05/07） | PATCH 带版本 | GB-01/05/07 | aps-api |
| 11 | `/api/plans/{id}/validate` | POST | 发布前校验（PB-01） | — | PB-01 | aps-api |
| 12 | `/api/plans/{id}/publish` | POST | 发布（PB-02/03） | 是 | PB-02/03 | aps-api |
| 13 | `/api/plans/{id}/dispatch` | POST | 下达 MES（PB-04） | 是 | PB-04 | aps-api→DB 集成任务表+MES 适配器 |
| 14 | `/api/analysis/load` | GET | 负荷分析（AN-02） | — | AN-02 | aps-api |
| 15 | `/api/analysis/unscheduled` | GET | 未排清单（SC-08/AN-03） | — | SC-08/AN-03 | aps-api |

> 补充（P0 支撑）：`/api/auth/login`、`/api/master/import`（MD-09 Excel 导入）、`/api/plans/{id}/rollback`（SC-07）、`/api/audit`（PF-03）、SSE `/api/scheduling/runs/{id}/events`。

### 5.3 异步任务接口状态机（SC-02/03）

```
PENDING → RUNNING → SUCCEEDED
              ├──→ FAILED → (retry) → PENDING
              └──→ CANCELLING → CANCELLED
POST /runs        → 202 Accepted + {runId, status, Location: /runs/{runId}}
GET  /runs/{id}   → {status, progress, phase, elapsed, resultSummary?, failureReason?}
SSE               → phase/progress 事件流 + 终态事件
```

### 5.4 错误码（分段）

| 段 | 含义 | 示例 |
|---|---|---|
| 0 | 成功 | `0` |
| 1xxxx | 通用参数/鉴权 | 10001 参数错误、10003 无权限、10004 幂等冲突 |
| 2xxxx | 主数据/订单 | 20001 工艺缺失(BOM-004)、20002 物料缺料(MAT-001) |
| 3xxxx | 排产引擎 | 30001 快照加载失败、30002 硬约束校验未通过、30003 任务已取消、30004 求解超时未收敛 |
| 4xxxx | 计划/发布 | 40001 校验未通过不可发布、40002 版本已冻结、40003 下达失败可重试 |
| 5xxxx | 集成/外部 | 50001 ERP 同步失败、50002 MES 下达失败、50003 重试耗尽 |
| 9xxxx | 系统内部 | 90000 未知错误 |

错误响应体含 `code/message/traceId/details[]`，业务码与 HTTP 状态映射（4xx 用户可控、5xx 系统侧）。

---

## 6. 前端工程方案

### 6.1 目录结构

```
frontend/
  src/
    app/            # 应用入口、路由、Provider、全局样式
    router/         # 路由表（对齐 §6.2）
    shared/         # 通用组件(Button/Table/Form/Drawer 封装)、hooks、utils、types
    api/            # 接口 client（按域分组：orders/master/scheduling/plans/analysis）
    features/
      dashboard/    # AN-01
      orders/       # OR-01~07（列表/详情/导入/变更）
      scheduling/   # SC-01~08（参数/执行/进度/摘要）
      gantt/        # GB-01~10（甘特内核 + 工具栏 + 详情抽屉）
      unscheduled/  # SC-08/AN-03
      publish/      # PB-01~05
      master-data/  # MD-01~10（资源树/工艺/日历/换型 Tab）
      analysis/     # AN-02/05
      auth/         # PF-01/02 登录/权限
    store/          # Zustand stores（gantt UI 态、用户态）
    styles/         # 设计 token（对齐原型配色）
```

### 6.2 路由（对齐 P0 §4 页面清单与原型）

| 路由 | 页面 | 关键功能 | 原型页 |
|---|---|---|---|
| `/dashboard` | 工作台 | KPI、异常提醒、最近批次（AN-01） | dashboard |
| `/orders` | 订单池 | 筛选/批量/优先级（OR-02/04/05） | orders |
| `/orders/:id` | 订单详情 | 工艺展开/齐套/排产结果（OR-03/06） | orderDetail |
| `/scheduling` | 排产任务 | 参数/执行/进度/摘要（SC-01~08） | scheduling |
| `/plan` | 计划甘特 | 泳道甘特/详情/冲突（GB-01~08） | gantt |
| `/unscheduled` | 未排清单 | 原因码分组/明细（SC-08/AN-03） | unscheduled |
| `/publish` | 发布审批 | 校验/审批流/下达（PB-01~05） | publish |
| `/master-data` | 主数据 | 资源/工艺/日历/换型 4 Tab（MD-01~10） | master |
| `/analysis` | 负荷分析 | 负荷曲线/超载（AN-02） | analysis |

> 原型内部菜单用 `gantt` 命名，正式路由以 P0 清单 `/plan` 为准；前端保留兼容重定向。

### 6.3 组件分层

`页面(Page)` → `功能组件(Feature)` → `领域组件(Domain: GanttBoard/OrderTable/CalendarEditor)` → `通用组件(AntD 封装)` → ` api/hooks`。跨页复用走 `shared`，域内复用走 `features/xxx/components`；状态：服务端数据 TanStack Query，UI 交互态 Zustand。

### 6.4 甘特万级任务块虚拟滚动与拖拽校验（核心难点，GB-01/05/06）

**渲染性能（目标：5000 块首屏 ≤1.5s，滚动 ≥30fps）**

1. 时间轴映射：`x = (t - viewStart) / pxPerUnit`，日/周/月缩放改 `pxPerUnit`（GB-02）。
2. 双向虚拟化：纵向用 TanStack Virtual 只渲染可见资源行；横向按视口时间窗裁剪任务块（API 支持按时间窗分批取数）。
3. 任务块用绝对定位 DOM（非全量 Canvas），视口内通常仅数百块，保证 hover/点击/无障碍。
4. 数据轻量化：甘特接口只返回渲染所需字段（id/资源/起止/状态/优先级/锁定/冲突），详情走抽屉按需加载（GB-04）。
5. Web Worker 预处理分块与坐标计算；骨架屏 + 渐进加载；避免大对象引用导致 GC 抖动。

**拖拽与冲突校验（目标：300ms 内返回）**

1. Pointer Events 实现横向移动（改时间）、纵向换资源、边界拉伸；吸附到班次边界（GB-05）。
2. 前端本地预检：`TimelineIndex` 的浏览器镜像做资源互斥/日历/工序顺序的快速冲突检测，命中即高亮（GB-06），保证 <300ms。
3. 复杂/跨资源约束：拖拽结束调 `/api/plans/{id}/validate`（或批量校验接口）异步复核，返回冲突清单与可选解法（换机/顺延/拆批，原型提示）。
4. 乐观更新 + 失败回滚；所有调整经 `PATCH /tasks` 落库并写审计（谁/何时/改了什么，E4 验收）。
5. 锁定任务：Zustand 记录锁定集，拖拽与重排均跳过；重排后位置不变（E4 验收）。

---

## 7. 非功能实现方案

### 7.1 性能

| 指标 | 目标 | 技术手段 |
|---|---|---|
| 全量排产 | 1 万工序 ≤5 分钟（产品 §8、E3） | 内存快照预加载；资源时间轴索引；候选前置过滤；启发式 O(n log n)；批量写库（非逐条）；应用内 worker 线程池异步执行（不阻塞 Web 请求）；P1 分片并行 |
| 增量排产 | ≤30 秒（P1） | 只重排受影响工序 + 冻结区；依赖传播范围计算；复用已排 TimelineIndex |
| 甘特首屏 | 5000 块 ≤1.5s（E4） | §6.4 双向虚拟化 + 视口裁剪 + 轻量化数据 + Web Worker + 骨架屏 |
| 拖拽校验 | ≤300ms | 前端本地预检 + 后端异步复核（§6.4） |
| 订单同步 | 1 万条 ≤2 分钟（E2） | 批量 upsert + 分批处理 + 幂等；异步任务 + 进度 |

验证：以真实量级基准数据做压测（§8.4），早期做（P0 风险"引擎性能不达标"）。

### 7.2 可用性（失败重试 / 版本回滚）

| 场景 | 手段 |
|---|---|
| 排产任务失败 | 状态 FAILED + 失败原因；有限次自动重试（指数退避）；超过阈值告警（PF-05）；重复提交幂等 |
| Worker 线程异常 | 任务表 PENDING/RUNNING 超时回收（定时补偿扫描重置）；PG advisory/行锁随事务回滚自动释放，任务可重新领取 |
| 版本回滚 | `plan_version.baseline_version_id` + 激活指针切换；发布版本不可变；回滚写审计（SC-07） |
| 集成下达失败 | DB 重试表 + 业务幂等键 + 定时补偿（指数退避、上限 5 次告警）；下游携带幂等键，失败可重试且不产生重复工单（PB-04，机制见 §1.7） |
| 数据库 | 主从（P1）+ 定期备份 + 恢复演练 |

### 7.3 可观测

| 层次 | 手段 |
|---|---|
| 指标 | Spring Boot Actuator + Micrometer → Prometheus → Grafana；业务指标：排产时长/成功率、未排率、硬约束违反数、任务队列长度、接口 P95 |
| 日志 | 结构化日志 + traceId（MDC）贯通 api→engine；ELK/Loki 聚合；引擎阶段耗时日志 |
| 链路 | OpenTelemetry（P1）追踪一次排产跨 api/engine/DB 调用 |
| 告警 | 任务失败/超时、接口异常、队列积压 → 邮件/企微（PF-05） |
| 业务健康 | 排产健康度看板（未排数、准时率、负荷率、稳定性，产品 §4.7） |

### 7.4 安全审计

| 项 | 手段 |
|---|---|
| 认证 | JWT + 密码策略（PF-01）、短会话过期；登出/失效用 DB 黑名单表（`token_jti` + 过期时间），不依赖 Redis；签名密钥从环境变量 `APS_JWT_SECRET` 读取，**不落代码库** |
| 授权 | RBAC 功能权限 + 行级数据权限（§3.5）；网关与服务端双重校验 |
| 审计 | `audit_log` 全量关键操作留痕（谁/何时/改前改后）；甘特每次调整必须写审计（E4 验收）；可查询导出（PF-03） |
| 脱敏 | 客户名称/联系人等敏感字段展示脱敏、导出受控 |
| 传输/存储 | HTTPS；密钥/凭据（如 `APS_DB_PASSWORD`、`APS_JWT_SECRET`）由平台环境变量运行时注入，**不入库、不入码、不进文档**（见 §8.6） |
| 集成安全 | 接口 AK/SK 签名、幂等、IP 白名单 |

---

## 8. 工程规范与交付

### 8.1 代码仓库目录（monorepo）

```
aps/                         # 仓库根
  docs/
    product/                 # 现有产品文档（不改动）
    tech/                    # 本技术方案（新增）
    prototype/               # 现有原型（不改动）
  frontend/                  # React SPA（pnpm workspace）
  server/                    # Maven 多模块，最终打包为单一可执行 jar（aps）
    aps-common/              # 通用：错误码、工具、常量
    aps-domain/              # 领域模型、约束/求解器接口
    aps-infra/               # 持久化(mapper)、集成适配、本地文件存储
    aps-api/                 # Web/应用编排（主启动模块）
      src/main/resources/application.yml       # 仅放占位符 ${APS_*} 与默认值，无真实值
    aps-engine/              # 求解实现 + 应用内 worker（逻辑模块，随主应用打包）
  solver-python/             # Python 参考实现/算法基准（tooling，不进生产）
  db/migration/              # Flyway PostgreSQL 迁移脚本（见 §3.6）
  deploy/                    # docker-compose、K8s manifests、nginx 配置
  scripts/                   # dev.sh、seed 数据、基准数据生成
  .env.example               # 环境变量占位符模板（仅变量名，无真实值，见 §8.6）
  .github/workflows/         # CI/CD
```

> `application.yml` 与 `.env.example` 只含占位符（如 `${APS_DB_URL}`）或空值模板；真实连接与密钥由平台运行时以环境变量注入，二者关系见 §8.6。

### 8.2 分支与提交规范

| 项 | 规范 |
|---|---|
| 分支 | `main`（保护，仅 PR 合并）、`develop`、`feature/<id>-<slug>`、`release/x.y`、`hotfix/*`；功能分支对齐功能编号（如 `feature/SC-02-heuristic`） |
| 提交 | Conventional Commits：`feat(scope): desc`，scope 用功能编号/模块（如 `feat(engine): 实现最早可用时间插入`） |
| PR | 必填改动说明 + 关联 Epic/功能编号 + CI 通过 + 1 人 Review；发布 PR 前检查 diff |
| 合并 | 禁止直接推 main；合并走 squash；**本任务不执行 push/建 PR/合并**（工作区无 .git，见交付说明） |

### 8.3 CI/CD

| 阶段 | 内容 |
|---|---|
| 提交前 | Husky + lint-staged（ESLint/Prettier、Spotless） |
| CI（PR） | 前端 `pnpm lint && test && build`；后端 `mvn verify`（单测 + 覆盖率门槛）；迁移冒烟（Testcontainers PostgreSQL） |
| 产物 | 前端静态包、后端单一可执行 jar（一个应用镜像）推镜像仓库 |
| 部署 | `dev → staging → prod`，prod 需审批；DB 迁移由 Flyway 在发布阶段受控执行（备份先行） |
| 追踪 | 每次构建记录 commit sha、引擎版本（写入 `scheduling_run`，支撑可复现） |

### 8.4 测试策略

| 类型 | 工具 | 重点 |
|---|---|---|
| 单元 | JUnit5 + Mockito（后端）、Vitest（前端） | 约束逻辑、计分、时间映射、状态机；覆盖率门槛（核心模块 ≥70%） |
| 集成 | Testcontainers（仅 PostgreSQL）+ 本地临时目录 | 迁移脚本、事务落库、幂等、任务领取/取消、集成任务重试与幂等；本地 FS 文件读写 |
| 引擎基准 | 固定基准数据集 + 断言 | **确定性**：同快照重跑结果一致；**性能阈值**：1 万工序 ≤5 分钟、硬约束=0、未排 100% 带码 |
| E2E | Playwright | 对齐 P0 验收总清单：导入→排产→甘特调整→发布→下达；甘特首屏/拖拽时延 |
| 性能 | k6 / JMeter | 万级甘特首屏、订单同步、并发排产任务隔离 |
| 安全 | 依赖扫描（Trivy/Snyk）+ 越权用例 | 行级权限、审计完整性 |

### 8.5 本地开发环境

前置：JDK 17、Node 20+、pnpm、Maven（Docker 仅裸机兜底情形需要）。

** Database / 密钥准备——分两种情形**：
- **(a) 平台 APS 测试沙箱（推荐）**：`APS_DB_URL` / `APS_DB_USER` / `APS_DB_PASSWORD` / `APS_JWT_SECRET` 已由平台注入容器，应用直接读取即可，**无需也不应自行起 PG 容器**。跳过下表的 compose 步骤，从"启动应用"开始。
- **(b) 纯本地裸机（可选兜底）**：可 `docker compose up -d` 起一个临时 PG（无 Redis/MinIO/MQ），再用环境变量指向它；或用环境变量指向任意可达的 PG。`.env.example` 仅提供占位符模板（`cp .env.example .env` 后本地填写，`.env` 不提交）。

```
1. 情形(a) 沙箱：确认上述 4 个环境变量已注入（只看变量名，不打印值）
   情形(b) 裸机：cp .env.example .env → docker compose up -d（可选）→ 在 .env 填本地连接占位
2. ./scripts/seed.sh      # 载入基准主数据 + 1 周订单（连接指向环境变量给定 PG）
3. 后端：mvn -pl server/aps-api -am spring-boot:run   （单进程，引擎 worker 随应用启动）
4. 前端：pnpm install && pnpm dev
5. 访问 http://localhost:5173，打开原型对照验收
```

### 8.6 配置与环境变量

**外部化配置优先级（高 → 低）**：`环境变量`  >  `.env`（本地占位，不提交）>  `application.yml` 默认值。生产/测试沙箱只依赖第一层。

**平台注入的环境变量**（应用直接读取，不自管数据库、不内置密钥）：

| 环境变量 | 用途 | 接入点 |
|---|---|---|
| `APS_DB_URL` | PostgreSQL JDBC URL（示例形态 `jdbc:postgresql://<host>:5432/aps`，实际值由平台提供） | `spring.datasource.url` |
| `APS_DB_USER` | 数据库账号 | `spring.datasource.username` |
| `APS_DB_PASSWORD` | 数据库密码（敏感） | `spring.datasource.password` |
| `APS_JWT_SECRET` | JWT 签名密钥（敏感） | `jwt.secret`（PF-01/§7.4） |

**Spring Boot 接入方式**（`application.yml`，只用占位符，无真实值）：

```yaml
spring:
  datasource:
    url: ${APS_DB_URL}
    username: ${APS_DB_USER}
    password: ${APS_DB_PASSWORD}
jwt:
  secret: ${APS_JWT_SECRET}
```

也可用 `@ConfigurationProperties(prefix = "jwt")` 绑定为类型安全的配置类；上述变量缺失时应用启动即失败（fail-fast），避免静默使用默认凭据。

**安全约定（强制）**：
- 敏感值（密码、JWT 密钥、内网 host）**仅在运行时由平台环境变量注入**，不写入 `application.yml`、不入库、不进代码仓库、不进任何文档（含本文件）。
- `application.yml` 与 `.env.example` 只保留变量名/占位符或空模板；`.env` 仅供本地裸机填写且加入 `.gitignore`。
- 日志与错误输出**不打印**这些变量的值（只可输出"配置缺失/连接失败"等状态）。
- 密钥轮换通过更新平台环境变量完成，无需改代码或重新构建 jar。

---

## 9. 需求追溯矩阵

### 9.1 Epic / 功能 → 技术组件 → 里程碑

| Epic | 功能编号 | 技术组件/模块 | 里程碑 |
|---|---|---|---|
| E1 主数据 | MD-01~10 | aps-api 主数据模块、Excel 导入(MD-09)、数据体检(MD-10)、`resource/routing/operation/calendar/setup_matrix` 表 | M1 |
| E1 变更影响 | MD-11(S) | aps-api 影响分析、约束注册表 | M1/M3 |
| E2 订单 | OR-01~07 | aps-api 订单模块、`integration_task` 同步(幂等)、`production_order`/`material_availability`/`order_change_log` | M1 |
| E3 引擎 | SC-01~08 | aps-engine 逻辑模块（heuristic/constraint/trace/worker，应用内）、`scheduling_run`/`plan_snapshot`/`plan_version`/`scheduled_task`/`decision_trace`、DB 任务表 + PG advisory/行锁 + SSE | M2 |
| E3 可配置 | SC-09(S) | `constraint_rule` 表 + ConstraintRegistry 参数化 | M2/M4 |
| E4 甘特 | GB-01~08 | frontend `features/gantt`（虚拟滚动内核、拖拽校验、详情抽屉）、`PATCH /plans/{id}/tasks` | M3 |
| E4 增强 | GB-09/10(S) | 视图切换、Zustand 撤销/重做栈 | M3/M4 |
| E5 发布 | PB-01~05 | aps-api 发布模块、`validate/publish/dispatch`、审批流状态机、`integration_task` 下达(幂等)+MES 适配器、`audit_log` | M4 |
| E6 分析 | AN-01/02/03/05 | aps-api 分析模块 + ECharts、负荷视图/物化视图(P1)、`reason_code` 统计 | M4 |
| E6 稳定性 | AN-04(S) | `plan_diff` + nervousness 计分 | M4/M5 |
| E7 平台 | PF-01~05 | Spring Security + JWT、`sys_*`/`audit_log`、集成管理、Actuator/Prometheus/告警 | M1(M1 建骨架) → M4/M5 |

### 9.2 接口追溯

P0 15 条接口（§5.2）均由 `aps-api` 对应 Controller 实现：订单 3 条 → M1；主数据 3 条 → M1；排产 3 条 → M2；甘特任务 → M3；发布 3 条 → M4；分析 2 条 → M4。SSE 与 auth/导入/回滚为支撑接口，随对应里程碑交付。

**E7 平台与集成（M1 已落地接口，回写于 E7 实现轮）**：

| Controller | 主要路由 | 权限码 | 功能 |
|---|---|---|---|
| `AuthController` | `POST /api/v1/auth/login`、`/logout`、`GET /me` | 白名单 / 认证 | PF-01 登录、登出黑名单、当前用户 |
| `UserController` | `/api/v1/users` CRUD 与 `/{id}/password`、`/{id}/status` | `user:manage` | PF-01 用户/密码/启停 |
| `RoleController` | `/api/v1/roles` | `role:manage` | PF-02 角色 |
| `PermissionController` | `/api/v1/permissions` | `role:manage` | PF-02 权限码字典 |
| `AuditController` | `GET /api/v1/audit`、`/audit/export` | `audit:read` / `audit:export` | PF-03 审计查询与导出 |
| `IntegrationController` | `/api/v1/integrations/configs`、`/tasks`、`/tasks/{id}/retry` | `integration:manage` / `integration:read` | PF-04 配置、监控、人工重试 |
| `OrderSyncController` | `POST /api/v1/orders/sync` | `order:sync` | PF-04 同步触发（双幂等） |

权限码常量见 `aps-common/constant/PermissionCodes.java`；审计动作码见 `AuditActions.java`（含跨 Epic 契约 `PLAN_TASK_ADJUST`，E4 甘特拖拽落地时接线）。工厂/车间级数据权限（验收 #8）仍为过渡实现，权威映射待 E1/E2/E5 补齐业务谓词；PF-05 告警按设计延后一版。

### 9.3 验收标准追溯（P0 §8 总清单）

| 验收项 | 技术方案落点 |
|---|---|
| 导入 5000 资源/2000 工艺/1 万订单无阻断错误 | §8.4 集成/E2E、§7.1 批量导入 |
| 1 万工序全量排产 ≤5 分钟 | §4.4、§7.1、§8.4 引擎基准 |
| 硬约束违反 = 0 | §4.2 Validator 兜底 + §4.7 |
| 未排订单 100% 带原因码 | §4.8、§4.6 |
| 甘特 5000 块 ≤1.5s | §6.4、§7.1 |
| 拖拽 300ms 内校验 | §6.4 |
| 锁定任务重排后位置不变 | §6.4 锁定集 + §4.7 |
| 发布前四项校验全拦截 | §5.2 #11、PB-01 |
| 下达 MES 幂等可重试 | §5.1 幂等、§7.2、PF-04 |
| 全流程可审计 | §7.4、§3.2 审计字段 |

---

## 10. 风险与待定项

### 10.1 影响技术选型的待确认问题（源自产品 §9）

> 下表挑出**影响技术选型/架构**的问题。**标注 ✅ 的两项（数据库、部署形态）已由用户确认，为定稿口径；其余项按"默认假设"推进，不视为已定论。**

| # | 问题 | 影响选型 | 结论 / 默认假设 | 决策建议 / 触发点 |
|---|---|---|---|---|
| ✅ — | **数据库选型** | **高**（migration/权限/性能） | **已确认 PostgreSQL 15**（见 §1.5/§3.6） | 定稿，单一 PG 方言，不再有分歧 |
| ✅ 4 | **部署形态** | **高**（隔离/权限/部署） | **已确认：私有化部署** | 多租户仅作为未来演进预留（§10.3），不再是待确认项 |
| 1 | 目标行业与工艺复杂度 | 高（约束模型、性能、分片） | 离散制造·多品种小批量，中等复杂度（4~8 工序，少量替代资源/换型） | M1 前用真实样本校准；复杂度高则 P1 提前上并行/CP-SAT |
| 2 | 排产粒度（工序级/工单级） | 高（数据模型/引擎） | **工序级**（产品 §6 已定；P0 已按工序级建模） | 若确需工单级，简化 §3/§4 的工序展开；建议维持工序级 |
| 3 | ERP/MES 品牌与接口文档 | 高（集成方案） | 先做文件/数据库视图同步兜底 + 应用内适配层与 `integration_task`（P0 风险已列，见 §1.7） | 拿到品牌/接口文档后实现具体适配器；无文档不阻塞 P0 内部闭环 |
| 5 | 规模（设备/订单/工序量级） | 高（分区/并行/缓存） | 设备数十台、日订单千级、工序万级（对齐产品 §8 目标） | 超 10 万工序则提前 §3.4 分区 + P1 并行/分片 |
| 6 | 首要痛点排序 | 中（目标权重/求解侧重） | 交期达成优先，换型/稳定次之（原型默认权重 45/25/15/15） | 由试点反馈调权重；权重已参数化（SC-01），调整不改代码 |
| 7 | 是否已有排产引擎（替换/增强） | 高（引擎边界） | **无既有引擎，全新自研**（工作区无历史代码，用户已确认丢失） | 若实际存在需对接的引擎，重新界定 §4 边界与接口 |
| — | 前端框架团队栈（新增） | 中 | React + TS | 若团队 Vue 栈更熟，可切 Vue 3 + Pinia（§1.2） |

### 10.2 技术风险与应对

| 风险（对齐 P0 §7） | 技术应对 |
|---|---|
| 客户主数据质量差 | MD-09 导入逐行校验 + MD-10 数据体检 + 实施前置清洗；原因码 BOM-004 暴露 |
| 引擎性能不达标 | 早期真实量级基准（§8.4）；资源时间轴索引、候选预筛、批量写；P1 分片并行 |
| 甘特交互复杂度被低估 | §6.4 先只读+虚拟滚动跑通，再上拖拽；预留专项（E4 已 34 人日） |
| 约束表达不清反复改代码 | §4.2 约束表驱动 + ConstraintRegistry，P0 固定 5 类硬约束 |
| ERP 接口反复 | §10.1#3 文件/视图兜底 + 应用内适配器隔离(`integration_task`)；接口后置 |

### 10.3 扩展点清单（P1/P2 预留，不在 P0 实现）

| 扩展点 | 预留方式 |
|---|---|
| 增量/局部/模拟排产 | `Scheduler` 策略接口 + `mode` 参数，新增实现不改主流程（§4.3） |
| 优化求解器（OR-Tools/Timefold） | `ScoreCalculator`/求解器策略接口 + 混合流程（§1.10、§4.1） |
| 决策轨迹 UI 与七层漏斗 | P0 建表埋点，P1 展示（§4.6） |
| 多工厂/多租户（私有化下的未来演进预留） | tenant_id 字段 + RLS + 任务队列隔离（§3.5、§2.2） |
| 可观测链路（OTel） | traceId 贯通、Micrometer 指标（§7.3） |
| 负荷物化视图 | PostgreSQL 物化视图 + 定期刷新（§3.6） |
| 全文检索 | 抽象搜索接口，P2 接 ES（§1.8） |

---

## 附录 A · 术语与编号对照

| 缩写 | 含义 |
|---|---|
| PlanRun / `scheduling_run` | 一次排产批次（参数快照、状态、耗时） |
| PlanVersion / `plan_version` | 一次排产产出的计划版本（含发布态） |
| ScheduledTask | 工序级计划行（订单+工序+资源+起止） |
| DecisionTrace | 决策依据（候选、排除原因、命中规则、差异/卡点） |
| RLS | 行级安全（Row Level Security） |
| SSE | Server-Sent Events |
| Nervousness | 计划变动量（稳定性指标） |

## 附录 B · 关键技术决策记录（ADR）清单

| ADR | 决策 | 状态 |
|---|---|---|
| ADR-001 | 后端 Java 17 + Spring Boot 3 + MyBatis-Plus | 已定 |
| ADR-002 | 服务形态：单进程模块化单体（一个 jar，引擎为应用内 worker） | 已定（v0.4） |
| ADR-003 | 甘特自研内核（虚拟滚动 + DOM 块） | 已定 |
| ADR-004 | 求解：启发式默认 + OR-Tools 增强 + Timefold 待评估 | 已定 |
| ADR-005 | **数据库 PostgreSQL 15（单一方言）** | **已确认（v0.2）** |
| ADR-006 | **私有化部署**（多租户为未来演进预留） | **已确认（v0.2）** |
| ADR-007 | P0 任务队列用 DB 任务表 + PG advisory/行锁防重，不引 MQ/Redis | 已定（v0.4） |
| ADR-008 | 无 Redis（进程内 Caffeine 可选）；无 MinIO（本地 FS + 抽象层）；进度经 DB + SSE | 已定（v0.4） |

---

> 交付边界声明：本文档只新增 `docs/tech/technical-design.md` 及 `docs/tech/` 目录；未修改产品文档与原型，未执行任何 git 写操作（工作区无 `.git`，且任务未授权推送）。文中所有性能数字均为**目标/验证计划值**，非已实测结果。
