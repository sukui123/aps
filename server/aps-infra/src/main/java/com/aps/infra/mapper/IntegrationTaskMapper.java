package com.aps.infra.mapper;

import com.aps.domain.model.IntegrationTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/** 集成任务 Mapper：幂等落库 + 行锁领取 + 乐观锁状态流转（PG15 方言）。 */
@Mapper
public interface IntegrationTaskMapper extends BaseMapper<IntegrationTask> {

    @Insert("INSERT INTO integration_task (id, tenant_id, biz_type, biz_key, idempotency_key, payload, "
            + "status, retry_count, created_at, updated_at, version, is_deleted) "
            + "VALUES (#{id}, #{tenantId}, #{bizType}, #{bizKey}, #{idempotencyKey}, CAST(#{payload} AS jsonb), "
            + "'PENDING', 0, now(), now(), 0, false) "
            // 不指定冲突目标：同时覆盖 uq_integration_biz 与 uq_integration_idempotency(V3)
            + "ON CONFLICT DO NOTHING")
    int insertIdempotent(IntegrationTask task);

    /** 幂等键必须带租户谓词：禁止把别的租户的任务 ID 返回给当前租户（B2）。 */
    @Select("SELECT * FROM integration_task WHERE tenant_id = #{tenantId} "
            + "AND idempotency_key = #{key} AND is_deleted = false")
    IntegrationTask findByIdempotencyKey(@Param("tenantId") Long tenantId, @Param("key") String key);

    @Select("SELECT * FROM integration_task WHERE tenant_id = #{tenantId} AND biz_type = #{bizType} "
            + "AND biz_key = #{bizKey} AND is_deleted = false")
    IntegrationTask findByBizKey(@Param("tenantId") Long tenantId, @Param("bizType") String bizType,
                                 @Param("bizKey") String bizKey);

    @Select("SELECT * FROM integration_task WHERE tenant_id = #{tenantId} AND is_deleted = false "
            + "AND (status = 'PENDING' OR ((status = 'RETRY' OR status = 'FAILED') AND next_retry_at <= now())) "
            + "ORDER BY created_at LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<IntegrationTask> claimBatch(@Param("tenantId") long tenantId, @Param("limit") int limit);

    @Select("SELECT DISTINCT tenant_id FROM integration_task WHERE is_deleted = false "
            + "AND (status = 'PENDING' OR ((status = 'RETRY' OR status = 'FAILED') AND next_retry_at <= now()))")
    List<Long> pendingTenantIds();

    @Select("SELECT * FROM integration_task WHERE status = 'RUNNING' AND is_deleted = false "
            + "AND updated_at < now() - interval '10 minutes'")
    List<IntegrationTask> findStaleRunning();

    @Update("UPDATE integration_task SET status = 'RUNNING', updated_at = now(), updated_by = #{actor}, "
            + "version = version + 1 WHERE id = #{id} AND version = #{version} "
            + "AND status IN ('PENDING','RETRY','FAILED')")
    int markRunning(@Param("id") Long id, @Param("version") Long version, @Param("actor") String actor);

    /** 行已由 claimBatch 的 FOR UPDATE 锁定，且 markRunning 已做一次乐观锁校验，终态更新不再重复比版本。 */
    @Update("UPDATE integration_task SET status = 'SUCCESS', last_error = NULL, next_retry_at = NULL, "
            + "updated_at = now(), version = version + 1 WHERE id = #{id}")
    int markSuccess(@Param("id") Long id);

    @Update("UPDATE integration_task SET status = #{status}, retry_count = retry_count + 1, "
            + "next_retry_at = #{nextRetryAt}, last_error = #{lastError}, updated_at = now(), "
            + "version = version + 1 WHERE id = #{id}")
    int markFailure(@Param("id") Long id, @Param("status") String status,
                    @Param("nextRetryAt") Instant nextRetryAt, @Param("lastError") String lastError);

    @Update("UPDATE integration_task SET status = 'PENDING', retry_count = 0, next_retry_at = now(), "
            + "last_error = NULL, updated_at = now(), version = version + 1 "
            + "WHERE id = #{id} AND status IN ('FAILED','DEAD') AND is_deleted = false")
    int resetToPending(@Param("id") Long id);

    @Update("UPDATE integration_task SET status = 'FAILED', next_retry_at = now(), "
            + "last_error = #{lastError}, updated_at = now(), version = version + 1 "
            + "WHERE id = #{id} AND status = 'RUNNING'")
    int markStaleFailed(@Param("id") Long id, @Param("lastError") String lastError);
}
