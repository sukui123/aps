package com.aps.engine;

import com.aps.engine.model.EngineResult;
import com.aps.engine.model.Operation;
import com.aps.engine.model.Order;
import com.aps.engine.model.Resource;
import com.aps.engine.model.Task;
import com.aps.engine.model.Unscheduled;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启发式排产引擎（v2 算法的 Java 移植）。
 *
 * 已修正评审 B1/B2/B3：
 *   - 换型作为独立区间占位，前驱取时间相邻任务
 *   - 加工可跨班次，产出多段占用
 *   - 订单级事务，失败整单回滚
 */
public final class Scheduler {
    private record Eval(long start, int setup, long end, List<long[]> segs, Resource res) {
    }

    private record Placed(Resource res, Task task) {
    }

    private final Map<String, Resource> resources = new LinkedHashMap<>();
    private final Map<String, List<Resource>> byCapability = new HashMap<>();
    private final Map<String, Integer> setupMatrix;
    private final long planStart;
    private final long freezeEnd;
    private final long horizonEnd;

    private final List<Task> tasks = new ArrayList<>();
    private final List<Unscheduled> unscheduled = new ArrayList<>();

    public Scheduler(List<Resource> templates, Map<String, Integer> setupMatrix) {
        this(templates, setupMatrix, 0L, 0L);
    }

    public Scheduler(List<Resource> templates, Map<String, Integer> setupMatrix,
                     long planStart, long freezeEnd) {
        this.setupMatrix = setupMatrix;
        this.planStart = planStart;
        this.freezeEnd = freezeEnd;
        long horizon = 0L;
        for (Resource template : templates) {
            Resource r = template.freshCopy();
            this.resources.put(r.id, r);
            this.byCapability.computeIfAbsent(r.capability, k -> new ArrayList<>()).add(r);
            for (long[] w : r.windows) {
                horizon = Math.max(horizon, w[1]);
            }
        }
        this.byCapability.values().forEach(list -> list.sort(Comparator.comparing(r -> r.id)));
        this.horizonEnd = horizon;
    }

    public EngineResult run(List<Order> orders) {
        List<Order> sorted = orders.stream()
                .sorted(Comparator.comparingInt(Order::priority)
                        .thenComparingLong(Order::due)
                        .thenComparing(Order::no))
                .toList();
        for (Order order : sorted) {
            placeOrder(order);
        }
        tasks.sort(Comparator.comparing((Task t) -> t.resource).thenComparingLong(Task::start));
        return new EngineResult(List.copyOf(tasks), List.copyOf(unscheduled));
    }

    public Map<String, Resource> resources() {
        return resources;
    }

    private void placeOrder(Order order) {
        if (order.materialReady() >= horizonEnd) {
            unscheduled.add(new Unscheduled(order.no(), "-", "MAT_001",
                    "物料齐套时点超出计划窗口"));
            return;
        }

        long cursor = Math.max(planStart, Math.max(freezeEnd, order.materialReady()));
        List<Placed> pending = new ArrayList<>();

        for (Operation op : order.ops()) {
            List<Resource> candidates = byCapability.get(op.capability());
            if (candidates == null || candidates.isEmpty()) {
                rollback(pending);
                unscheduled.add(new Unscheduled(order.no(), op.code(), "RES_002",
                        "能力 " + op.capability() + " 无可用资源"));
                return;
            }

            Eval best = null;
            for (Resource res : candidates) {
                Eval ev = evaluate(res, cursor, op, order);
                if (ev == null) {
                    continue;
                }
                if (best == null || ev.end < best.end
                        || (ev.end == best.end && ev.setup < best.setup)) {
                    best = ev;
                }
            }

            if (best == null) {
                rollback(pending);
                long demand = (long) Math.ceil(Math.max(order.qty(), op.minBatch()) * op.stdTimeMin());
                unscheduled.add(new Unscheduled(order.no(), op.code(), "CAP_005",
                        "窗口内无法容纳 " + demand + " 分钟（含换型）"));
                return;
            }

            commit(best, order, op, pending);
            cursor = best.segs.get(best.segs.size() - 1)[1];
        }

        pending.stream().map(Placed::task).forEach(tasks::add);
    }

    private Eval evaluate(Resource res, long earliest, Operation op, Order order) {
        long duration = (long) Math.ceil(
                Math.max(order.qty(), op.minBatch()) * op.stdTimeMin() / res.efficiency);
        Long cursor = TimeSlots.nextFreeStart(res.free, earliest);
        if (cursor == null) {
            return null;
        }
        long st = cursor;
        int setup = 0;
        for (int i = 0; i < 3; i++) {
            String prev = prevFamily(res, st);
            setup = setupMinutes(res, prev, order.family(), op.setupMin());
            if (setup == 0) {
                break;
            }
            Long pos = TimeSlots.peekSingle(res.free, st, setup);
            if (pos == null) {
                return null;
            }
            if (pos == st) {
                break;
            }
            st = pos;
        }
        List<long[]> segs = TimeSlots.peekSpan(res.free, st + setup, duration);
        if (segs == null) {
            return null;
        }
        long end = segs.get(segs.size() - 1)[1];
        return new Eval(st, setup, end, segs, res);
    }

    private String prevFamily(Resource res, long at) {
        Task prev = null;
        for (Task t : res.tasks) {
            if (t.end() <= at && (prev == null || t.end() > prev.end())) {
                prev = t;
            }
        }
        return prev == null ? null : prev.family;
    }

    private int setupMinutes(Resource res, String prev, String family, int base) {
        if (prev == null) {
            return base;
        }
        if (prev.equals(family)) {
            return 0;
        }
        Integer v = setupMatrix.get(res.id + "|" + prev + "|" + family);
        if (v != null) {
            return v;
        }
        res.setupWarnings++;
        return base;
    }

    private void commit(Eval ev, Order order, Operation op, List<Placed> pending) {
        if (ev.setup > 0) {
            TimeSlots.cut(ev.res.free, ev.start, ev.start + ev.setup);
        }
        for (long[] seg : ev.segs) {
            TimeSlots.cut(ev.res.free, seg[0], seg[1]);
        }
        Task task = new Task(order.no(), op.code(), ev.res.id, order.family());
        task.segs = ev.segs;
        task.setup = ev.setup > 0 ? new long[]{ev.start, ev.start + ev.setup} : null;
        task.late = ev.end > order.due();
        ev.res.tasks.add(task);
        pending.add(new Placed(ev.res, task));
    }

    private void rollback(List<Placed> pending) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            Placed p = pending.get(i);
            p.res.tasks.remove(p.task);
            for (long[] seg : p.task.segs) {
                TimeSlots.mergeInsert(p.res.free, seg[0], seg[1]);
            }
            if (p.task.setup != null) {
                TimeSlots.mergeInsert(p.res.free, p.task.setup[0], p.task.setup[1]);
            }
        }
    }
}
