package com.aps.infra.service;

import com.aps.common.annotation.Audited;
import com.aps.common.constant.AuditActions;
import com.aps.common.constant.Constants;
import com.aps.common.error.CommonErrorCode;
import com.aps.common.exception.BusinessException;
import com.aps.common.result.PageResult;
import com.aps.domain.model.AuditLog;
import com.aps.domain.model.IntegrationConfig;
import com.aps.domain.model.IntegrationTask;
import com.aps.domain.port.IntegrationAdapter;
import com.aps.domain.service.AuditService;
import com.aps.domain.service.IntegrationRetryPolicy;
import com.aps.domain.service.IntegrationService;
import com.aps.infra.integration.CredentialCipher;
import com.aps.infra.mapper.IntegrationConfigMapper;
import com.aps.infra.mapper.IntegrationTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 集成管理实现（PF-04）：配置脱敏、双幂等、状态机补偿、文件/DB 视图兜底。 */
@Service
public class IntegrationServiceImpl implements IntegrationService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationServiceImpl.class);

    private final IntegrationConfigMapper configMapper;
    private final IntegrationTaskMapper taskMapper;
    private final CredentialCipher cipher;
    private final Map<String, IntegrationAdapter> adapters;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final PlatformTransactionManager transactionManager;

    public IntegrationServiceImpl(IntegrationConfigMapper configMapper, IntegrationTaskMapper taskMapper,
                                  CredentialCipher cipher, List<IntegrationAdapter> adapters,
                                  ObjectMapper objectMapper, AuditService auditService,
                                  PlatformTransactionManager transactionManager) {
        this.configMapper = configMapper;
        this.taskMapper = taskMapper;
        this.cipher = cipher;
        this.adapters = adapters.stream()
                .collect(Collectors.toMap(IntegrationAdapter::type, Function.identity()));
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.transactionManager = transactionManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntegrationConfig> listConfigs(Long tenantId) {
        return configMapper.selectList(new LambdaQueryWrapper<IntegrationConfig>()
                .eq(IntegrationConfig::getTenantId, tenantId)
                .eq(IntegrationConfig::getIsDeleted, false)
                .orderByAsc(IntegrationConfig::getId));
    }

    @Override
    @Transactional
    @Audited(action = AuditActions.INTEGRATION_CONFIG_CREATE, targetType = "IntegrationConfig", target = "#result")
    public Long createConfig(Long tenantId, IntegrationConfig config, String rawCredentials, String actor) {
        validateType(config.getType());
        IntegrationConfig entity = copy(config);
        entity.setId(IdWorker.getId());
        entity.setTenantId(tenantId);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);
        entity.setCredentialsCipher(encrypt(rawCredentials));
        if (configMapper.insertConfig(entity) == 0) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "数据源编码已存在");
        }
        return entity.getId();
    }

    @Override
    @Transactional
    @Audited(action = AuditActions.INTEGRATION_CONFIG_UPDATE, targetType = "IntegrationConfig", target = "#config.id")
    public void updateConfig(Long tenantId, IntegrationConfig config, String rawCredentials, String actor) {
        validateType(config.getType());
        IntegrationConfig existing = configMapper.selectOne(new LambdaQueryWrapper<IntegrationConfig>()
                .eq(IntegrationConfig::getId, config.getId())
                .eq(IntegrationConfig::getTenantId, tenantId)
                .eq(IntegrationConfig::getIsDeleted, false));
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "数据源配置不存在");
        }
        IntegrationConfig entity = copy(config);
        entity.setTenantId(tenantId);
        // 版本以库中现值为准，禁止信任客户端传入，避免并发更新误报冲突
        entity.setVersion(existing.getVersion());
        entity.setUpdatedBy(actor);
        // 未传入新凭据时沿用原密文，避免把 **** 当成真实凭据写入
        entity.setCredentialsCipher(rawCredentials == null || rawCredentials.isBlank()
                ? existing.getCredentialsCipher() : encrypt(rawCredentials));
        if (configMapper.updateConfig(entity) == 0) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "配置已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional
    @Audited(action = AuditActions.ORDER_SYNC_TRIGGER, targetType = "IntegrationTask", target = "#result?.taskId()")
    public SyncResult submitTask(Long tenantId, String bizType, String bizKey, String idempotencyKey,
                                 String payloadJson, String actor) {
        // 预检查：所有查询都带 tenantId，禁止跨租户命中（B2）
        SyncResult existing = lookupFirst(tenantId, bizType, bizKey, idempotencyKey);
        if (existing != null) {
            return existing;
        }
        IntegrationTask task = new IntegrationTask();
        task.setId(IdWorker.getId());
        task.setTenantId(tenantId);
        task.setBizType(bizType);
        task.setBizKey(bizKey);
        task.setIdempotencyKey(idempotencyKey);
        task.setPayload(normalizePayload(payloadJson, bizType, bizKey));
        task.setUpdatedBy(actor);
        // 在独立事务内插入：唯一键冲突被限制在子事务，外层事务不会进入 aborted 状态而返回 500（B2）
        SyncResult created = insertInNewTransaction(task);
        if (created != null) {
            return created;
        }
        // 并发重复提交：回查首次任务，created=false（验收 #14）
        SyncResult first = lookupFirst(tenantId, bizType, bizKey, idempotencyKey);
        if (first == null) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT, "重复提交冲突，请重试");
        }
        return first;
    }

    /** 插入放入 REQUIRES_NEW 子事务，DuplicateKeyException 在此被捕获并回滚，不污染外层事务。 */
    private SyncResult insertInNewTransaction(IntegrationTask task) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> {
            try {
                return taskMapper.insertIdempotent(task) == 1 ? new SyncResult(task.getId(), true) : null;
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                log.warn("idempotent insert conflict, key={}, bizKey={}", task.getIdempotencyKey(), task.getBizKey());
                return null;
            }
        });
    }

    /** 按幂等键、业务键顺序回查，二者都限定在当前租户内。 */
    private SyncResult lookupFirst(Long tenantId, String bizType, String bizKey, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IntegrationTask byKey = taskMapper.findByIdempotencyKey(tenantId, idempotencyKey);
            if (byKey != null) {
                return new SyncResult(byKey.getId(), false);
            }
        }
        IntegrationTask byBiz = taskMapper.findByBizKey(tenantId, bizType, bizKey);
        return byBiz == null ? null : new SyncResult(byBiz.getId(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<IntegrationTask> listTasks(Long tenantId, TaskQuery query) {
        Page<IntegrationTask> req = new Page<>(query.page(), query.pageSize());
        var result = taskMapper.selectPage(req, new LambdaQueryWrapper<IntegrationTask>()
                .eq(IntegrationTask::getTenantId, tenantId)
                .eq(query.status() != null && !query.status().isBlank(), IntegrationTask::getStatus, query.status())
                .eq(query.bizType() != null && !query.bizType().isBlank(), IntegrationTask::getBizType, query.bizType())
                .in(query.failedOnly(), IntegrationTask::getStatus, List.of("FAILED", "DEAD"))
                .ge(query.minRetryCount() != null, IntegrationTask::getRetryCount, query.minRetryCount())
                .le(query.nextRetryBefore() != null, IntegrationTask::getNextRetryAt, query.nextRetryBefore())
                .eq(IntegrationTask::getIsDeleted, false)
                .orderByDesc(IntegrationTask::getCreatedAt));
        return PageResult.of(result.getRecords(), result.getTotal(), query.page(), query.pageSize());
    }

    @Override
    @Transactional
    @Audited(action = AuditActions.INTEGRATION_TASK_RETRY, targetType = "IntegrationTask", target = "#taskId")
    public void retryTask(Long tenantId, Long taskId, String actor) {
        IntegrationTask task = taskMapper.selectOne(new LambdaQueryWrapper<IntegrationTask>()
                .eq(IntegrationTask::getId, taskId)
                .eq(IntegrationTask::getTenantId, tenantId)
                .eq(IntegrationTask::getIsDeleted, false));
        if (task == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "任务不存在");
        }
        if (!"FAILED".equals(task.getStatus()) && !"DEAD".equals(task.getStatus())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "仅 FAILED/DEAD 任务可人工重试");
        }
        if (taskMapper.resetToPending(taskId) == 0) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT, "任务状态已变更");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> pendingTenantIds() {
        return taskMapper.pendingTenantIds();
    }

    @Override
    @Transactional
    public int processTenant(long tenantId, int limit) {
        List<IntegrationTask> batch = taskMapper.claimBatch(tenantId, limit);
        for (IntegrationTask stale : taskMapper.findStaleRunning()) {
            if (stale.getTenantId() != null && stale.getTenantId() == tenantId) {
                taskMapper.markStaleFailed(stale.getId(), "stale RUNNING recovered by worker");
            }
        }
        int done = 0;
        for (IntegrationTask task : batch) {
            if (taskMapper.markRunning(task.getId(), task.getVersion(), "system:worker") == 0) {
                continue;
            }
            try {
                dispatch(tenantId, task);
                taskMapper.markSuccess(task.getId());
                auditWorker(task, "SUCCESS", null);
                done++;
            } catch (Exception e) {
                int nextRetry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
                String status = IntegrationRetryPolicy.isDead(nextRetry) ? "DEAD" : "RETRY";
                taskMapper.markFailure(task.getId(), status,
                        IntegrationRetryPolicy.nextRetryAt(nextRetry, Instant.now()),
                        truncate(e.getMessage()));
                auditWorker(task, status, truncate(e.getMessage()));
                log.warn("integration task failed, id={}, status={}", task.getId(), status);
            }
        }
        return done;
    }

    private void dispatch(long tenantId, IntegrationTask task) throws Exception {
        IntegrationConfig config = configMapper.selectOne(new LambdaQueryWrapper<IntegrationConfig>()
                .eq(IntegrationConfig::getTenantId, tenantId)
                .eq(IntegrationConfig::getEnabled, true)
                .eq(IntegrationConfig::getIsDeleted, false)
                .orderByAsc(IntegrationConfig::getId)
                .last("limit 1"));
        if (config == null) {
            throw new IllegalStateException("no enabled integration config for tenant " + tenantId);
        }
        IntegrationAdapter adapter = adapters.get(config.getType());
        if (adapter == null) {
            throw new IllegalStateException("no adapter for type " + config.getType());
        }
        String bizType = task.getBizType() == null ? "" : task.getBizType().toUpperCase(Locale.ROOT);
        if (bizType.contains("DISPATCH") || bizType.contains("WRITEBACK")) {
            adapter.push(config, task.getPayload());
        } else {
            adapter.fetch(config);
        }
    }

    private String encrypt(String rawCredentials) {
        if (rawCredentials == null || rawCredentials.isBlank()) {
            return null;
        }
        if (!cipher.configured()) {
            throw new BusinessException(CommonErrorCode.INTEGRATION_ERROR,
                    "APS_INTEGRATION_SECRET 未配置，凭据写入被拒绝");
        }
        return cipher.encrypt(rawCredentials);
    }

    private void validateType(String type) {
        if (type == null || (!"REST".equals(type) && !"DB_VIEW".equals(type) && !"FILE".equals(type))) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "type 仅允许 REST/DB_VIEW/FILE");
        }
    }

    private IntegrationConfig copy(IntegrationConfig source) {
        IntegrationConfig target = new IntegrationConfig();
        target.setId(source.getId());
        target.setCode(source.getCode());
        target.setName(source.getName());
        target.setType(source.getType());
        target.setEndpoint(source.getEndpoint());
        target.setSignatureType(source.getSignatureType());
        target.setIpAllowlist(source.getIpAllowlist() == null ? "[]" : source.getIpAllowlist());
        target.setCronExpr(source.getCronExpr());
        target.setEnabled(source.getEnabled() == null ? Boolean.TRUE : source.getEnabled());
        target.setVersion(source.getVersion());
        return target;
    }

    private String normalizePayload(String payloadJson, String bizType, String bizKey) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return "{}";
        }
        try {
            objectMapper.readTree(payloadJson);
            return payloadJson;
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "payload 不是合法 JSON");
        }
    }

    private void auditWorker(IntegrationTask task, String outcome, String error) {
        AuditLog logEntry = new AuditLog();
        logEntry.setTenantId(task.getTenantId());
        logEntry.setActor("system:worker");
        logEntry.setAction("INTEGRATION_TASK_" + outcome);
        logEntry.setTargetType("IntegrationTask");
        logEntry.setTargetId(String.valueOf(task.getId()));
        logEntry.setBeforeJson("{\"status\":\"RUNNING\"}");
        logEntry.setAfterJson(error == null ? "{\"status\":\"" + outcome + "\"}"
                : "{\"status\":\"" + outcome + "\",\"error\":\"" + error.replace("\"", "'") + "\"}");
        logEntry.setTraceId(MDC.get(Constants.TRACE_ID));
        auditService.record(logEntry);
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
