package com.aps.api;

import com.aps.engine.ConstraintValidator;
import com.aps.engine.Scheduler;
import com.aps.engine.TimeSlots;
import com.aps.engine.model.EngineResult;
import com.aps.engine.model.Operation;
import com.aps.engine.model.Order;
import com.aps.engine.model.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** P0 纵切接口：发起排产 / 查询批次。状态为内存态，生产实现落 PostgreSQL。 */
@RestController
@RequestMapping("/api/v1")
public class SchedulingController {
    private final AtomicInteger seq = new AtomicInteger();
    private final Map<String, Map<String, Object>> runs = new ConcurrentHashMap<>();
    private final Map<String, Object> idempotency = new ConcurrentHashMap<>();

    @PostMapping("/scheduling/runs")
    public Map<String, Object> create(@RequestBody(required = false) Map<String, Object> body,
                                      @org.springframework.web.bind.annotation.RequestHeader(
                                              value = "Idempotency-Key", required = false) String key) {
        if (key != null && idempotency.containsKey(key)) {
            Map<String, Object> replayed = new LinkedHashMap<>(runs.get(idempotency.get(key)));
            replayed.put("replayed", true);
            return wrap(replayed);
        }

        EngineResult result = runEngine();
        String runId = "RUN-" + String.format("%03d", seq.incrementAndGet());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", runId);
        summary.put("status", "SUCCEEDED");
        summary.put("progress", 100);
        summary.put("tasks", result.tasks().size());
        summary.put("unscheduled", result.unscheduled().size());
        summary.put("violations", ConstraintValidator.validate(
                result.tasks(), sampleOrders(), Map.of()).size());
        runs.put(runId, summary);
        if (key != null) {
            idempotency.put(key, runId);
        }
        return wrap(summary);
    }

    @GetMapping("/scheduling/runs/{id}")
    public Map<String, Object> get(@PathVariable("id") String id) {
        Map<String, Object> run = runs.get(id);
        return run == null ? wrap(Map.of("error", "not found")) : wrap(run);
    }

    @GetMapping("/analysis/kpi")
    public Map<String, Object> kpi() {
        return wrap(Map.of("runs", runs.size(), "orders", sampleOrders().size()));
    }

    private EngineResult runEngine() {
        List<Resource> resources = new ArrayList<>();
        List<long[]> windows = TimeSlots.buildWindows(7, new int[][]{{8, 16}, {16, 24}});
        for (String cap : List.of("CNC", "SMT", "ASM", "TEST", "PACK")) {
            resources.add(new Resource(cap + "-01", cap, windows, 1.0));
        }
        return new Scheduler(resources, Map.of()).run(sampleOrders());
    }

    private List<Order> sampleOrders() {
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            orders.add(new Order("SO-" + (1200 + i), "SM", 2 * 1440L, 3, 1, List.of(
                    new Operation(10, "OP10", "CNC", 60, 0, 1),
                    new Operation(20, "OP20", "SMT", 45, 0, 1),
                    new Operation(30, "OP30", "ASM", 90, 0, 1)), 0L));
        }
        return orders;
    }

    private Map<String, Object> wrap(Map<String, Object> data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", 0);
        out.put("message", "ok");
        out.put("data", data);
        out.put("traceId", UUID.randomUUID().toString().substring(0, 12));
        return out;
    }
}
