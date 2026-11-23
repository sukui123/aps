package com.aps.engine.worker;

import com.aps.common.constant.AuditActions;
import com.aps.common.constant.Constants;
import com.aps.common.context.TenantContext;
import com.aps.domain.model.AuditLog;
import com.aps.domain.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 应用内排产 worker（技术方案 §2.2/§1.7）：DB 任务表轮询领取 + PG advisory lock/行锁防重。
 * E7 最小改动：仅预留审计端口调用位，不触碰求解逻辑与约束链。
 */
@Component
public class ScheduleWorker {

    private static final Logger log = LoggerFactory.getLogger(ScheduleWorker.class);

    private final ObjectProvider<AuditService> auditService;

    public ScheduleWorker(ObjectProvider<AuditService> auditService) {
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "${aps.worker.poll-ms:5000}")
    public void poll() {
        log.debug("scheduling worker poll (scaffold) — claim via DB task table + advisory lock pending");
    }

    /**
     * 审计端口（E3/E5 落地时调用）：在写入 plan_version/scheduled_task 的同一事务边界内，
     * 用 before/after JSON 记录计划调整。当前无甘特实现，故本方法为预留调用位。
     */
    public void recordPlanAdjustment(String targetId, String beforeJson, String afterJson, String actor) {
        AuditService service = auditService.getIfAvailable();
        if (service == null) {
            return;
        }
        AuditLog entry = new AuditLog();
        entry.setTenantId(TenantContext.tenantId());
        entry.setActor(actor);
        entry.setAction(AuditActions.PLAN_TASK_ADJUST);
        entry.setTargetType("PlanTask");
        entry.setTargetId(targetId);
        entry.setBeforeJson(beforeJson);
        entry.setAfterJson(afterJson);
        entry.setTraceId(MDC.get(Constants.TRACE_ID));
        service.record(entry);
    }
}
