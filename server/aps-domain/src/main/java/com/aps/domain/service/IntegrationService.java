package com.aps.domain.service;

import com.aps.common.result.PageResult;
import com.aps.domain.model.IntegrationConfig;
import com.aps.domain.model.IntegrationTask;

import java.time.Instant;
import java.util.List;

/** 集成配置/任务/补偿端口（PF-04）。REST 适配器为桩，文件/DB 视图为可运行兜底。 */
public interface IntegrationService {

    List<IntegrationConfig> listConfigs(Long tenantId);

    Long createConfig(Long tenantId, IntegrationConfig config, String rawCredentials, String actor);

    void updateConfig(Long tenantId, IntegrationConfig config, String rawCredentials, String actor);

    /** (tenant_id,biz_type,biz_key) 与 Idempotency-Key 双幂等：重复提交返回首次任务。 */
    SyncResult submitTask(Long tenantId, String bizType, String bizKey, String idempotencyKey,
                          String payloadJson, String actor);

    PageResult<IntegrationTask> listTasks(Long tenantId, TaskQuery query);

    /** 人工重试：FAILED/DEAD → PENDING；人工触发不消耗自动退避次数。 */
    void retryTask(Long tenantId, Long taskId, String actor);

    /** worker 使用：返回尚有可执行任务的租户。 */
    List<Long> pendingTenantIds();

    /** worker 使用：在已注入租户上下文的事务内领取并执行最多 limit 条任务。 */
    int processTenant(long tenantId, int limit);

    record SyncResult(Long taskId, boolean created) {
    }

    record TaskQuery(String status, String bizType, boolean failedOnly, Integer minRetryCount,
                     Instant nextRetryBefore, int page, int pageSize) {
    }
}
