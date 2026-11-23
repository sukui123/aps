package com.aps.domain.service;

import java.time.Instant;

/** 集成任务失败退避策略（PF-04）：1/2/4/8/16 分钟，超过 5 次置 DEAD。 */
public final class IntegrationRetryPolicy {

    private IntegrationRetryPolicy() {
    }

    public static final int MAX_RETRIES = 5;

    /** @param retryCount 本次失败后累计的重试次数（从 1 开始） */
    public static Instant nextRetryAt(int retryCount, Instant now) {
        int exponent = Math.max(0, retryCount - 1);
        long minutes = Math.min(1L << exponent, 16L);
        return now.plusSeconds(minutes * 60);
    }

    public static boolean isDead(int retryCount) {
        return retryCount > MAX_RETRIES;
    }
}
