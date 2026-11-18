"""
APS 排产引擎 · 启发式算法原型 v2（已按评审报告 B1–B3 修正）

不依赖第三方库：python3 prototypes/scheduler_demo.py

v2 相对 v1 的修正
  B1 换型真正占位：换型作为独立区间写入资源时间轴，前驱取「时间上相邻」的任务，
     同族显式 0 换型，矩阵缺项回落到工序准备时间并计数告警。
  B2 工序可跨班次：加工时长在日历内累计消耗，任务产出多段占用（segs）。
  B3 订单原子排产：逐单暂存，全部工序成功才提交，失败整单回滚并记录首个卡点。
  B4 未排原因结构化：order / op / reason / detail 四元组，可直接落 plan_unscheduled。
  I6 duration = ceil(max(qty, min_batch) × std_time / efficiency)；capacity>1 显式拒绝。
  V  校验器新增时间轴守恒断言：加工 + 换型 + 停机互不重叠、且不越出日历。

约束口径
  - 加工可跨班次、跨天；换型必须整段落在同一工作时段内。
  - 资源互斥、工序顺序、日历边界、同族免换型为硬约束。
"""

from __future__ import annotations

import math
import random
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Optional

BASE = datetime(2024, 11, 18, 0, 0)
DAY_MIN = 24 * 60


def fmt(m: int) -> str:
    return (BASE + timedelta(minutes=m)).strftime("%m-%d %H:%M")


# ---------------------------------------------------------------- 数据模型
@dataclass
class Operation:
    seq: int
    code: str
    capability: str
    std_time_min: float          # 单件工时
    setup_min: int = 0           # 无换型矩阵时的默认准备时间
    min_batch: int = 1


@dataclass
class Order:
    no: str
    family: str
    due: int                     # 相对 BASE 的分钟
    priority: int                # 1 最高
    qty: int
    ops: list[Operation]
    material_ready: int = 0


@dataclass
class Resource:
    id: str
    capability: str
    windows: list[tuple[int, int]]
    free: list[list[int]]
    efficiency: float = 1.0
    capacity: int = 1
    tasks: list["Task"] = field(default_factory=list)
    setup_warnings: int = 0

    def __post_init__(self):
        if self.capacity != 1:
            raise ValueError(f"{self.id}: P0 仅支持单机容量，capacity>1 请拆成独立机台")


@dataclass
class Task:
    order: str
    op: str
    resource: str
    family: str
    segs: list[tuple[int, int]]          # 加工分段
    setup: Optional[tuple[int, int]] = None
    late: bool = False

    @property
    def start(self) -> int:
        return self.segs[0][0]

    @property
    def end(self) -> int:
        return self.segs[-1][1]

    @property
    def proc_minutes(self) -> int:
        return sum(e - s for s, e in self.segs)


@dataclass
class Unscheduled:
    order: str
    op: str
    reason: str
    detail: str


# ---------------------------------------------------------------- 时间工具
def build_windows(days: int, shifts: list[tuple[int, int]]) -> list[tuple[int, int]]:
    """班次按小时给出，展开为相对 BASE 的分钟窗口。"""
    ws: list[tuple[int, int]] = []
    for d in range(days):
        base = d * DAY_MIN
        for sh, eh in shifts:
            ws.append((base + sh * 60, base + eh * 60))
    return ws


def next_free_start(free: list[list[int]], earliest: int) -> Optional[int]:
    """把起点推进到该资源最早可用的时刻（跳过已被占用的区间）。"""
    for a, b in free:
        if b <= earliest:
            continue
        return earliest if a <= earliest else a
    return None


def peek_single(free: list[list[int]], earliest: int, dur: int) -> Optional[int]:
    """要求整段落在同一空闲区间内（用于换型）。"""
    for a, b in free:
        s = max(a, earliest)
        if s + dur <= b:
            return s
    return None


