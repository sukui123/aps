package com.aps.infra.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** DB 视图兜底路径的下达结果回写表（V2.integration_writeback）。 */
@Mapper
public interface IntegrationWritebackMapper {

    @Insert("INSERT INTO integration_writeback (tenant_id, biz_type, biz_key, payload, created_at) "
            + "VALUES (#{tenantId}, #{bizType}, #{bizKey}, CAST(#{payload} AS jsonb), now())")
    int writeback(@Param("tenantId") Long tenantId, @Param("bizType") String bizType,
                  @Param("bizKey") String bizKey, @Param("payload") String payload);
}
