package com.aps.engine.worker;

import com.aps.common.constant.Constants;
import com.aps.engine.context.WorkerTenantContext;
import com.aps.domain.service.IntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 集成补偿 worker（PF-04）：轮询有可执行任务的租户，逐租户注入租户上下文后处理。
 * 与 ScheduleWorker 同构；不修改任何求解逻辑。aps.worker.enabled=false 可关闭。
 */
@Component
@ConditionalOnProperty(prefix = "aps.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IntegrationTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(IntegrationTaskWorker.class);

    private final IntegrationService integrationService;

    public IntegrationTaskWorker(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @Scheduled(fixedDelayString = "${aps.worker.poll-ms:5000}")
    public void poll() {
        MDC.put(Constants.TRACE_ID, UUID.randomUUID().toString().replace("-", ""));
        try {
            List<Long> tenants = integrationService.pendingTenantIds();
            for (Long tenantId : tenants) {
                try {
                    WorkerTenantContext.runInTenant(tenantId,
                            () -> integrationService.processTenant(tenantId, 10));
                } catch (Exception e) {
                    log.warn("integration worker failed for tenant={}, traceId={}", tenantId,
                            MDC.get(Constants.TRACE_ID), e);
                }
            }
        } catch (Exception e) {
            log.warn("integration worker poll failed, traceId={}", MDC.get(Constants.TRACE_ID), e);
        } finally {
            MDC.remove(Constants.TRACE_ID);
        }
    }
}
