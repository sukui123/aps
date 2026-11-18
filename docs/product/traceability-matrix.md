# APS 排产系统 · P0 需求追溯矩阵

> 版本：v0.1
> 用途：把 45 项 P0 Must 功能与「Epic / 页面 / 接口 / 数据表」一一对应，缺口即返工点。
> 口径：功能 ID 来自 [P0 功能清单](./p0-backlog.md)，接口契约见 [技术方案](../tech/technical-design.md) §5，表结构以 [基线 DDL](../../db/migration/V1__p0_baseline.sql) 为准。

---

## 1. 统一功能域口径（解决 I9）

产品设计的架构图、章节标题与 P0 Epic 曾有三套划分，这里统一为**唯一口径**，其他地方引用本表：

| 域 | 对应 Epic | 接口前缀 | 主要页面 |
|---|---|---|---|
| 主数据与建模 | E1 | `/master` | 主数据 |
| 需求与订单 | E2 | `/orders` | 订单池、订单详情 |
| 排产引擎 | E3 | `/scheduling` | 排产任务、未排清单 |
| 计划工作台 | E4 | `/plans/{id}/tasks` | 计划甘特 |
| 执行与发布 | E5 | `/plans/{id}`（审批/发布/下达） | 发布审批 |
| 分析与排查 | E6 | `/analysis`、`/dict` | 工作台、负荷分析、未排清单 |
| 平台与集成 | E7 | `/auth`、`/users`、`/roles`、`/audit-logs`、`/integrations` | 登录、集成与权限 |

> 排除项：锁定/冻结（GB-07）已降 P1，不进入本矩阵。

---

## 2. 追溯矩阵

状态说明：**✅** 已闭环（页面 + 接口 + 表齐全）；**⚠️** 接口或表刚补齐，待工程验证；**➖** 前端纯交互，无独立接口。

### E1 主数据与建模

| ID | 功能 | 页面 | 接口 | 主表 | 状态 |
|---|---|---|---|---|---|
| MD-01 | 资源层级管理 | 主数据/资源 | `GET/POST /master/resources`、`GET /master/resources/tree` | `md_resource` | ✅ |
| MD-02 | 资源能力与产能 | 主数据/资源 | `PUT /master/resources/{id}` | `md_resource`、`md_resource_capability` | ✅ |
| MD-03 | 工艺路线维护 | 主数据/工艺 | `GET/POST /master/routings`、`PUT /master/routings/{id}` | `md_routing` | ✅ |
| MD-04 | 工序与工序网络 | 主数据/工艺 | `PUT /master/routings/{id}/operations` | `md_operation`、`md_operation_link` | ✅ |
| MD-05 | 标准工时与准备时间 | 主数据/工艺 | `PUT /master/operations/{id}` | `md_operation` | ✅ |
| MD-06 | 换型矩阵 | 主数据/换型 | `GET/PUT /master/setup-matrix` | `md_setup_matrix` | ✅ |
| MD-07 | 工厂日历与班次 | 主数据/日历 | `GET/POST/PUT /master/calendars` | `md_calendar`、`md_shift`、`md_calendar_day` | ✅ |
| MD-08 | 设备停机与维护窗口 | 主数据/日历 | `GET/POST /master/downtimes` | `md_downtime` | ✅ |
| MD-09 | Excel 批量导入 | 主数据（导入弹窗） | `GET /master/import/template`、`POST /master/import`、`GET /master/import/{id}/errors` | 导入暂存表/错误明细 | ⚠️ |
| MD-10 | 主数据完整性检查 | 主数据/体检 | `GET /master/validate` | `md_item`、`md_routing`、`md_operation` | ⚠️ |

> ⚠️ 导入错误暂存、体检结果落库表尚未在 DDL 中定义，工程实现时可先用接口内存返回，P1 再落表。

### E2 需求与订单