def peek_span(free: list[list[int]], earliest: int, dur: int) -> Optional[list[tuple[int, int]]]:
    """允许跨区间累计消耗（用于加工）。返回分段占用，不修改 free。"""
    segs: list[tuple[int, int]] = []
    remain = dur
    cur = earliest
    for a, b in free:
        if b <= cur:
            continue
        s = max(a, cur)
        if s >= b:
            continue
        take = min(b - s, remain)
        if take > 0:
            segs.append((s, s + take))
            remain -= take
            cur = s + take
        if remain == 0:
            return segs
    return None


def cut(free: list[list[int]], s: int, e: int) -> None:
    out: list[list[int]] = []
    for a, b in free:
        if b <= s or a >= e:
            out.append([a, b])
        else:
            if a < s:
                out.append([a, s])
            if b > e:
                out.append([e, b])
    free[:] = out


def merge_insert(free: list[list[int]], s: int, e: int) -> None:
    free.append([s, e])
    free.sort()
    merged: list[list[int]] = []
    for a, b in free:
        if merged and a <= merged[-1][1]:
            merged[-1][1] = max(merged[-1][1], b)
        else:
            merged.append([a, b])
    free[:] = merged


def covered(windows: list[tuple[int, int]], s: int, e: int) -> bool:
    """[s,e) 是否完全落在工作时段并集内。"""
    cur = s
    for a, b in windows:
        if b <= cur:
            continue
        if a > cur:
            return False
        cur = max(cur, b)
        if cur >= e:
            return True
    return cur >= e


