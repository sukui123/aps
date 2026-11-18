"""
APS 排产引擎 · 启发式算法最小可运行原型

用途：验证《技术方案》4.2 节的算法骨架在真实数据量级下的可行性与性能。
不依赖任何第三方库，python3 prototypes/scheduler_demo.py 直接运行。

实现要点：
  1. 资源日历展开为「可用时间窗」数组
  2. 每个资源维护有序「空闲区间」列表，peek 只查询、commit 才切分（候选评估不允许有副作用）
  3. 正排：按 优先级 → 交期 排序订单，逐工序找最早可用槽位
  4. 换型时间按「该资源上一个任务的 product family → 当前 family」取值
  5. 未排原因码：RES_002（无候选资源）/ CAP_005（窗口内排不下）/ MAT_001（缺料）
  6. 硬约束校验：资源互斥、工序顺序、交期标记
"""

from __future__ import annotations

import random
import time
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Optional

BASE = datetime(2024, 11, 18, 0, 0)
MIN = timedelta(minutes=1)


# ---------------------------------------------------------------- 数据模型
@dataclass
class Operation:
    seq: int
    code: str
    capability: str
    duration_min: int
    setup_min: int = 0


@dataclass
class Order:
    no: str
    family: str
    due: datetime
    priority: int          # 1 最高
    ops: list[Operation]
    material_ready: datetime = BASE


@dataclass
class Resource:
    id: str
    capability: str
    free: list[list[datetime]] = field(default_factory=list)   # 空闲区间
    tasks: list["Task"] = field(default_factory=list)


@dataclass
class Task:
    order: str
    op: str
    resource: str
    start: datetime
    end: datetime
    family: str
    late: bool = False


@dataclass
class Unscheduled:
    order: str
    op: str
    reason: str


# ---------------------------------------------------------------- 日历
def build_free_windows(days: int, shifts: list[tuple[int, int]]) -> list[list[datetime]]:
    """展开 N 天的班次为连续可用时间窗（班次之间视为不可用）。"""
    windows: list[list[datetime]] = []
    for d in range(days):
        day0 = BASE + timedelta(days=d)
        for start_h, end_h in shifts:
            s = day0 + timedelta(hours=start_h)
            e = day0 + timedelta(hours=end_h)
            windows.append([s, e])
    return windows


def peek_slot(free: list[list[datetime]], earliest: datetime, need: timedelta) -> Optional[datetime]:
    """只查询、不修改空闲区间；用于比较候选资源。"""
    for s, e in free:
        start = max(s, earliest)
        if start + need <= e:
            return start
    return None


def commit_slot(free: list[list[datetime]], start: datetime, need: timedelta) -> None:
    """确认占用，切分空闲区间。只在选定资源上调用。"""
    for i, (s, e) in enumerate(free):
        if s <= start and start + need <= e:
            rest: list[list[datetime]] = []
            if start > s:
                rest.append([s, start])
            if start + need < e:
                rest.append([start + need, e])
            free[i:i + 1] = rest
            return
    raise RuntimeError(f"commit_slot 未找到匹配区间: {start} + {need}")


