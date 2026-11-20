package com.aps.engine.model;

import java.util.ArrayList;
import java.util.List;

/** 排产任务。加工可跨班次，因此 segs 为多段占用；setup 单独占位。 */
public final class Task {
    public String order;
    public String op;
    public String resource;
    public String family;
    public List<long[]> segs = new ArrayList<>();
    public long[] setup;
    public boolean late;

    public Task(String order, String op, String resource, String family) {
        this.order = order;
        this.op = op;
        this.resource = resource;
        this.family = family;
    }

    public long start() {
        return segs.get(0)[0];
    }

    public long end() {
        return segs.get(segs.size() - 1)[1];
    }

    public long procMinutes() {
        return segs.stream().mapToLong(s -> s[1] - s[0]).sum();
    }
}
