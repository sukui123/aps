# Java 工程实现状态

> 更新：2026-11-20
> 状态：**引擎与 API 骨架已编译通过，6 个 JUnit 用例通过**

## 工程结构

```
aps/
├── pom.xml                      # 父工程，Java 17
├── aps-engine/                  # 纯 POJO 引擎，不依赖 Spring
│   ├── model/                   # Operation / Order / Resource / Task / Unscheduled
│   ├── Scheduler.java           # 启发式排产（v2 算法 Java 移植）
│   ├── TimeSlots.java           # 空闲区间、跨班次分段、换型占位
│   └── ConstraintValidator.java # 时间轴守恒与订单完整性校验
└── aps-api/                     # Spring Boot 3.2.5 骨架
    ├── ApsApplication.java
    └── SchedulingController.java
```

## 已验证

| 项 | 结果 |
|---|---|
| Maven 全量编译 | 通过（JDK 17.0.21 / Maven 3.9.16） |
| JUnit 回归 | 6/6 通过，0 失败 |
| B1 换型占位 | 通过 |
| B2 跨班次加工 | 通过 |
| B3 订单原子性 | 通过 |
| 同族免换型 | 通过 |
| 校验器负向检测 | 通过 |
| 压测 | 300 单 / 1375 任务 / 50 ms，硬约束 0 违反 |
| 可执行 jar | `aps-api/target/aps-api-0.1.0-SNAPSHOT.jar`（19 MB） |

## 构建与测试

沙箱内的工具链装在 `/tmp/aps-tools`（不入库）。常规环境只需 JDK 17 + Maven：

```bash
mvn test                       # 全量编译 + 测试
mvn -DskipTests package        # 生成 aps-api 可执行 jar
java -jar aps-api/target/aps-api-0.1.0-SNAPSHOT.jar
```

国内网络可加镜像配置：

```bash
mvn -s <settings-with-mirror> test
```

## API 已实现端点

| 方法 | 路径 |
|---|---|
| POST | `/api/v1/scheduling/runs`（支持 `Idempotency-Key` 重放） |
| GET | `/api/v1/scheduling/runs/{id}` |
| GET | `/api/v1/analysis/kpi` |

> 状态目前是内存态，尚未接入 PostgreSQL；生产接入按 `db/migration/V1__p0_baseline.sql`。

## 与参考实现的关系

`service/` 下的 Python 纵切服务用于快速验证接口契约，仍保留。
Java 工程是其生产对应物：**引擎逻辑一一对应**，但 API 只实现了 3 个端点，
后续按 [需求追溯矩阵](../product/traceability-matrix.md) 的 45 项 Must 补齐。

## 下一步

1. 接入 PostgreSQL，跑 `V1__p0_baseline.sql`，补 Flyway/Spring Data JDBC。
2. 把 `SchedulingController` 的内存态换成 `plan_run` / `plan_version` / `plan_task` 持久化。
3. 按追溯矩阵补齐 E1 主数据、E2 订单、E4 甘特、E5 发布审批端点。
4. 加 Spring Boot 集成测试（MockMvc）跑真实 HTTP 链路。