# ---------------------------------------------------------------- 引擎
class Scheduler:
    def __init__(self, resources: list[Resource], setup_matrix: dict[tuple[str, str, str], int],
                 plan_start: datetime, freeze_end: datetime):
        self.resources = {r.id: r for r in resources}
        self.by_cap: dict[str, list[Resource]] = {}
        for r in resources:
            self.by_cap.setdefault(r.capability, []).append(r)
        for group in self.by_cap.values():
            group.sort(key=lambda x: x.id)          # 稳定 tie-breaker
        self.setup_matrix = setup_matrix
        self.plan_start = plan_start
        self.freeze_end = freeze_end
        self.tasks: list[Task] = []
        self.unscheduled: list[Unscheduled] = []

    def _prev_family(self, res: Resource, at: datetime) -> str:
        prev = [t for t in res.tasks if t.end <= at]
        return prev[-1].family if prev else ""

    def _setup(self, res: Resource, at: datetime, family: str, base_setup: int) -> int:
        prev = self._prev_family(res, at)
        if not prev:
            return base_setup
        return self.setup_matrix.get((res.id, prev, family), 60)

    def run(self, orders: list[Order]) -> tuple[list[Task], list[Unscheduled]]:
        orders = sorted(orders, key=lambda o: (o.priority, o.due))
        for order in orders:
            if order.material_ready > self.freeze_end and order.material_ready > BASE + timedelta(days=7):
                self.unscheduled.append(Unscheduled(order.no, "-", "MAT_001"))
                continue

            cursor = max(self.plan_start, self.freeze_end, order.material_ready)
            placed_all = True

            for op in order.ops:
                candidates = self.by_cap.get(op.capability, [])
                if not candidates:
                    self.unscheduled.append(Unscheduled(order.no, op.code, "RES_002"))
                    placed_all = False
                    break

                best: Optional[tuple[datetime, Resource, int]] = None
                for res in candidates:
                    setup = self._setup(res, cursor, order.family, op.setup_min)
                    start = peek_slot(res.free, cursor + timedelta(minutes=setup),
                                      timedelta(minutes=op.duration_min))
                    if start is None:
                        continue
                    # 时间相同则选资源 code 小的，保证结果确定性
                    if best is None or start < best[0]:
                        best = (start, res, setup)

                if best is None:
                    self.unscheduled.append(Unscheduled(order.no, op.code, "CAP_005"))
                    placed_all = False
                    break

                start, res, setup = best
                commit_slot(res.free, start, timedelta(minutes=op.duration_min))
                end = start + timedelta(minutes=op.duration_min)
                task = Task(order.no, op.code, res.id, start, end, order.family,
                            late=end > order.due)
                res.tasks.append(task)
                self.tasks.append(task)
                cursor = end

            if placed_all and order.ops:
                pass  # 已按工序顺序排完

        for r in self.resources.values():
            r.tasks.sort(key=lambda t: t.start)
        self.tasks.sort(key=lambda t: (t.resource, t.start))
        return self.tasks, self.unscheduled


# ---------------------------------------------------------------- 校验
def validate(tasks: list[Task], orders: list[Order]) -> list[str]:
    errors: list[str] = []

    # 1. 资源互斥
    by_res: dict[str, list[Task]] = {}
    for t in tasks:
        by_res.setdefault(t.resource, []).append(t)
    for rid, group in by_res.items():
        group.sort(key=lambda t: t.start)
        for a, b in zip(group, group[1:]):
            if b.start < a.end:
                errors.append(f"资源冲突 {rid}: {a.order}/{a.op} 与 {b.order}/{b.op} 重叠")

    # 2. 工序顺序
    seq_of = {o.no: [x.code for x in o.ops] for o in orders}
    by_order: dict[str, list[Task]] = {}
    for t in tasks:
        by_order.setdefault(t.order, []).append(t)
    for ono, group in by_order.items():
        group.sort(key=lambda t: t.start)
        got = [t.op for t in group]
        expect = [c for c in seq_of.get(ono, []) if c in got]
        if got != expect:
            errors.append(f"工序顺序错误 {ono}: {got} != {expect}")

    return errors


