package com.aps.engine;

import com.aps.engine.model.EngineResult;
import com.aps.engine.model.Operation;
import com.aps.engine.model.Order;
import com.aps.engine.model.Resource;
import com.aps.engine.model.Task;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 与 Python 原型 v2 的 5 个回归用例一一对应。 */
class EngineRegressionTest {

    private static Resource res(String id, String cap, long[][] windows) {
        List<long[]> ws = new ArrayList<>();
        for (long[] w : windows) {
            ws.add(new long[]{w[0], w[1]});
        }
        return new Resource(id, cap, ws, 1.0);
    }

    private static Order order(String no, String family, int priority, long due, Operation... ops) {
        return new Order(no, family, due, priority, 1, List.of(ops), 0L);
    }

    @Test
    void b1SetupOccupiesTimeline() {
        Resource r = res("R1", "X", new long[][]{{8 * 60L, 20 * 60L}});
        Order a = order("A1", "FA", 1, 1200, new Operation(10, "OP10", "X", 60, 0, 1));
        Order b = order("B1", "FB", 2, 1200, new Operation(10, "OP10", "X", 120, 0, 1));

        Scheduler scheduler = new Scheduler(List.of(r), Map.of("R1|FA|FB", 60));
        EngineResult result = scheduler.run(List.of(a, b));

        Task bt = result.tasks().stream()
                .filter(t -> t.order.equals("B1")).findFirst().orElseThrow();
        assertArrayEquals(new long[]{9 * 60L, 10 * 60L}, bt.setup,
                "换型必须真实占用 09:00-10:00");
        assertEquals(10 * 60L, bt.start(), "加工必须在换型结束后开始");
        assertTrue(ConstraintValidator.validate(result.tasks(), List.of(a, b),
                scheduler.resources()).isEmpty());
    }

    @Test
    void b2ProcessingSpansShifts() {
        Resource r = new Resource("R1", "X", TimeSlots.buildWindows(3, new int[][]{{8, 16}}), 1.0);
        Order o = order("SO-1", "F", 3, 3 * 1440L,
                new Operation(10, "OP10", "X", 600, 0, 1));

        Scheduler scheduler = new Scheduler(List.of(r), Map.of());
        EngineResult result = scheduler.run(List.of(o));

        assertEquals(1, result.tasks().size(), "600 分钟工序不应被误判产能不足");
        Task t = result.tasks().get(0);
        assertEquals(600, t.procMinutes());
        assertTrue(t.segs.size() >= 2, "应产生跨班次分段");
        assertTrue(ConstraintValidator.validate(result.tasks(), List.of(o),
                scheduler.resources()).isEmpty());
    }

    @Test
    void b3OrderIsAtomic() {
        Resource r = res("R1", "X", new long[][]{{8 * 60L, 10 * 60L}});
        Order o = new Order("SO-2", "F", 2 * 1440L, 3, 1, List.of(
                new Operation(10, "OP10", "X", 90, 0, 1),
                new Operation(20, "OP20", "X", 90, 0, 1)), 0L);

        Scheduler scheduler = new Scheduler(List.of(r), Map.of());
        EngineResult result = scheduler.run(List.of(o));

        assertTrue(result.tasks().isEmpty(), "未排订单不得残留已排工序");
        assertEquals(1, result.unscheduled().size());
        assertEquals("CAP_005", result.unscheduled().get(0).reason());
        List<long[]> free = scheduler.resources().get("R1").free;
        assertEquals(1, free.size(), "空闲区间必须完整还原");
        assertEquals(8 * 60L, free.get(0)[0]);
        assertEquals(10 * 60L, free.get(0)[1]);
    }

    @Test
    void sameFamilyHasNoSetup() {
        Resource r = res("R1", "X", new long[][]{{480, 1200}});
        Order a = order("A1", "FA", 1, 1200, new Operation(10, "OP10", "X", 60, 0, 1));
        Order b = order("A2", "FA", 2, 1200, new Operation(10, "OP10", "X", 60, 0, 1));

        Scheduler scheduler = new Scheduler(List.of(r), Map.of());
        EngineResult result = scheduler.run(List.of(a, b));
        Task second = result.tasks().stream()
                .filter(t -> t.order.equals("A2")).findFirst().orElseThrow();
        assertNull(second.setup, "同族连续生产不应换型");
    }

    @Test
    void validatorCatchesInjectedOverlap() {
        Task a = new Task("A", "OP10", "R1", "F");
        a.segs.add(new long[]{0, 120});
        Task b = new Task("B", "OP10", "R1", "F");
        b.segs.add(new long[]{60, 180});

        List<String> errors = ConstraintValidator.validate(List.of(a, b), List.of(), Map.of());
        assertFalse(errors.isEmpty(), "负向用例：校验器必须检出人为注入的重叠");
        assertTrue(errors.get(0).contains("资源冲突"));
    }

    @Test
    void scaleBenchmarkCompletesWithinBudget() {
        Random random = new Random(42);
        List<long[]> windows = TimeSlots.buildWindows(7, new int[][]{{8, 16}, {16, 24}});
        String[] caps = {"CNC", "SMT", "ASM", "TEST", "PACK"};
        List<Resource> resources = new ArrayList<>();
        for (String cap : caps) {
            for (int j = 1; j <= 2; j++) {
                resources.add(new Resource(cap + "-" + String.format("%02d", j),
                        cap, windows, j == 1 ? 0.95 : 1.0));
            }
        }
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            List<Operation> ops = new ArrayList<>();
            for (int k = 0; k < 5; k++) {
                ops.add(new Operation((k + 1) * 10, "OP" + (k + 1) * 10,
                        caps[random.nextInt(caps.length)], 15 + random.nextInt(61),
                        random.nextInt(3) * 15, 1));
            }
            orders.add(order(String.format("SO-%05d", i), "SM", 3, 6 * 1440L,
                    ops.toArray(new Operation[0])));
        }

        long t0 = System.nanoTime();
        Scheduler scheduler = new Scheduler(resources, Map.of());
        EngineResult result = scheduler.run(orders);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        List<String> errors = ConstraintValidator.validate(
                result.tasks(), orders, scheduler.resources());
        assertTrue(errors.isEmpty(), "压测结果必须通过硬约束校验");
        assertTrue(elapsedMs < 30_000, "1500 任务应在 30 秒内完成，实际 " + elapsedMs + "ms");
        System.out.printf("Java 引擎压测: %d 订单 / %d 任务 / %d ms%n",
                orders.size(), result.tasks().size(), elapsedMs);
    }
}
