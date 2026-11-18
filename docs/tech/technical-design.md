# APS 排产系统 · 技术方案（P0）

> 版本：v0.1
> 对应：[产品设计](../product/aps-product-design.md) · [P0 功能清单](../product/p0-backlog.md)
> 目标：把 P0 的 46 项 Must 功能落成可实现的技术方案——技术栈、数据模型、引擎算法、接口契约、性能边界。

---

## 1. 技术选型

| 层 | 选型 | 理由 | 备选 |
|---|---|---|---|
| 后端 | Java 21 + Spring Boot 3.2 | 制造业交付主流，招人容易，事务与权限生态成熟 | Python FastAPI（团队 Python 强时，接口契约不变） |
| 数据库 | PostgreSQL 16 | `jsonb` 存排产参数与决策快照；窗口函数、部分索引适合负荷查询 | MySQL 8（放弃部分 jsonb 能力） |
| 缓存/锁 | Redis 7 | 排产任务分布式锁、进度缓存、会话 | 单机可先不用，用 DB 行锁 |
| 任务调度 | Spring `@Async` + 任务表轮询 | P0 单机足够，避免过早引入 MQ | RabbitMQ / Kafka（多工厂时） |
| 排产引擎 | 独立 Maven 模块（同进程） | P0 零网络开销，可直接调；后续可拆独立服务 | 独立 gRPC 服务 |
| 优化求解 | 暂不引入；P2 接 OR-Tools CP-SAT | P0 启发式已能满足可行性目标 | Python sidecar 调 OR-Tools |
| 前端 | Vue 3 + TypeScript + Vite + Pinia | 团队通用，组件生态好 | React 18 |
| UI 组件 | Element Plus | 表格、表单、树、日期组件齐全 | Ant Design Vue |
| 甘特 | Canvas 自绘（自研） | 5000+ 任务块，DOM 方案会卡；Canvas 可做到 30fps | DHTMLX Gantt（商业授权，快但贵） |
| 部署 | Docker Compose | P0 单机/单厂，运维简单 | K8s（多厂、高可用时） |

**一句话**：Java + PostgreSQL + Vue3，甘特自研 Canvas，引擎先做启发式。这个组合的交付风险最低，性能也够 P0 的目标。

---

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│ 浏览器：Vue3 SPA                                              │
│  工作台 / 订单池 / 排产任务 / 甘特(Canvas) / 未排 / 发布审批    │
└──────────────────────────┬──────────────────────────────────┘
                           │ REST / JSON
┌──────────────────────────▼──────────────────────────────────┐
│ Spring Boot 应用                                              │
│  ├─ api 层：Controller + DTO + 参数校验                       │
│  ├─ domain 层：订单、计划、主数据、发布审批                     │
│  ├─ engine 层：SchedulingEngine（启发式）+ ConstraintValidator │
│  ├─ integration 层：ERP/MES 适配器（接口 + 文件兜底）           │
│  └─ infra 层：任务调度、审计、权限                              │
└───────────┬───────────────────────────┬─────────────────────┘
            │                           │
     ┌──────▼──────┐             ┌──────▼──────┐
     │ PostgreSQL  │             │   Redis     │
     │ 主数据/计划  │             │ 锁/进度/会话 │
     └─────────────┘             └─────────────┘
```

**关键约束**：engine 层不依赖 Spring 容器，纯 POJO 输入输出。这样引擎可以单独压测、单独演进，将来拆服务或换语言成本最低。

---

## 3. 数据模型

### 3.1 核心 ER

```
md_resource ──< md_resource_capability
     │
     └──< md_downtime

md_item ──< md_routing ──< md_operation ──< md_operation_link
                │              │
                │              └── md_setup_matrix（resource × family）
                └── md_calendar ──< md_calendar_day ──< md_shift

ord_production_order ──< ord_order_operation ──< ord_material_requirement

plan_run ──< plan_version ──< plan_task ──< plan_task_trace
                                  │
                                  └── 引用 ord_order_operation、md_resource
