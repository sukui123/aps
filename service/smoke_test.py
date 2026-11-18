"""
P0 纵切冒烟测试：启动参考服务并验证主链路

    python3 service/smoke_test.py

覆盖：
  1. POST /scheduling/runs（含 Idempotency-Key 重放）
  2. GET  /scheduling/runs/{id}
  3. GET  /plans/{id}/tasks（甘特数据、跨班次分段）
  4. GET  /analysis/unscheduled 与 /dict/reason-codes
  5. PATCH /plans/{id}/tasks/{taskId}?mode=check 冲突拒绝
"""

from __future__ import annotations

import json
import threading
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

from app import Handler


def request(base: str, method: str, path: str, body: dict | None = None,
            headers: dict | None = None) -> tuple[int, dict]:
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(
        base + path, data=data, method=method,
        headers={"Content-Type": "application/json", **(headers or {})},
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, json.load(e)


def main() -> int:
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base = f"http://127.0.0.1:{server.server_address[1]}"
    results: list[tuple[str, bool, str]] = []

    def check(name, ok, detail=""):
        results.append((name, bool(ok), detail))

    try:
        # 1. 发起排产
        status, resp = request(base, "POST", "/api/v1/scheduling/runs",
                               {"params": {"horizonDays": 7}},
                               {"Idempotency-Key": "smoke-run-001"})
        created = resp.get("data", {})
        check("发起排产", status == 201 and created.get("status") == "SUCCEEDED",
              f"status={status}, run={created.get('id')}")

        # 2. 幂等重放
        status2, resp2 = request(base, "POST", "/api/v1/scheduling/runs",
                                 {"params": {"horizonDays": 7}},
                                 {"Idempotency-Key": "smoke-run-001"})
        check("幂等重放", status2 == 200 and resp2["data"].get("replayed") is True
              and resp2["data"]["id"] == created["id"])

        # 3. 批次状态与摘要
        _, run_resp = request(base, "GET", f"/api/v1/scheduling/runs/{created['id']}")
        run = run_resp.get("data", {})
        summary = run.get("summary", {})
        check("排产结果", run.get("progress") == 100 and summary.get("violations") == 0,
              f"tasks={summary.get('tasks')}, violations={summary.get('violations')}")

        # 4. 甘特数据
        pid = created["planId"]
        _, gantt = request(base, "GET", f"/api/v1/plans/{pid}/tasks")
        tasks = gantt.get("data", {}).get("items", [])
        check("甘特数据", len(tasks) > 0 and all("segments" in t for t in tasks),
              f"tasks={len(tasks)}")

        # 5. 未排与原因码
        _, unsched = request(base, "GET", "/api/v1/analysis/unscheduled")
        _, dict_resp = request(base, "GET", "/api/v1/dict/reason-codes")
        codes = dict_resp.get("data", {}).get("items", [])
        check("未排原因码", isinstance(unsched["data"].get("items"), list) and len(codes) == 8,
              f"codes={len(codes)}")

        # 6. 拖拽冲突拒绝：把同资源上两个任务挪到同一位置
        pair = None
        for i, a in enumerate(tasks):
            for b in tasks[i + 1:]:
                if a["resourceId"] == b["resourceId"]:
                    pair = (a, b)
                    break
            if pair:
                break
        if pair:
            a, b = pair
            status3, patch = request(
                base, "PATCH",
                f"/api/v1/plans/{pid}/tasks/{a['id']}?mode=check",
                {"resourceId": a["resourceId"], "startTs": b["start"]},
            )
            data = patch.get("data", {})
            check("拖拽冲突拒绝", status3 == 200 and data.get("accepted") is False
                  and len(data.get("violations", [])) > 0,
                  f"accepted={data.get('accepted')}, violations={len(data.get('violations', []))}")
        else:
            check("拖拽冲突拒绝", False, "找不到同资源的两个任务")

        # 7. KPI
        _, kpi = request(base, "GET", "/api/v1/analysis/kpi")
        check("工作台 KPI", kpi.get("data", {}).get("plans", 0) >= 1)

    finally:
        server.shutdown()

    print("=" * 68)
    print("P0 纵切冒烟测试")
    print("=" * 68)
    for name, ok, detail in results:
        print(f"  [{'PASS' if ok else 'FAIL'}] {name:14s} {detail}")
    ok_all = all(r[1] for r in results)
    print()
    print("结果:", "全部通过" if ok_all else "存在失败")
    return 0 if ok_all else 1


if __name__ == "__main__":
    raise SystemExit(main())