# ---------------------------------------------------------------- 引擎
class Scheduler:
    def __init__(self, resources: list[Resource],
                 setup_matrix: dict[tuple[str, str, str], int],
                 plan_start: int = 0, freeze_end: int = 0):
        self.resources = {r.id: r for r in resources}
        self.by_cap: dict[str, list[Resource]] = {}
        for r in resources:
            self.by_cap.setdefault(r.capability, []).append(r)
        for group in self.by_cap.values():
            group.sort(key=lambda x: x.id)
        self.setup_matrix = setup_matrix
        self.plan_start = plan_start
        self.freeze_end = freeze_end
        self.tasks: list[Task] = []
        self.unscheduled: list[Unscheduled] = []
        self.horizon_end = max((b for r in resources for _, b in r.windows), default=0)

    # ---- 换型
    def _prev_family(self, res: Resource, at: int) -> Optional[str]:
        prev = [t for t in res.tasks if t.end <= at]
        return max(prev, key=lambda t: t.end).family if prev else None

    def _setup_minutes(self, res: Resource, prev: Optional[str], family: str, base: int) -> int:
        if prev is None:
            return base
        if prev == family:
            return 0
        key = (res.id, prev, family)
        if key in self.setup_matrix:
            return self.setup_matrix[key]
        res.setup_warnings += 1
        return base

    # ---- 候选评估（无副作用）
    def _evaluate(self, res: Resource, earliest: int, op: Operation,
                  qty: int, family: str) -> Optional[tuple[int, int, list[tuple[int, int]]]]:
        dur = math.ceil(max(qty, op.min_batch) * op.std_time_min / res.efficiency)
        st = next_free_start(res.free, earliest)
        if st is None:
            return None
        setup = 0
        for _ in range(3):
            prev = self._prev_family(res, st)
            setup = self._setup_minutes(res, prev, family, op.setup_min)
            if setup == 0:
                break
            pos = peek_single(res.free, st, setup)
            if pos is None:
                return None
            if pos == st:
                break
            st = pos
        segs = peek_span(res.free, st + setup, dur)
        if segs is None:
            return None
        return st, setup, segs

    # ---- 提交 / 回滚
    def _commit(self, res: Resource, st: int, setup: int,
                segs: list[tuple[int, int]]) -> Task:
        if setup > 0:
            cut(res.free, st, st + setup)
        for s, e in segs:
            cut(res.free, s, e)
        return Task(resource=res.id, order="", op="", family="",
                    segs=segs, setup=(st, st + setup) if setup > 0 else None)

    def _rollback(self, pending: list[tuple[Resource, Task]]) -> None:
        for res, task in reversed(pending):
            res.tasks.remove(task)
            if task.setup:
                merge_insert(res.free, *task.setup)
            for s, e in task.segs:
                merge_insert(res.free, s, e)

    # ---- 单订单原子排产
    def _place_order(self, order: Order) -> None:
        if order.material_ready >= self.horizon_end:
            self.unscheduled.append(Unscheduled(order.no, "-", "MAT_001",
                                                f"物料齐套时点 {fmt(order.material_ready)} 超出计划窗口"))
            return

        cursor = max(self.plan_start, self.freeze_end, order.material_ready)
        pending: list[tuple[Resource, Task]] = []

        for op in order.ops:
            cands = self.by_cap.get(op.capability, [])
            if not cands:
                self._rollback(pending)
                self.unscheduled.append(Unscheduled(order.no, op.code, "RES_002",
                                                    f"能力 {op.capability} 无可用资源"))
                return

            best = None
            for res in cands:
                ev = self._evaluate(res, cursor, op, order.qty, order.family)
                if ev is None:
                    continue
                st, setup, segs = ev
                key = (segs[-1][1], setup, res.id)      # 最早完工 → 少换型 → 资源编码
                if best is None or key < best[0]:
                    best = (key, res, st, setup, segs)

            if best is None:
                self._rollback(pending)
                self.unscheduled.append(Unscheduled(
                    order.no, op.code, "CAP_005",
                    f"窗口内无可容纳 {math.ceil(max(order.qty, op.min_batch) * op.std_time_min)} 分钟的时段"))
                return

            _, res, st, setup, segs = best
            task = self._commit(res, st, setup, segs)
            task.order = order.no
            task.op = op.code
            task.family = order.family
            task.late = segs[-1][1] > order.due
            res.tasks.append(task)
            pending.append((res, task))
            cursor = segs[-1][1]

        self.tasks.extend(t for _, t in pending)

    def run(self, orders: list[Order]) -> tuple[list[Task], list[Unscheduled]]:
        for order in sorted(orders, key=lambda o: (o.priority, o.due, o.no)):
            self._place_order(order)
        for r in self.resources.values():
            r.tasks.sort(key=lambda t: t.start)
        self.tasks.sort(key=lambda t: (t.resource, t.start))
        return self.tasks, self.unscheduled


# ---------------------------------------------------------------- 校验器
def validate(tasks: list[Task], orders: list[Order],
             resources: dict[str, Resource]) -> list[str]:
    errors: list[str] = []

    # 1) 时间轴守恒：加工 + 换型 不重叠、不越界
    by_res: dict[str, list[tuple[int, int, str, Task]]] = {}
    for t in tasks:
        for s, e in t.segs:
            by_res.setdefault(t.resource, []).append((s, e, "PROC", t))
        if t.setup:
            by_res.setdefault(t.resource, []).append((t.setup[0], t.setup[1], "SETUP", t))

    for rid, blocks in by_res.items():
        blocks.sort()
        for (s1, e1, k1, t1), (s2, e2, k2, t2) in zip(blocks, blocks[1:]):
            if s2 < e1:
                errors.append(f"资源冲突 {rid}: {t1.order}/{t1.op}({k1}) 与 {t2.order}/{t2.op}({k2}) 重叠")
        res = resources[rid]
        for s, e, k, t in blocks:
            if not covered(res.windows, s, e):
                errors.append(f"越出日历 {rid}: {t.order}/{t.op} {k} {fmt(s)}-{fmt(e)}")

    # 2) 换型必须整段落在日历内（上面已覆盖），且不得跨班次
    for t in tasks:
        if not t.setup:
            continue
        s, e = t.setup
        if not any(a <= s and e <= b for a, b in resources[t.resource].windows):
            errors.append(f"换型跨班次 {t.resource}: {t.order}/{t.op} {fmt(s)}-{fmt(e)}")

    # 3) 订单完整性与工序顺序（不得过滤缺失工序）
    by_order: dict[str, list[Task]] = {}
    for t in tasks:
        by_order.setdefault(t.order, []).append(t)

    for o in orders:
        got = by_order.get(o.no, [])
        if not got:
            # 整单未排是合法终态，由 plan_unscheduled 记录；这里只校验“排了一半”
            continue
        if len(got) != len(o.ops):
            errors.append(f"订单未完整排产 {o.no}: {len(got)}/{len(o.ops)} 道工序")
            continue
        seq = [t.op for t in sorted(got, key=lambda t: t.start)]
        expect = [op.code for op in o.ops]
        if seq != expect:
            errors.append(f"工序顺序错误 {o.no}: {seq} != {expect}")

    return errors


