# solver-python（tooling 占位）

**定位：参考实现 / 算法基准，不进入生产链路**（技术方案 §1.10、ADR-004）。

- P0 生产求解路径是 `aps-engine` 的 Java 自研启发式；本目录用 OR-Tools Python 做建模原型、基准回放与结果对照。
- 目录结构（后续迭代填充）：
  - `baselines/` — 固定基准数据集与期望指标
  - `reference/` — OR-Tools CP-SAT 参考模型
  - `reports/` — 对照报告输出（gitignore）
- 环境：`pip install -r requirements.txt`（建议独立 venv）。

本轮仅建立占位目录与依赖清单，不提供可运行求解脚本。