```

### 3.2 主数据

```sql
-- 资源（车间/工作中心/设备/线体，自关联树）
CREATE TABLE md_resource (
  id            BIGSERIAL PRIMARY KEY,
  code          VARCHAR(64)  NOT NULL UNIQUE,
  name          VARCHAR(128) NOT NULL,
  res_type      VARCHAR(32)  NOT NULL,        -- PLANT/WORKCENTER/MACHINE/LINE/TOOL/CREW
  parent_id     BIGINT REFERENCES md_resource(id),
  capacity      NUMERIC(8,2) NOT NULL DEFAULT 1,   -- 并行能力
  efficiency    NUMERIC(5,3) NOT NULL DEFAULT 1,   -- 效率系数
  calendar_id   BIGINT,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  attrs         JSONB        NOT NULL DEFAULT '{}',
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_resource_parent ON md_resource(parent_id);
CREATE INDEX idx_resource_type   ON md_resource(res_type, status);

-- 资源能力标签（用于工序-资源匹配）
CREATE TABLE md_resource_capability (
  resource_id BIGINT NOT NULL REFERENCES md_resource(id) ON DELETE CASCADE,
  capability  VARCHAR(64) NOT NULL,
  level       SMALLINT NOT NULL DEFAULT 1,
  PRIMARY KEY (resource_id, capability)
);

-- 物料
CREATE TABLE md_item (
  id          BIGSERIAL PRIMARY KEY,
  code        VARCHAR(64) NOT NULL UNIQUE,
  name        VARCHAR(128) NOT NULL,
  uom         VARCHAR(16) NOT NULL DEFAULT 'EA',
  family      VARCHAR(64),                    -- 产品族，用于换型判断
  lot_size    INT,
  status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
);

-- 工艺路线（版本化）
CREATE TABLE md_routing (
  id         BIGSERIAL PRIMARY KEY,
  code       VARCHAR(64) NOT NULL,
  item_id    BIGINT NOT NULL REFERENCES md_item(id),
  version    VARCHAR(16) NOT NULL,
  status     VARCHAR(16) NOT NULL DEFAULT 'DRAFT',   -- DRAFT/ACTIVE/ARCHIVED
  valid_from DATE,
  valid_to   DATE,
  UNIQUE (code, version)
);
CREATE INDEX idx_routing_item ON md_routing(item_id, status);

-- 工序
CREATE TABLE md_operation (
  id             BIGSERIAL PRIMARY KEY,
  routing_id     BIGINT NOT NULL REFERENCES md_routing(id) ON DELETE CASCADE,
  seq            INT NOT NULL,
  code           VARCHAR(32) NOT NULL,        -- OP10/OP20
  name           VARCHAR(128) NOT NULL,
  std_time_min   NUMERIC(10,2) NOT NULL,      -- 单件工时
  setup_time_min NUMERIC(10,2) NOT NULL DEFAULT 0,
  min_batch      INT NOT NULL DEFAULT 1,
  resource_group VARCHAR(64),                 -- 能力标签
  UNIQUE (routing_id, seq)
);

-- 工序前后置（支持并行、汇聚）
CREATE TABLE md_operation_link (
  routing_id   BIGINT NOT NULL REFERENCES md_routing(id) ON DELETE CASCADE,
  pred_seq     INT NOT NULL,
  succ_seq     INT NOT NULL,
  link_type    VARCHAR(16) NOT NULL DEFAULT 'FS',  -- FS/SS/FF
  lag_min      INT NOT NULL DEFAULT 0,
  PRIMARY KEY (routing_id, pred_seq, succ_seq)
);

-- 日历与班次
CREATE TABLE md_calendar (
  id   BIGSERIAL PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  tz   VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai'
);
CREATE TABLE md_calendar_day (
  calendar_id BIGINT NOT NULL REFERENCES md_calendar(id) ON DELETE CASCADE,
  work_date   DATE NOT NULL,
  is_workday  BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (calendar_id, work_date)
);
CREATE TABLE md_shift (
  id          BIGSERIAL PRIMARY KEY,
  calendar_id BIGINT NOT NULL REFERENCES md_calendar(id) ON DELETE CASCADE,
  shift_no    SMALLINT NOT NULL,
  start_time  TIME NOT NULL,
  end_time    TIME NOT NULL
);

-- 停机/维护
CREATE TABLE md_downtime (
  id          BIGSERIAL PRIMARY KEY,
  resource_id BIGINT NOT NULL REFERENCES md_resource(id) ON DELETE CASCADE,
  start_ts    TIMESTAMPTZ NOT NULL,
  end_ts      TIMESTAMPTZ NOT NULL,
  reason      VARCHAR(64),
  CHECK (end_ts > start_ts)
);
CREATE INDEX idx_downtime_res ON md_downtime(resource_id, start_ts);

-- 换型矩阵
CREATE TABLE md_setup_matrix (
  resource_id  BIGINT NOT NULL REFERENCES md_resource(id) ON DELETE CASCADE,
  from_family  VARCHAR(64) NOT NULL,
  to_family    VARCHAR(64) NOT NULL,
  setup_min    INT NOT NULL,
  PRIMARY KEY (resource_id, from_family, to_family)
);
```

### 3.3 订单与计划

```sql
-- 生产订单
CREATE TABLE ord_production_order (
  id            BIGSERIAL PRIMARY KEY,
  order_no      VARCHAR(64) NOT NULL UNIQUE,
  item_id       BIGINT NOT NULL REFERENCES md_item(id),
  routing_id    BIGINT NOT NULL REFERENCES md_routing(id),
  qty           NUMERIC(14,3) NOT NULL,
  due_date      TIMESTAMPTZ NOT NULL,
  priority      SMALLINT NOT NULL DEFAULT 5,      -- 1 最高
  status        VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  -- PENDING/SCHEDULED/UNSCHEDULED/RELEASED/IN_PROGRESS/DONE/CANCELLED
  material_ready_ts TIMESTAMPTZ,                  -- 齐套时点
  src_system    VARCHAR(32),
  src_id        VARCHAR(64),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_status_due ON ord_production_order(status, due_date);
CREATE INDEX idx_order_priority   ON ord_production_order(priority, due_date);

-- 工序实例（订单展开后）
CREATE TABLE ord_order_operation (
  id              BIGSERIAL PRIMARY KEY,
  order_id        BIGINT NOT NULL REFERENCES ord_production_order(id) ON DELETE CASCADE,
  op_seq          INT NOT NULL,
  op_code         VARCHAR(32) NOT NULL,
  qty             NUMERIC(14,3) NOT NULL,
  duration_min    NUMERIC(10,2) NOT NULL,        -- 已按 qty×std_time 折算
  setup_min       NUMERIC(10,2) NOT NULL DEFAULT 0,
  resource_group  VARCHAR(64),
  UNIQUE (order_id, op_seq)
);

-- 齐套明细
CREATE TABLE ord_material_requirement (
  id          BIGSERIAL PRIMARY KEY,
  order_id    BIGINT NOT NULL REFERENCES ord_production_order(id) ON DELETE CASCADE,
  item_code   VARCHAR(64) NOT NULL,
  required_qty NUMERIC(14,3) NOT NULL,
  available_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
  shortage_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
  eta_ts      TIMESTAMPTZ
);

-- 排产批次
CREATE TABLE plan_run (
  id           BIGSERIAL PRIMARY KEY,
  run_no       VARCHAR(64) NOT NULL UNIQUE,
  params       JSONB NOT NULL,                   -- 计划范围、策略、权重、冻结区
  input_digest VARCHAR(64) NOT NULL,             -- 输入快照哈希，判重与复现
  status       VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
  -- QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED
  progress     SMALLINT NOT NULL DEFAULT 0,
  message      TEXT,
  started_at   TIMESTAMPTZ,
  finished_at  TIMESTAMPTZ,
  created_by   BIGINT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_run_status ON plan_run(status, created_at DESC);

-- 计划版本
CREATE TABLE plan_version (
  id          BIGSERIAL PRIMARY KEY,
  run_id      BIGINT NOT NULL REFERENCES plan_run(id),
  version_no  VARCHAR(32) NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  -- DRAFT/IN_REVIEW/PUBLISHED/ARCHIVED
  score       NUMERIC(8,2),
  kpi         JSONB,                             -- 准时率、换型数、负荷等
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (version_no)
);

-- 排产任务（计划行）
CREATE TABLE plan_task (
  id          BIGSERIAL PRIMARY KEY,
  version_id  BIGINT NOT NULL REFERENCES plan_version(id) ON DELETE CASCADE,
  order_op_id BIGINT NOT NULL REFERENCES ord_order_operation(id),
  resource_id BIGINT NOT NULL REFERENCES md_resource(id),
  start_ts    TIMESTAMPTZ NOT NULL,
  end_ts      TIMESTAMPTZ NOT NULL,
  seq_no      INT NOT NULL,                      -- 资源上的加工顺序
  locked      BOOLEAN NOT NULL DEFAULT FALSE,
  source      VARCHAR(16) NOT NULL DEFAULT 'ENGINE',  -- ENGINE/MANUAL
  CHECK (end_ts > start_ts)
);
CREATE INDEX idx_task_version_res_time ON plan_task(version_id, resource_id, start_ts);
CREATE INDEX idx_task_version_order    ON plan_task(version_id, order_op_id);

-- 决策依据（P0 先落库，P1 做可视化）
CREATE TABLE plan_task_trace (
  task_id      BIGINT PRIMARY KEY REFERENCES plan_task(id) ON DELETE CASCADE,
  candidates   JSONB,        -- [{resourceId, score, rejectedReason}]
  constraints  JSONB,        -- [{code, level, passed}]
  unscheduled_reason VARCHAR(32),
  detail       JSONB
);
```

### 3.4 数据设计要点

1. **工序实例与主数据分离**：`ord_order_operation` 是订单展开后的快照，主数据改了不影响历史计划。
2. **时间统一 `TIMESTAMPTZ`**，前端按工厂时区展示，避免跨时区错误。
3. **`plan_run.params` 与 `input_digest` 必须存**：前者用于复现参数，后者用于判断“输入没变就不必重算”。
4. **`plan_task_trace` 拆表**：决策数据体积大，与主表分离，避免拖慢甘特查询。

---

## 4. 排产引擎

### 4.1 输入输出契约（纯 POJO，不依赖框架）

```java
public interface SchedulingEngine {
    EngineResult solve(EngineInput input, ProgressListener listener);
}

record EngineInput(
    List<OrderSnapshot> orders,       // 已按优先级排序
    Map<Long, ResourceSnapshot> resources,
    Map<Long, List<CalendarSlot>> calendar,   // 资源 -> 可工作时间段（已展开停机）
    Map<String, List<Long>> resourceByCapability,
    SetupMatrix setupMatrix,
    EngineParams params                // 时间窗、冻结区、权重、正/倒排
) {}

record EngineResult(
    List<PlannedTask> tasks,
    List<UnscheduledOrder> unscheduled,
    PlanScore score,
    List<ConstraintViolation> violations
) {}
```

### 4.2 启发式算法骨架

```
1. 预处理
   a. 展开订单 → 工序实例（工时 = qty × std_time）
   b. 资源日历展开为可用时间段数组（去掉停机、非工作日）
   c. 按优先级 + 交期排序订单

2. 逐单逐工序排产（正排）
   for order in orders:
     prev_end = max(now, freeze_end, order.material_ready)
     for op in order.operations (按 seq):
       candidates = resources matching op.resource_group
       if candidates empty: unschedule(order, "RES_002"); break
       best = null
       for res in candidates sorted by (负荷低, 换型少, 距离近):
         slot = find_earliest_slot(res, prev_end, op.duration, op.setup, order.family)
         if slot != null and score(slot) > score(best): best = slot
       if best == null:
         unschedule(order, "CAP_005"); break
       assign(op, best)
       prev_end = best.end
     if order.end > order.due: mark_late(order)   -- 仍保留，计入软约束惩罚

3. 局部改进（可选，限时）
   a. 换型合并：同资源相邻任务按 family 聚类，尝试交换顺序降低 setup
   b. 负荷均衡：超载资源上的任务尝试迁移到低负荷同能力资源
   c. 每次改动后跑增量约束校验，失败则回滚

4. 硬约束校验（独立组件，全量跑一遍）
5. 输出任务、未排清单、得分、违规列表
```

### 4.3 关键实现点

| 问题 | 方案 |
|---|---|
| `find_earliest_slot` 性能 | 每个资源维护**有序空闲区间列表**（`TreeMap<Instant, Interval>`），查询 O(log n + k)，避免逐分钟扫描 |
| 候选评估的副作用 | 查询（peek）与占用（commit）必须分离。评估多个候选资源时不能修改空闲区间，否则会出现“评估即占用”，实际只用到第一个资源（原型中已踩坑并修复） |
| 换型时间 | 取该资源上“前一个任务的 family → 当前 family”的 setup，首个任务用默认 setup |
| 冻结区 | 冻结区内的已发布任务先占位，新任务只能排在冻结区之后 |
| 批量 | 工时按 `qty × std_time`，若 `qty < min_batch` 按 min_batch 计 |
| 可中断 | 每处理 N 个订单检查取消标志，写入进度 |
| 结果确定性 | 同输入同参数结果稳定：候选排序用稳定的 tie-breaker（资源 code），禁用哈希顺序依赖 |

### 4.4 复杂度估算

- 1 万工序、6 类资源、每工序平均 3 个候选：约 3 万次 slot 查询，每次 O(log n)。
- 单线程预估 20–40 秒；加 4 线程按资源组并行可压到 10 秒内。**5 分钟目标有充足余量。**

### 4.5 约束校验器

```java
public final class ConstraintValidator {
    List<ConstraintViolation> validate(Plan plan);   // 全量
    List<ConstraintViolation> validateDelta(Plan plan, List<Long> changedTaskIds); // 拖拽时增量
}
```
P0 五类硬约束：工序顺序、资源互斥、资源日历、物料齐套时点、模具唯一占用。
拖拽调整走 `validateDelta`，只校验受影响资源与订单，保证 300ms 内返回。

---

## 5. API 契约

### 5.1 约定

- REST + JSON，`/api/v1`，统一响应 `{code, message, data, traceId}`
- 分页：`?page=1&size=20`，返回 `{items,total,page,size}`
- 时间：ISO-8601 带时区，如 `2024-11-18T08:00:00+08:00`
- 错误码：`ORD_001`、`CAP_005` 等，与未排原因码共用一套字典

### 5.2 核心接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/orders/sync` | 触发 ERP 同步，返回任务 id |
| GET | `/orders` | 分页查询，支持 status/dueFrom/dueTo/priority/keyword |
| GET | `/orders/{id}` | 详情（含工艺展开、齐套、计划结果） |
| POST | `/orders/{id}/split` | 拆分，body `{items:[{qty,dueDate}]}` |
| PUT | `/orders/{id}/priority` | 改优先级 |
| GET | `/master/resources/tree` | 资源树 |
| PUT | `/master/resources/{id}` | 更新资源 |
| POST | `/master/import` | Excel 导入，返回校验报告 |
| POST | `/scheduling/runs` | 发起排产，body 为 `EngineParams` |
| GET | `/scheduling/runs/{id}` | 进度与状态 |
| POST | `/scheduling/runs/{id}/cancel` | 取消 |
| GET | `/plans/{id}/tasks` | 甘特数据（时间窗、资源过滤） |
| PATCH | `/plans/{id}/tasks/{taskId}` | 拖拽调整 `{resourceId,startTs}`，返回校验结果 |
| POST | `/plans/{id}/tasks/{taskId}/lock` | 锁定/解锁 |
| POST | `/plans/{id}/validate` | 发布前校验 |
| POST | `/plans/{id}/publish` | 发布 |
| POST | `/plans/{id}/dispatch` | 下达 MES |
| GET | `/analysis/load` | 负荷分析 |
| GET | `/analysis/unscheduled` | 未排清单 |

### 5.3 甘特数据接口示例

```json
GET /api/v1/plans/1201/tasks?from=2024-11-18T00:00:00%2B08:00&to=2024-11-25T00:00:00%2B08:00&resourceIds=1,2,3

{
  "code": 0,
  "data": {
    "resources": [
      {"id": 1, "code": "SMT-01", "name": "贴片机 1 号", "parentName": "SMT 车间"}
    ],
    "tasks": [
      {
        "id": 88001, "orderNo": "SO-2024-1183", "opCode": "OP20", "opName": "SMT 贴片",
        "resourceId": 1, "start": "2024-11-18T16:00:00+08:00", "end": "2024-11-18T22:00:00+08:00",
        "locked": false, "conflict": false, "family": "SM"
      }
    ]
  }
}
```

> 甘特接口只返回渲染必要字段；点开详情再单独查 `/tasks/{id}`，避免首屏传输决策数据。

---

## 6. 前端甘特方案

| 决策 | 方案 |
|---|---|
| 渲染 | Canvas 2D 自绘，DOM 只保留工具栏与详情面板 |
| 数据量 | 视口裁剪：只绘制可见时间窗 + 可见资源行，5000 块无压力 |
| 交互 | 命中检测用任务矩形索引（按行分桶，O(1) 定位） |
| 拖拽 | `pointerdown` → 拖影层 → `pointerup` 调 PATCH，300ms 内回显校验结果 |
| 缩放 | 日/周/月三档像素密度，缩放时只重算坐标不重取数据 |
| 时间轴 | 统一用 `epoch ms`，避免时区与夏令时问题 |
| 依赖关系 | P0 不画工序连线，P1 再加 |

---

## 7. 性能与容量

| 场景 | 目标 | 手段 |
|---|---|---|
| 1 万工序排产 | ≤5 min | 空闲区间树 + 资源组并行 |
| 甘特首屏 5000 块 | ≤1.5 s | 视口裁剪 + 轻量 DTO + 索引 |
| 拖拽校验 | ≤300 ms | 增量校验，只算受影响资源与订单 |
| 订单列表 10 万行 | ≤500 ms | 复合索引 + 分页，禁止全表 count（用估算或缓存） |
| Excel 导入 5000 行 | ≤60 s | 流式解析 + 批量 insert（每 500 条） |

**容量估算（单厂）**：订单 10 万/年，工序 80 万/年，plan_task 按版本保留 12 个 → 约 100 万行/年。PostgreSQL 单表完全够用，P0 不做分区；超过 500 万行再按 `version_id` 分区。

---

## 8. 安全与权限

- 认证：JWT + 刷新令牌；密码 BCrypt。
- 权限：RBAC + 数据权限（`plant_id` / `workshop_id`），在 MyBatis 拦截器统一注入。
- 审计：`audit_log` 记录操作人、对象、前值/后值、时间；排产发布、拖拽调整、主数据变更必须留痕。
- 接口幂等：同步与下达用 `src_id` 唯一约束 + 幂等键。

---

## 9. 部署

```yaml
# docker-compose.yml 结构
services:
  web:      # Nginx + 静态资源
  app:      # Spring Boot，2 副本起
  db:       # PostgreSQL 16，主备
  redis:    # Redis 7
```

- 单厂 P0：单机 8C16G 可跑（app 4G、db 4G、redis 1G）。
- 多厂：app 无状态水平扩展，排产任务按工厂路由，避免同厂并发排产（Redis 分布式锁 `sched:{plantId}`）。

---

## 10. 演进路径

| 阶段 | 技术动作 |
|---|---|
| P0 | 单体 + 启发式 + Canvas 甘特 |
| P1 | 引擎拆独立服务（gRPC）；引入增量重排（保留上次结果作为初始解）；决策轨迹可视化 |
| P2 | OR-Tools CP-SAT 作为二次优化；换型时间学习；多工厂分片；K8s |

**拆分时机**：当排产任务开始影响 API 响应（同进程 GC/CPU 争抢）时拆，不要提前拆。

---

## 11. 落地顺序建议

1. 建库建表 + 主数据 CRUD + Excel 导入（第 1–2 周）
2. 订单接入 + 订单池 + 齐套（第 2–4 周）
3. 引擎：日历展开 → 单资源 slot 查找 → 全量排产 → 约束校验（第 4–8 周）
4. 甘特只读 → 拖拽 → 锁定（第 8–12 周）
5. 发布审批 + 下达 + 分析（第 12–15 周）
6. 真实数据压测与调优（第 15–16 周）

> 引擎和甘特是两条独立主线，第 4 周起可并行；但 `plan_task` 的 DTO 必须先定稿，否则前端会返工。

---

## 12. 算法原型验证

`prototypes/scheduler_demo.py` 用纯 Python（无依赖）实现了 4.2 节的启发式骨架，可直接运行验证：

```bash
python3 prototypes/scheduler_demo.py
```

实测结果（本机单线程）：

| 场景 | 规模 | 耗时 | 结果 |
|---|---|---|---|
| 演示 | 24 单 × 3 工序 = 72 任务，6 资源 | 2.4 ms | 72/72 排上，硬约束 0 违反 |
| 压测 | 400 单 × 5 工序 = 2000 任务，12 资源 | 91 ms | 951 排上 / 241 未排（窗口产能不足），硬约束 0 违反 |

吞吐约 **1 万任务/秒**。按此推算，1 万工序的主要耗时不在算法本身，而在数据加载与落库；5 分钟目标余量充足。

原型暴露的一个真实问题已写回 4.3：**候选资源评估必须无副作用**，否则会“评估即占用”，导致只用第一个资源、产能利用率腰斩。这类问题在 Java 实现里同样会出现，评审时需重点检查。