# ---------------------------------------------------------------- 回归用例
def _base_resource(rid: str, windows: list[tuple[int, int]], cap: str = "X",
                   eff: float = 1.0) -> Resource:
    return Resource(rid, cap, windows, [list(w) for w in windows], efficiency=eff)


def case_setup_occupies_time() -> tuple[bool, str]:
    """B1：换型必须占据资源时间轴，后续任务不得压入换型窗口。"""
    res = _base_resource("R1", [(8 * 60, 20 * 60)])
    a = Order("A1", "FA", 20 * 60, 1, 1, [Operation(10, "OP10", "X", 60, 0)], material_ready=0)
    b = Order("B1", "FB", 20 * 60, 2, 1, [Operation(10, "OP10", "X", 120, 0)], material_ready=0)
    eng = Scheduler([res], {("R1", "FA", "FB"): 60})
    eng.run([a, b])
    btask = next((t for t in eng.tasks if t.order == "B1"), None)
    if btask is None:
        return False, "B1 未排上"
    if btask.setup != (9 * 60, 10 * 60):
        return False, f"换型未占位，期望 09:00-10:00，实际 {btask.setup and (fmt(btask.setup[0]), fmt(btask.setup[1]))}"
    if btask.start != 10 * 60:
        return False, f"加工起点应在 10:00，实际 {fmt(btask.start)}"
    errs = validate(eng.tasks, [a, b], eng.resources)
    return (not errs), "换型 09:00-10:00 已占位，加工 10:00 开始" if not errs else str(errs)


def case_cross_shift() -> tuple[bool, str]:
    """B2：600 分钟工序在 8 小时单班日历下必须能跨天排上，而不是 CAP_005。"""
    windows = build_windows(3, [(8, 16)])
    res = _base_resource("R1", windows)
    o = Order("SO-1", "F", 3 * DAY_MIN, 3, 1, [Operation(10, "OP10", "X", 600, 0)])
    eng = Scheduler([res], {})
    eng.run([o])
    if not eng.tasks:
        return False, f"长工序被误判未排：{[(u.reason, u.detail) for u in eng.unscheduled]}"
    t = eng.tasks[0]
    if t.proc_minutes != 600:
        return False, f"加工时长错误 {t.proc_minutes}"
    if len(t.segs) < 2:
        return False, "未产生跨班次分段"
    errs = validate(eng.tasks, [o], eng.resources)
    return (not errs), f"跨 {len(t.segs)} 段完成 600 分钟：{fmt(t.start)} → {fmt(t.end)}"


def case_order_atomic() -> tuple[bool, str]:
    """B3：订单某道工序排不下时，前面已排工序必须整单回滚。"""
    res = _base_resource("R1", [(8 * 60, 10 * 60)])       # 只有 120 分钟
    o = Order("SO-2", "F", 2 * DAY_MIN, 3, 1, [
        Operation(10, "OP10", "X", 90, 0),
        Operation(20, "OP20", "X", 90, 0),
    ])
    eng = Scheduler([res], {})
    eng.run([o])
    if eng.tasks:
        return False, f"未排订单仍残留 {len(eng.tasks)} 个任务"
    if not any(u.reason == "CAP_005" for u in eng.unscheduled):
        return False, "未记录 CAP_005"
    if res.free != [list(res.windows[0])]:
        return False, f"回滚未还原空闲区间：{res.free}"
    return True, "整单回滚，资源空闲区间完整还原"


