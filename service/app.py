"""
APS 排产系统 · P0 纵切参考实现（可运行）

说明：
  生产目标技术栈是 Java 21 + Spring Boot 3（见 docs/tech/technical-design.md）。
  当前沙箱没有 Java 工具链且无法及时下载，本服务用 Python 标准库实现一个
  **可运行的参考实现**，用于验证「发起排产 → 甘特 → 未排原因 → 拖拽校验」主链路。

  不依赖任何第三方包：python3 service/app.py [port]
  冒烟测试：python3 service/smoke_test.py
"""

from __future__ import annotations

import json
import sys
import uuid
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "prototypes"))
from scheduler_demo import (  # noqa: E402
    Operation, Order, Resource, Scheduler, build_windows, covered, validate,
)

TZ = timezone(timedelta(hours=8))
BASE = datetime(2024, 11, 18, tzinfo=TZ)


def iso(minutes: int) -> str:
    return (BASE + timedelta(minutes=minutes)).isoformat()


def to_minutes(text: str) -> int:
    dt = datetime.fromisoformat(text)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=TZ)
    return int((dt - BASE).total_seconds() // 60)


REASON_CODES = [
    {"code": "MAT_001", "level": "ORDER", "name": "缺料", "suggestion": "确认到料计划或调整交期"},
    {"code": "RES_002", "level": "OPERATION", "name": "无可用资源", "suggestion": "维护资源能力或替代资源"},
    {"code": "HOR_003", "level": "ORDER", "name": "超出计划展望期", "suggestion": "纳入后续计划周期"},
    {"code": "BOM_004", "level": "ORDER", "name": "主数据缺失", "suggestion": "指派主数据维护"},
    {"code": "CAP_005", "level": "OPERATION", "name": "产能不足", "suggestion": "加班、增开资源或顺延"},
    {"code": "CAL_006", "level": "OPERATION", "name": "日历不可用", "suggestion": "检查日历与停机设置"},
    {"code": "FRZ_007", "level": "OPERATION", "name": "冻结区冲突", "suggestion": "P1 功能"},
    {"code": "PRI_008", "level": "OPERATION", "name": "优先级挤占", "suggestion": "提升优先级或拆分订单"},
]


class Store:
    """进程内状态，仅用于参考实现。生产环境落 PostgreSQL。"""

    def __init__(self):
        self.windows = build_windows(7, [(8, 16), (16, 24)])
        self.capabilities = ["CNC", "SMT", "ASM", "TEST", "PACK"]
        self.matrix = self._matrix()
        self.orders = self._orders()
        self.runs: dict[str, dict] = {}
        self.plans: dict[str, dict] = {}
        self.idempotency: dict[str, str] = {}

    def _matrix(self):
        families = ["SM", "RD", "PCB"]
        m: dict[tuple[str, str, str], int] = {}
        for i, c in enumerate(self.capabilities):
            for j in range(2):
                rid = f"{c}-{j+1:02d}"
                for a, f1 in enumerate(families):
                    for b, f2 in enumerate(families):
                        if f1 != f2:
                            m[(rid, f1, f2)] = 30 + 15 * abs(a - b)
        return m

    def _orders(self):
        orders = []
        for i in range(40):
            family = ["SM", "RD", "PCB"][i % 3]
            ops = [
                Operation(10, "OP10", self.capabilities[i % 5], 45, 15),
                Operation(20, "OP20", self.capabilities[(i + 1) % 5], 60, 30),
                Operation(30, "OP30", self.capabilities[(i + 2) % 5], 75, 15),
            ]
            orders.append(Order(
                no=f"SO-2024-{1200+i}", family=family,
                due=(1 + i % 6) * 24 * 60, priority=1 if i % 7 == 0 else 3,
                qty=1, ops=ops,
            ))
        return orders

    def fresh_resources(self):
        return [
            Resource(f"{c}-{j+1:02d}", c, self.windows,
                     [list(w) for w in self.windows], efficiency=0.95 if j == 0 else 1.0)
            for c in self.capabilities for j in range(2)
        ]

    @staticmethod
    def task_dto(tid: int, t) -> dict:
        return {
            "id": tid,
            "orderNo": t.order,
            "opCode": t.op,
            "resourceId": t.resource,
            "family": t.family,
            "start": iso(t.start),
            "end": iso(t.end),
            "segments": [{"start": iso(s), "end": iso(e)} for s, e in t.segs],
            "setup": [iso(t.setup[0]), iso(t.setup[1])] if t.setup else None,
            "late": t.late,
            "conflict": False,
            "source": "ENGINE",
        }

    def create_run(self, params: dict) -> dict:
        eng = Scheduler(self.fresh_resources(), self.matrix)
        tasks, unscheduled = eng.run(self.orders)
        violations = validate(tasks, self.orders, eng.resources)

        plan_id = f"PLAN-{len(self.plans)+1:03d}"
        items = [{"id": i + 1, "task": t} for i, t in enumerate(tasks)]
        self.plans[plan_id] = {
            "id": plan_id, "version": "V2024.47-DRAFT",
            "horizon": params.get("horizonDays", 7),
            "tasks": items, "unscheduled": unscheduled,
            "violations": violations,
            "resources": eng.resources,
        }

        run_id = f"RUN-{len(self.runs)+1:03d}"
        total_cap = sum(b - a for r in eng.resources.values() for a, b in r.windows)
        occupied = sum(
            t.proc_minutes + (t.setup[1] - t.setup[0] if t.setup else 0) for t in tasks
        )
        self.runs[run_id] = {
            "id": run_id, "status": "SUCCEEDED", "progress": 100,
            "planId": plan_id, "params": params,
            "summary": {
                "tasks": len(tasks),
                "unscheduled": len(unscheduled),
                "violations": len(violations),
                "setupCount": sum(1 for t in tasks if t.setup),
                "utilization": round(occupied / total_cap * 100, 1) if total_cap else 0,
            },
        }
        return self.runs[run_id]

    def gantt(self, plan: dict, query: dict) -> list[dict]:
        from_ts = to_minutes(query["from"][0]) if "from" in query else 0
        to_ts = to_minutes(query["to"][0]) if "to" in query else 7 * 24 * 60
        wanted = set(query["resourceIds"][0].split(",")) if "resourceIds" in query else None
        return [
            self.task_dto(item["id"], item["task"])
            for item in plan["tasks"]
            if wanted is None or item["task"].resource in wanted
            if item["task"].start < to_ts and item["task"].end > from_ts
        ]


STORE = Store()


class Handler(BaseHTTPRequestHandler):
    server_version = "APS-Reference/0.1"

    def log_message(self, fmt_str, *args):
        pass

    def _reply(self, data=None, message="ok", http_status=200, code=0):
        body = json.dumps(
            {"code": code, "message": message, "data": data,
             "traceId": uuid.uuid4().hex[:12]},
            ensure_ascii=False,
        ).encode("utf-8")
        self.send_response(http_status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self) -> dict:
        n = int(self.headers.get("Content-Length", "0"))
        if not n:
            return {}
        return json.loads(self.rfile.read(n).decode("utf-8"))

    def do_GET(self):
        u = urlparse(self.path)
        p, q = u.path, parse_qs(u.query)
        if p == "/api/v1/analysis/kpi":
            return self._reply({
                "pendingOrders": len(STORE.orders),
                "runs": len(STORE.runs),
                "plans": len(STORE.plans),
                "unscheduled": len(STORE.plans[next(reversed(STORE.plans))]["unscheduled"])
                if STORE.plans else 0,
            })
        if p == "/api/v1/dict/reason-codes":
            return self._reply({"items": REASON_CODES})
        if p == "/api/v1/analysis/unscheduled":
            plan = STORE.plans.get(next(reversed(STORE.plans))) if STORE.plans else None
            items = []
            if plan:
                for u0 in plan["unscheduled"]:
                    items.append({
                        "orderNo": u0.order, "opCode": u0.op,
                        "reasonCode": u0.reason, "detail": u0.detail,
                    })
            return self._reply({"items": items})
        if p == "/api/v1/scheduling/runs":
            return self._reply({"items": [STORE.runs[k] for k in STORE.runs]})
        if p.startswith("/api/v1/scheduling/runs/"):
            rid = p.rsplit("/", 1)[1]
            run = STORE.runs.get(rid)
            return self._reply(run) if run else self._reply(
                message="run not found", http_status=404, code=40401)
        if p.startswith("/api/v1/plans/") and p.endswith("/tasks"):
            pid = p.split("/")[4]
            plan = STORE.plans.get(pid)
            return self._reply({"items": STORE.gantt(plan, q)}) if plan else self._reply(
                message="plan not found", http_status=404, code=40402)
        if p.startswith("/api/v1/plans/") and "/tasks/" in p:
            parts = p.split("/")
            pid, tid = parts[4], int(parts[6])
            plan = STORE.plans.get(pid)
            item = next((x for x in plan["tasks"] if x["id"] == tid), None) if plan else None
            return self._reply(STORE.task_dto(tid, item["task"])) if item else self._reply(
                message="task not found", http_status=404, code=40403)
        return self._reply(message="not found", http_status=404, code=40400)

    def do_POST(self):
        u = urlparse(self.path)
        if u.path == "/api/v1/scheduling/runs":
            body = self._body()
            key = self.headers.get("Idempotency-Key") or body.get("idempotencyKey")
            if key and key in STORE.idempotency:
                rid = STORE.idempotency[key]
                return self._reply({"replayed": True, **STORE.runs[rid]})
            run = STORE.create_run(body.get("params", {}))
            if key:
                STORE.idempotency[key] = run["id"]
            return self._reply(run, http_status=201)
        return self._reply(message="not found", http_status=404, code=40400)

    def do_PATCH(self):
        u = urlparse(self.path)
        parts = u.path.split("/")
        if len(parts) < 7 or parts[4] not in STORE.plans:
            return self._reply(message="plan not found", http_status=404, code=40402)
        pid, tid = parts[4], int(parts[6])
        plan = STORE.plans[pid]
        item = next((x for x in plan["tasks"] if x["id"] == tid), None)
        if not item:
            return self._reply(message="task not found", http_status=404, code=40403)

        query = parse_qs(u.query)
        mode = query.get("mode", ["check"])[0]
        body = self._body()
        task = item["task"]
        resource = body.get("resourceId", task.resource)
        new_start = to_minutes(body["startTs"]) if "startTs" in body else task.start
        duration = sum(e - s for s, e in task.segs)
        new_end = new_start + duration

        res = plan["resources"][resource]
        violations = []
        for other in plan["tasks"]:
            o = other["task"]
            if other["id"] == tid or o.resource != resource:
                continue
            for s1, e1 in [(new_start, new_end)]:
                for s2, e2 in o.segs:
                    if s1 < e2 and s2 < e1:
                        violations.append({
                            "code": "RES_002",
                            "message": f"与 {o.order}/{o.op} 重叠",
                            "taskIds": [other["id"]],
                        })
        if not covered(res.windows, new_start, new_end):
            violations.append({"code": "CAL_006", "message": "落点不在工作时段内", "taskIds": []})

        accepted = not violations
        if accepted and mode == "commit":
            task.resource = resource
            task.segs = [(new_start, new_end)]
            task.setup = None

        return self._reply({
            "accepted": accepted,
            "mode": mode,
            "task": {"id": tid, "resourceId": resource, "start": iso(new_start), "end": iso(new_end)},
            "affectedTasks": [],
            "violations": violations,
            "suggestion": {"resourceId": resource, "startTs": iso(new_end)},
        })


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"APS reference service listening on http://127.0.0.1:{port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
