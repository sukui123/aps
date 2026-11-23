package com.aps.engine.context;

import com.aps.common.context.TenantContext;

import java.util.List;

/** worker 线程从任务行恢复租户上下文；与 Web 链路共用同一个 TenantContext。 */
public final class WorkerTenantContext {

    private WorkerTenantContext() {
    }

    @FunctionalInterface
    public interface TenantTask {
        void run() throws Exception;
    }

    public static void runInTenant(long tenantId, TenantTask task) throws Exception {
        TenantContext.set(tenantId, List.of(), null, "system:worker", null);
        try {
            task.run();
        } finally {
            TenantContext.clear();
        }
    }
}
