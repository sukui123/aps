package com.aps.infra.mapper;

import com.aps.domain.model.IntegrationConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/** 集成配置 Mapper：JSONB 白名单显式 CAST；凭据密文为不可回显字段。 */
@Mapper
public interface IntegrationConfigMapper extends BaseMapper<IntegrationConfig> {

    @Insert("INSERT INTO integration_config (id, tenant_id, code, name, type, endpoint, credentials_cipher, "
            + "signature_type, ip_allowlist, cron_expr, enabled, created_at, created_by, updated_at, "
            + "updated_by, version, is_deleted) "
            + "VALUES (#{id}, #{tenantId}, #{code}, #{name}, #{type}, #{endpoint}, #{credentialsCipher}, "
            + "#{signatureType}, CAST(#{ipAllowlist} AS jsonb), #{cronExpr}, #{enabled}, now(), #{createdBy}, "
            + "now(), #{updatedBy}, 0, false) "
            + "ON CONFLICT (tenant_id, code) DO NOTHING")
    int insertConfig(IntegrationConfig config);

    @Update("UPDATE integration_config SET name = #{name}, type = #{type}, endpoint = #{endpoint}, "
            + "credentials_cipher = COALESCE(#{credentialsCipher}, credentials_cipher), "
            + "signature_type = #{signatureType}, ip_allowlist = CAST(#{ipAllowlist} AS jsonb), "
            + "cron_expr = #{cronExpr}, enabled = #{enabled}, updated_at = now(), updated_by = #{updatedBy}, "
            + "version = version + 1 WHERE id = #{id} AND tenant_id = #{tenantId} AND version = #{version} "
            + "AND is_deleted = false")
    int updateConfig(IntegrationConfig config);
}