# ---------------------------------------------------------------- 演示数据
def demo():
    cal = build_free_windows(7, [(8, 16), (16, 24)])
    resources = [
        Resource("SMT-01", "SMT", free=[w[:] for w in cal]),
        Resource("SMT-02", "SMT", free=[w[:] for w in cal]),
        Resource("CNC-01", "CNC", free=[w[:] for w in cal]),
        Resource("CNC-02", "CNC", free=[w[:] for w in cal]),
        Resource("ASM-A", "ASM", free=[w[:] for w in cal]),
        Resource("ASM-B", "ASM", free=[w[:] for w in cal]),
    ]
    setup = {
        ("SMT-01", "SM", "RD"): 45, ("SMT-01", "RD", "SM"): 45,
        ("SMT-02", "SM", "RD"): 45, ("SMT-02", "RD", "SM"): 45,
        ("CNC-01", "SM", "RD"): 90,
    }
    ops = [
        Operation(10, "OP10", "CNC", 18 * 60 // 60 * 8, 30),   # 8 件 × 18min
        Operation(20, "OP20", "SMT", 6 * 60, 45),
        Operation(30, "OP30", "ASM", 22 * 60 // 60 * 12, 15),
    ]
    orders = []
    for i in range(24):
        family = "SM" if i % 3 else "RD"
        orders.append(Order(
            no=f"SO-{1180+i}", family=family,
            due=BASE + timedelta(days=2 + i % 5),
            priority=1 if i % 7 == 0 else 3,
            ops=[Operation(o.seq, o.code, o.capability, o.duration_min, o.setup_min) for o in ops],
        ))

    eng = Scheduler(resources, setup, BASE, BASE)
    t0 = time.perf_counter()
    tasks, unsched = eng.run(orders)
    elapsed = time.perf_counter() - t0

    errors = validate(tasks, orders)
    late = sum(1 for t in tasks if t.late)
    print("=" * 62)
    print("演示：24 张订单 × 3 道工序 = 72 个任务")
    print("=" * 62)
    print(f"排产耗时      : {elapsed*1000:.1f} ms")
    print(f"已排任务      : {len(tasks)}")
    print(f"未排           : {len(unsched)}")
    print(f"硬约束校验    : {'通过' if not errors else '失败 ' + str(errors[:3])}")
    print(f"逾期任务      : {late}")
    print(f"资源利用      : " + ", ".join(
        f"{rid}={sum((t.end-t.start).total_seconds()/60 for t in ts)/ (7*16*60)*100:.0f}%"
        for rid, ts in sorted({r.id: [t for t in tasks if t.resource == r.id] for r in resources}.items())
    ))
    print()
    for t in tasks[:6]:
        print(f"  {t.order:10s} {t.op:5s} {t.resource:7s} "
              f"{t.start:%m-%d %H:%M} → {t.end:%m-%d %H:%M}")
    print("  ...")
    return errors


def benchmark(n_orders: int = 400, n_ops: int = 5):
    random.seed(42)
    cal = build_free_windows(7, [(8, 16), (16, 24)])
    caps = ["CNC", "SMT", "ASM", "TEST", "PACK"]
    resources = []
    for i, cap in enumerate(caps):
        for j in range(2):
            resources.append(Resource(f"{cap}-{j+1:02d}", cap, free=[w[:] for w in cal]))

    orders = []
    for i in range(n_orders):
        ops = [
            Operation(k * 10 + 10, f"OP{k*10+10}", random.choice(caps),
                      random.randint(20, 120), random.choice([0, 15, 30]))
            for k in range(n_ops)
        ]
        orders.append(Order(
            no=f"SO-{i:05d}",
            family=random.choice(["SM", "RD", "PCB"]),
            due=BASE + timedelta(days=random.randint(1, 6)),
            priority=random.randint(1, 5),
            ops=ops,
        ))

    eng = Scheduler(resources, {}, BASE, BASE)
    t0 = time.perf_counter()
    tasks, unsched = eng.run(orders)
    elapsed = time.perf_counter() - t0
    errors = validate(tasks, orders)

    print()
    print("=" * 62)
    print(f"压测：{n_orders} 张订单 × {n_ops} 道工序 = {n_orders*n_ops} 个任务，12 个资源")
    print("=" * 62)
    print(f"排产耗时      : {elapsed*1000:.0f} ms")
    print(f"已排 / 未排   : {len(tasks)} / {len(unsched)}")
    print(f"硬约束校验    : {'通过' if not errors else '失败'}")
    print(f"吞吐          : {len(tasks)/max(elapsed,1e-9):,.0f} 任务/秒")
    return elapsed, len(errors)


if __name__ == "__main__":
    demo()
    benchmark()
