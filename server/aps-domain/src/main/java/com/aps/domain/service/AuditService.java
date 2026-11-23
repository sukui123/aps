package com.aps.domain.service;

import com.aps.common.result.PageResult;
import com.aps.domain.model.AuditLog;

import java.time.Instant;
import java.util.List;

/** 审计写入/查询/导出端口（PF-03）。 */
public interface AuditService {

    /** 与业务操作同事务写入，避免“改了没留痕”。 */
    void record(AuditLog auditLog);

    /** 业务事务失败回滚时仍需留痕（失败操作），使用独立事务写入。 */
    void recordStandalone(AuditLog auditLog);

    PageResult<AuditLog> query(Query query);

    List<AuditLog> search(Query query);

    /** 导出 CSV（表头固定），内容与查询同作用域。 */
    String exportCsv(Query query);

    record Query(Long tenantId, String actor, String action, String targetType, String targetId,
                 Instant from, Instant to, int page, int pageSize) {
    }
}
