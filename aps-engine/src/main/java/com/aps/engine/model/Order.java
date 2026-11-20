package com.aps.engine.model;

import java.util.List;

/** 生产订单，时间均为相对计划起点的分钟数。 */
public record Order(
        String no,
        String family,
        long due,
        int priority,
        int qty,
        List<Operation> ops,
        long materialReady
) {
    public Order {
        ops = List.copyOf(ops);
    }
}