def case_same_family_no_setup() -> tuple[bool, str]:
    """同族连续生产不应产生换型。"""
    res = _base_resource("R1", [(8 * 60, 20 * 60)])
    a = Order("A1", "FA", 20 * 60, 1, 1, [Operation(10, "OP10", "X", 60)])
    b = Order("A2", "FA", 20 * 60, 2, 1, [Operation(10, "OP10", "X", 60)])
    eng = Scheduler([res], {})
    eng.run([a, b])
    btask = next(t for t in eng.tasks if t.order == "A2")
    return (btask.setup is None), f"第二个同族任务换型={btask.setup}"


def case_validator_catches_overlap() -> tuple[bool, str]:
    """负向用例：人为制造重叠，校验器必须报错。"""
    res = _base_resource("R1", [(0, DAY_MIN)])
    t1 = Task("A", "OP10", "R1", "F", [(0, 120)])
    t2 = Task("B", "OP10", "R1", "F", [(60, 180)])
    errs = validate([t1, t2], [], {"R1": res})
    return (bool(errs)), (errs[0] if errs else "未检出重叠")


def run_tests() -> bool:
    cases = [
        ("B1 换型占位", case_setup_occupies_time),
        ("B2 跨班次加工", case_cross_shift),
        ("B3 订单原子性", case_order_atomic),
        ("同族免换型", case_same_family_no_setup),
        ("校验器负向检测", case_validator_catches_overlap),
    ]
    print("=" * 68)
    print("回归用例")
    print("=" * 68)
    all_ok = True
    for name, fn in cases:
        try:
            ok, msg = fn()
        except Exception as e:                     # 用例自身异常也算失败
            ok, msg = False, f"{type(e).__name__}: {e}"
        all_ok &= ok
        print(f"  [{'PASS' if ok else 'FAIL'}] {name:16s} {msg}")
    return all_ok


# ---------------------------------------------------------------- 演示 / 压测
def _families_matrix(resources: list[Resource], families: list[str]) -> dict[tuple[str, str, str], int]:
    m: dict[tuple[str, str, str], int] = {}
    for r in resources:
        for i, f1 in enumerate(families):
            for j, f2 in enumerate(families):
                if f1 != f2:
                    m[(r.id, f1, f2)] = 30 + 15 * abs(i - j)
    return m


def demo() -> None:
    windows = build_windows(7, [(8, 16), (16, 24)])
    resources = [
        _base_resource("SMT-01", windows, "SMT", 0.95),
        _base_resource("SMT-02", windows, "SMT", 0.92),
        _base_resource("CNC-01", windows, "CNC", 0.88),
        _base_resource("CNC-02", windows, "CNC", 0.90),
        _base_resource("ASM-A", windows, "ASM", 1.0),
        _base_resource("ASM-B", windows, "ASM", 1.0),
    ]
    orders = []
    for i in range(24):
        family = "SM" if i % 3 else "RD"
        qty = 6 + i % 7
        orders.append(Order(
            no=f"SO-{1180+i}", family=family,
            due=(2 + i % 5) * DAY_MIN, priority=1 if i % 7 == 0 else 3, qty=qty,
            ops=[
                Operation(10, "OP10", "CNC", 18, 30),
                Operation(20, "OP20", "SMT", 6, 45),
                Operation(30, "OP30", "ASM", 22, 15),
            ],
        ))

    eng = Scheduler(resources, _families_matrix(resources, ["SM", "RD"]))
    t0 = time.perf_counter()
    tasks, unsched = eng.run(orders)
    elapsed = time.perf_counter() - t0

    errs = validate(tasks, orders, eng.resources)
    total_cap = sum(b - a for r in resources for a, b in r.windows)
    occupied = sum(t.proc_minutes + (t.setup[1] - t.setup[0] if t.setup else 0) for t in tasks)

    print("=" * 68)
    print("演示：24 张订单 × 3 道工序")
    print("=" * 68)
    print(f"排产耗时     : {elapsed*1000:.1f} ms")
    print(f"已排任务     : {len(tasks)}   未排订单: {len(unsched)}")
    print(f"硬约束校验   : {'通过' if not errs else '失败 ' + str(errs[:3])}")
    print(f"换型次数/时长: {sum(1 for t in tasks if t.setup)} 次 / "
          f"{sum(t.setup[1]-t.setup[0] for t in tasks if t.setup)} 分钟")
    print(f"资源占用率   : {occupied/total_cap*100:.0f}%  (加工+换型 / 总可用)")
    print()


