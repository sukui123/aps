# APS 排产系统

面向离散制造的有限产能、工序级排产系统。

## 文档

- [APS 排产系统 · 产品设计](docs/product/aps-product-design.md) — 产品定位、功能架构、数据模型、引擎设计、迭代路线
- [P0 功能清单与验收标准](docs/product/p0-backlog.md) — 可开发粒度的功能拆解、工作量、接口与里程碑
- [技术方案（P0）](docs/tech/technical-design.md) — 技术选型、数据模型 DDL、引擎算法、API 契约、甘特实现、性能边界
- [P0 方案评审报告](docs/review/p0-review.md) — 7 项阻断问题、10 项重要问题、修正计划与工期重估

## 原型

- [P0 交互原型](docs/prototype/p0-prototype.html) — 工作台、订单池、排产任务、计划甘特、未排清单、发布审批、主数据、负荷分析（单文件，浏览器直接打开；页面数值为布局示意）
- [排产算法原型](prototypes/scheduler_demo.py) — 启发式引擎最小实现，含硬约束校验与压测，`python3 prototypes/scheduler_demo.py` 直接运行

> 当前状态：方案评审未通过，需先完成评审报告中的 B1–B7 修正，再进入工程实现。