| ID | 功能 | 页面 | 接口 | 主表 | 状态 |
|---|---|---|---|---|---|
| OR-01 | 订单接入 | 订单池 | `POST /orders/sync`、`GET /orders/sync/{taskId}`、`POST /orders/import` | `ord_production_order` | ✅ |
| OR-02 | 订单池列表 | 订单池 | `GET /orders` | `ord_production_order` | ✅ |
| OR-03 | 订单详情 | 订单详情 | `GET /orders/{id}` | `ord_order_operation` | ✅ |
| OR-04 | 优先级维护 | 订单池 | `PUT /orders/{id}/priority`、`POST /orders/batch/priority` | `ord_production_order` | ✅ |
| OR-05 | 订单变更 | 订单详情 | `PUT /orders/{id}/due`、`PUT /orders/{id}/qty`、`POST /orders/{id}/split`、`POST /orders/{id}/merge`、`POST /orders/{id}/cancel` | `ord_production_order` | ✅ |
| OR-06 | 齐套检查 | 订单详情 | `GET /orders/{id}/material-requirements` | `ord_material_requirement`、`inv_stock`、`inv_incoming` | ✅ |
| OR-07 | 订单状态机 | 订单池 | `GET /orders/{id}/status` | `ord_production_order`、`plan_version` | ⚠️ |

> ⚠️ 订单“已排/已发布”状态从当前生效计划版本派生，不落死在订单表；接口需明确派生规则。

### E3 排产引擎

| ID | 功能 | 页面 | 接口 | 主表 | 状态 |
|---|---|---|---|---|---|
| SC-01 | 排产参数配置 | 排产任务 | `GET /scheduling/params`、`POST /scheduling/runs` | `plan_run.params` | ✅ |
| SC-02 | 全量排产执行 | 排产任务 | `POST /scheduling/runs`（Idempotency-Key） | `plan_run`、`plan_task` | ✅ |
| SC-03 | 任务进度与日志 | 排产任务 | `GET /scheduling/runs/{id}`、`GET /scheduling/runs/{id}/log` | `plan_run` | ✅ |
| SC-04 | 硬约束校验 | 引擎内部 + 发布校验 | `POST /plans/{id}/validate` | `plan_task`、`plan_task_segment` | ✅ |
| SC-05 | 软约束计分 | 排产任务 | `GET /scheduling/runs/{id}`（返回 `score`） | `plan_version.score` | ✅ |
| SC-06 | 批次与版本 | 发布审批 | `GET /scheduling/runs`、`GET /plans/{id}/versions` | `plan_run`、`plan_version` | ✅ |
| SC-07 | 版本回滚 | 发布审批 | `POST /plans/{id}/rollback` | `plan_version` | ✅ |
| SC-08 | 未排原因输出 | 未排清单 | `GET /analysis/unscheduled`、`GET /dict/reason-codes` | `plan_unscheduled`、`dict_reason_code` | ✅ |

### E4 计划工作台

| ID | 功能 | 页面 | 接口 | 主表 | 状态 |
|---|---|---|---|---|---|
| GB-01 | 资源泳道甘特 | 计划甘特 | `GET /plans/{id}/tasks` | `plan_task`、`plan_task_segment` | ✅ |
| GB-02 | 时间轴控制 | 计划甘特 | —（前端，复用 GB-01 数据） | — | ➖ |
| GB-03 | 筛选与高亮 | 计划甘特 | `GET /plans/{id}/tasks?from&to&resourceIds` | — | ✅ |
| GB-04 | 任务块详情 | 计划甘特 | `GET /plans/{id}/tasks/{taskId}` | `plan_task_trace` | ✅ |
| GB-05 | 拖拽调整 | 计划甘特 | `PATCH /plans/{id}/tasks/{taskId}?mode=check\|commit` | `plan_task` | ⚠️ |
| GB-06 | 冲突实时校验 | 计划甘特 | `PATCH ...?mode=check`、`POST /plans/{id}/tasks/{taskId}/validate` | `plan_task` | ✅ |
| GB-08 | 手工插单 | 计划甘特 | `POST /plans/{id}/tasks/manual` | `plan_task`（`source=MANUAL`） | ✅ |

> ⚠️ GB-05 的拖拽语义已定：**单任务试算、冲突拒绝、不做自动级联**；`mode=check` 不落库，`mode=commit` 才写。详见技术方案 §5.3。

### E5 执行与发布