def benchmark(n_orders: int = 300, n_ops: int = 5) -> None:
    random.seed(42)
    windows = build_windows(7, [(8, 16), (16, 24)])
    caps = ["CNC", "SMT", "ASM", "TEST", "PACK"]
    resources = [_base_resource(f"{c}-{j+1:02d}", windows, c, random.uniform(0.85, 1.0))
                 for c in caps for j in range(2)]
    families = ["SM", "RD", "PCB"]

    orders = []
    for i in range(n_orders):
        ops = [Operation(k * 10 + 10, f"OP{k*10+10}", random.choice(caps),
                         random.randint(15, 75), random.choice([0, 15, 30]))
               for k in range(n_ops)]
        orders.append(Order(no=f"SO-{i:05d}", family=random.choice(families),
                            due=(1 + i % 6) * DAY_MIN, priority=random.randint(1, 5),
                            qty=1, ops=ops))

    eng = Scheduler(resources, _families_matrix(resources, families))
    t0 = time.perf_counter()
    tasks, unsched = eng.run(orders)
    elapsed = time.perf_counter() - t0
    errs = validate(tasks, orders, eng.resources)

    total_cap = sum(b - a for r in resources for a, b in r.windows)
    occupied = sum(t.proc_minutes + (t.setup[1] - t.setup[0] if t.setup else 0) for t in tasks)
    complete = n_orders - len(unsched)

    print("=" * 68)
    print(f"压测：{n_orders} 张订单 × {n_ops} 道工序 = {n_orders*n_ops} 个任务，{len(resources)} 资源")
    print("=" * 68)
    print(f"排产耗时     : {elapsed*1000:.0f} ms")
    print(f"已排 / 未排  : {len(tasks)} / {len(unsched)} 订单")
    print(f"订单完整率   : {complete/n_orders*100:.1f}%")
    print(f"硬约束校验   : {'通过' if not errs else '失败 ' + str(errs[:2])}")
    print(f"换型         : {sum(1 for t in tasks if t.setup)} 次 / "
          f"{sum(t.setup[1]-t.setup[0] for t in tasks if t.setup)} 分钟")
    print(f"资源占用率   : {occupied/total_cap*100:.0f}%  (加工+换型 / 总可用)")
    print(f"吞吐         : {len(tasks)/max(elapsed,1e-9):,.0f} 任务/秒")
    print(f"换型告警     : {sum(r.setup_warnings for r in resources)} 次（矩阵缺项回落）")
    reasons: dict[str, int] = {}
    for u in unsched:
        reasons[u.reason] = reasons.get(u.reason, 0) + 1
    print(f"未排原因分布 : " + ", ".join(f"{k}={v}" for k, v in sorted(reasons.items())))


if __name__ == "__main__":
    ok = run_tests()
    print()
    demo()
    benchmark()
    print()
    print("回归用例:", "全部通过" if ok else "存在失败")
    sys.exit(0 if ok else 1)
