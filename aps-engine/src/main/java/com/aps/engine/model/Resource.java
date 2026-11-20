package com.aps.engine.model;

import java.util.ArrayList;
import java.util.List;

/** 资源及其空闲区间。P0 仅支持单机容量，capacity>1 需拆成独立机台。 */
public final class Resource {
    public final String id;
    public final String capability;
    public final List<long[]> windows;
    public final List<long[]> free;
    public final double efficiency;
    public final int capacity;
    public final List<Task> tasks = new ArrayList<>();
    public int setupWarnings;

    public Resource(String id, String capability, List<long[]> windows,
                    double efficiency, int capacity) {
        if (capacity != 1) {
            throw new IllegalArgumentException(id + ": P0 仅支持单机容量，capacity>1 请拆成独立机台");
        }
        this.id = id;
        this.capability = capability;
        this.windows = windows;
        this.free = new ArrayList<>();
        for (long[] w : windows) {
            this.free.add(new long[]{w[0], w[1]});
        }
        this.efficiency = efficiency;
        this.capacity = capacity;
    }

    public Resource(String id, String capability, List<long[]> windows, double efficiency) {
        this(id, capability, windows, efficiency, 1);
    }

    /** 每次排产使用独立的资源状态，避免串扰。 */
    public Resource freshCopy() {
        return new Resource(id, capability, windows, efficiency, capacity);
    }
}
