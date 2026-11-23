package com.aps.infra.mapper;

import com.aps.domain.model.AuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 审计 Mapper：JSONB 字段显式 CAST 写入；动态查询由 BaseMapper + QueryWrapper 承担。 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Insert("INSERT INTO audit_log (tenant_id, actor, action, target_type, target_id, "
            + "before_json, after_json, trace_id, created_at, is_deleted) "
            + "VALUES (#{tenantId}, #{actor}, #{action}, #{targetType}, #{targetId}, "
            + "CAST(#{beforeJson} AS jsonb), CAST(#{afterJson} AS jsonb), #{traceId}, now(), false)")
    int insertAudit(AuditLog log);
}
