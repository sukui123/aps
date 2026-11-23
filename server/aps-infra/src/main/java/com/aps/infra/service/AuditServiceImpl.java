package com.aps.infra.service;

import com.aps.common.result.PageResult;
import com.aps.domain.model.AuditLog;
import com.aps.domain.service.AuditService;
import com.aps.infra.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 审计实现（PF-03）：写入与业务同事务，查询/导出共用同一条件与租户作用域。 */
@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper mapper;

    public AuditServiceImpl(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void record(AuditLog auditLog) {
        mapper.insertAudit(auditLog);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStandalone(AuditLog auditLog) {
        mapper.insertAudit(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditLog> query(Query query) {
        Page<AuditLog> req = new Page<>(query.page(), query.pageSize());
        var result = mapper.selectPage(req, wrapper(query));
        return PageResult.of(result.getRecords(), result.getTotal(), query.page(), query.pageSize());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> search(Query query) {
        return mapper.selectList(wrapper(query));
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(Query query) {
        StringBuilder sb = new StringBuilder("id,tenantId,actor,action,targetType,targetId,beforeJson,afterJson,traceId,createdAt\n");
        for (AuditLog log : search(query)) {
            sb.append(csv(log.getId())).append(',').append(csv(log.getTenantId())).append(',')
                    .append(csv(log.getActor())).append(',').append(csv(log.getAction())).append(',')
                    .append(csv(log.getTargetType())).append(',').append(csv(log.getTargetId())).append(',')
                    .append(csv(log.getBeforeJson())).append(',').append(csv(log.getAfterJson())).append(',')
                    .append(csv(log.getTraceId())).append(',').append(csv(log.getCreatedAt())).append('\n');
        }
        return sb.toString();
    }

    private LambdaQueryWrapper<AuditLog> wrapper(Query query) {
        LambdaQueryWrapper<AuditLog> w = new LambdaQueryWrapper<AuditLog>()
                .eq(query.tenantId() != null, AuditLog::getTenantId, query.tenantId())
                .eq(query.actor() != null && !query.actor().isBlank(), AuditLog::getActor, query.actor())
                .eq(query.action() != null && !query.action().isBlank(), AuditLog::getAction, query.action())
                .eq(query.targetType() != null && !query.targetType().isBlank(), AuditLog::getTargetType, query.targetType())
                .eq(query.targetId() != null && !query.targetId().isBlank(), AuditLog::getTargetId, query.targetId())
                .ge(query.from() != null, AuditLog::getCreatedAt, query.from())
                .le(query.to() != null, AuditLog::getCreatedAt, query.to())
                .eq(AuditLog::getIsDeleted, false)
                .orderByDesc(AuditLog::getCreatedAt);
        return w;
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value).replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\n") || s.contains("\"")) {
            return "\"" + s + "\"";
        }
        return s;
    }
}
