# P0 纵切参考实现（可运行）

## 这是什么

把 P0 主链路真正跑起来的**参考实现**：`发起排产 → 批次状态 → 甘特数据 → 未排原因 → 拖拽冲突校验`。

技术栈是 Python 标准库（无第三方依赖），因为当前沙箱没有 Java 工具链、也无法及时下载 JDK。**生产实现仍按 [技术方案](../docs/tech/technical-design.md) 的 Java 21 + Spring Boot 3**，本服务用于验证接口契约和算法行为，不替代生产代码。

## 运行

```bash
# 启动服务（默认 127.0.0.1:8080）
python3 service/app.py 8080

# 冒烟测试（自启服务、自跑HTTP请求、自动退出）
python3 service/smoke_test.py
```

## 已实现端点

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/scheduling/runs` | 发起排产，支持 `Idempotency-Key` |
| GET | `/api/v1/scheduling/runs` | 批次列表 |
| GET | `/api/v1/scheduling/runs/{id}` | 进度、状态、结果摘要 |
| GET | `/api/v1/plans/{id}/tasks` | 甘特数据（支持 from/to/resourceIds） |
| GET | `/api/v1/plans/{id}/tasks/{taskId}` | 任务详情 |
| PATCH | `/api/v1/plans/{id}/tasks/{taskId}?mode=check\|commit` | 拖拽试算 / 提交 |
| GET | `/api/v1/analysis/kpi` | 工作台 KPI |
| GET | `/api/v1/analysis/unscheduled` | 未排清单 |
| GET | `/api/v1/dict/reason-codes` | 原因码字典（8 个） |

## 冒烟覆盖（7 项全通过）

发起排产、幂等重放、排产结果（0 违反）、甘特数据与跨班次分段、未排原因码、拖拽冲突拒绝、工作台 KPI。

## 与生产实现的差异（不能带到生产）

| 项 | 参考实现 | 生产实现 |
|---|---|---|
| 状态 | 进程内存 | PostgreSQL（`V1__p0_baseline.sql`） |
| 排产 | 同步执行 | 异步任务 + 单飞约束 + 可取消 |
| 认证权限 | 无 | JWT + RBAC + 行级数据权限 |
| 拖拽校验 | 简化为同资源区间重叠 + 日历覆盖 | 增量约束校验，含前后邻居换型差量 |
| 并发与幂等 | 进程内 dict | `Idempotency-Key` + 数据库唯一约束 |
| 引擎 | 复用 `prototypes/scheduler_demo.py` | Java 纯 POJO 模块（`aps-engine`） |

## 下一步（Java 工程）

1. 建 Maven 多模块：`aps-engine`（纯 POJO 引擎，移植 v2 算法）、`aps-api`（Spring Boot 3）。
2. 跑 `db/migration/V1__p0_baseline.sql`，先做写操作冒烟（幂等索引、单飞索引、发布版本唯一）。
3. 按 [需求追溯矩阵](../docs/product/traceability-matrix.md) 的 45 项 Must 分派接口实现，优先 E1/E2/E3/E4 主链路。