| ID | 功能 | 页面 | 接口 | 主表 | 状态 |
|---|---|---|---|---|---|
| PB-01 | 发布前校验 | 发布审批 | `POST /plans/{id}/validate` | `plan_task`、`plan_unscheduled` | ✅ |
| PB-02 | 计划发布 | 发布审批 | `POST /plans/{id}/publish` | `plan_publish` | ✅ |
| PB-03 | 审批流 | 发布审批 | `POST /plans/{id}/submit`、`/approve`、`/reject` | `plan_approval` | ✅ |
| PB-04 | 工单下达 | 发布审批 | `POST /plans/{id}/dispatch`、`GET /plans/{id}/dispatch-records` | `dispatch_record`、`dispatch_item` | ✅ |
| PB-05 | 发布记录 | 发布审批 | `GET /plans/{id}/publish-record` | `plan_publish` | ✅ |

### E6 分析与排查

| ID | 功能 | 页面 | 接口 | 主表 | 状态 |
|---|---|---|---|---|---|
| AN-01 | 工作台首页 | 工作台 | `GET /analysis/kpi`、`GET /scheduling/runs` | —（聚合） | ✅ |
| AN-02 | 资源负荷分析 | 负荷分析 | `GET /analysis/load` | `plan_task`、`plan_task_segment` | ✅ |
| AN-03 | 未排原因分布 | 未排清单 | `GET /analysis/unscheduled`、`GET /dict/reason-codes` | `plan_unscheduled` | ✅ |
| AN-05 | 报表导出 | 各页导出按钮 | `POST /analysis/export` | — | ✅ |

### E7 平台与集成

| ID | 功能 | 页面 | 接口 | 主表 | 状态 |
|---|---|---|---|---|---|
| PF-01 | 登录与用户 | 全站 | `POST /auth/login`、`POST /auth/logout`、`GET /auth/me`、`GET/POST /users` | `sys_user` | ✅ |
| PF-02 | 权限模型 | 管理页 | `GET /roles`、`GET /permissions`、`PUT /roles/{id}/permissions`、`PUT /users/{id}/data-scopes` | `sys_role` 等 6 表 | ✅ |
| PF-03 | 操作审计 | 审计页 | `GET /audit-logs` | `audit_log` | ✅ |
| PF-04 | 集成管理 | 集成页 | `GET /integrations/tasks`、`POST /integrations/tasks/{id}/retry` | `idempotency_record`（部分） | ⚠️ |

> ⚠️ PF-04 的同步任务执行记录尚无独立表，P0 可用 `plan_run.message` + 日志兜底，P1 补 `integration_task`。

---

## 3. 缺口汇总（本轮已补齐接口）

评审 I4 指出的 12 处缺口，对应接口已在技术方案 §5.2 补齐：

| 缺口 | 补齐接口 |
|---|---|
| 登录与会话 | `POST /auth/login`、`/auth/logout`、`GET /auth/me` |
| 用户与角色 | `GET/POST /users`、`GET /roles`、`GET /permissions`、`PUT /roles/{id}/permissions` |
| 数据权限 | `PUT /users/{id}/data-scopes` |
| 审计查询 | `GET /audit-logs` |
| 集成管理 | `GET /integrations/tasks`、`POST /integrations/tasks/{id}/retry` |
| 审批提交/批准/退回 | `POST /plans/{id}/submit`、`/approve`、`/reject` |
| 版本列表与回滚 | `GET /plans/{id}/versions`、`POST /plans/{id}/rollback` |
| 发布记录与下达回执 | `GET /plans/{id}/publish-record`、`GET /plans/{id}/dispatch-records` |
| 订单改期/改量/合并/撤销 | `PUT /orders/{id}/due`、`/qty`、`POST /orders/{id}/merge`、`/cancel` |
| 批量改优先级 | `POST /orders/batch/priority` |
| 最近批次列表 | `GET /scheduling/runs` |
| 报表导出 | `POST /analysis/export` |

---

## 4. 仍未覆盖（非阻断）

| 项 | 说明 | 归属 |
|---|---|---|
| MD-09 / MD-10 的暂存与结果表 | 导入错误与体检结果的持久化 | P1 |
| PF-04 `integration_task` | 同步任务的独立执行记录表 | P1 |
| 订单状态派生规则 | “已排/已发布”从生效版本派生，需在详细设计中明确 | P0 详细设计 |
| 主数据版本有效期 | I5 剩余部分 | P1 |
