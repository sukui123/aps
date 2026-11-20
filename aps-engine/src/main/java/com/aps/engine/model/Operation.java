package com.aps.engine.model;

/** 工序主数据（展开后的实例粒度）。 */
public record Operation(
        int seq,
        String code,
        String capability,
        double stdTimeMin,
        int setupMin,
        int minBatch
) {
    public Operation {
        if (stdTimeMin <= 0 || minBatch < 1) {
            throw new IllegalArgumentException("stdTimeMin 必须为正，minBatch 至少为 1");
        }
    }
}
