package com.aps.engine;

import com.aps.engine.model.Order;
import com.aps.engine.model.Resource;
import com.aps.engine.model.Task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 硬约束校验器：时间轴守恒 + 订单完整性。 */
public final class ConstraintValidator {
    private record Block(long start, long end, String kind, Task task) {
    }

    private ConstraintValidator() {
    }

    public static List<String> validate(List<Task> tasks, List<Order> orders,
                                        Map<String, Resource> resources) {
        List<String> errors = new ArrayList<>();

        Map<String, List<Block>> byResource = new HashMap<>();
        for (Task t : tasks) {
            for (long[] seg : t.segs) {
                byResource.computeIfAbsent(t.resource, k -> new ArrayList<>())
                        .add(new Block(seg[0], seg[1], "PROC", t));
            }
            if (t.setup != null) {
                byResource.computeIfAbsent(t.resource, k -> new ArrayList<>())
                        .add(new Block(t.setup[0], t.setup[1], "SETUP", t));
            }
        }

        for (Map.Entry<String, List<Block>> entry : byResource.entrySet()) {
            String rid = entry.getKey();
            Resource res = resources.get(rid);
            List<Block> blocks = entry.getValue();
            blocks.sort(Comparator.comparingLong(Block::start));
            for (int i = 1; i < blocks.size(); i++) {
                Block a = blocks.get(i - 1);
                Block b = blocks.get(i);
                if (b.start < a.end) {
                    errors.add("资源冲突 " + rid + ": " + a.task.order + "/" + a.task.op
                            + "(" + a.kind + ") 与 " + b.task.order + "/" + b.task.op
                            + "(" + b.kind + ") 重叠");
                }
            }
            if (res != null) {
                for (Block b : blocks) {
                    if (!TimeSlots.covered(res.windows, b.start, b.end)) {
                        errors.add("越出日历 " + rid + ": " + b.task.order + "/" + b.task.op
                                + " " + b.kind);
                    }
                    if ("SETUP".equals(b.kind) && !singleWindow(res.windows, b.start, b.end)) {
                        errors.add("换型跨班次 " + rid + ": " + b.task.order + "/" + b.task.op);
                    }
                }
            }
        }

        Map<String, List<Task>> byOrder = new HashMap<>();
        for (Task t : tasks) {
            byOrder.computeIfAbsent(t.order, k -> new ArrayList<>()).add(t);
        }
        for (Order o : orders) {
            List<Task> got = byOrder.get(o.no());
            if (got == null || got.isEmpty()) {
                continue;   // 整单未排由 plan_unscheduled 记录
            }
            if (got.size() != o.ops().size()) {
                errors.add("订单未完整排产 " + o.no() + ": " + got.size() + "/" + o.ops().size());
                continue;
            }
            List<String> seq = got.stream()
                    .sorted(Comparator.comparingLong(Task::start))
                    .map(t -> t.op).toList();
            List<String> expect = o.ops().stream().map(op -> op.code()).toList();
            if (!seq.equals(expect)) {
                errors.add("工序顺序错误 " + o.no() + ": " + seq + " != " + expect);
            }
        }
        return errors;
    }

    private static boolean singleWindow(List<long[]> windows, long s, long e) {
        for (long[] w : windows) {
            if (w[0] <= s && e <= w[1]) {
                return true;
            }
        }
        return false;
    }
}
