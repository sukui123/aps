package com.aps.infra.integration;

import com.aps.domain.model.IntegrationConfig;
import com.aps.domain.port.IntegrationAdapter;
import com.aps.infra.mapper.IntegrationWritebackMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * DB 视图兜底适配器（PF-04/US-PF04-5）：从配置的 PG 视图读订单，向下发回写表写结果。
 * 视图名只允许标识符，避免 SQL 注入；租户由视图的 tenant_id 列 + RLS 双重约束。
 */
@Component
public class DbViewAdapter implements IntegrationAdapter {

    private static final java.util.regex.Pattern IDENTIFIER =
            java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_.]{0,127}");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final IntegrationWritebackMapper writebackMapper;

    public DbViewAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper,
                         IntegrationWritebackMapper writebackMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.writebackMapper = writebackMapper;
    }

    @Override
    public String type() {
        return "DB_VIEW";
    }

    @Override
    public String fetch(IntegrationConfig config) throws Exception {
        String view = requireView(config);
        String sql = "SELECT coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb)::text FROM " + view
                + " t WHERE t.tenant_id = ?";
        String json = jdbc.queryForObject(sql, String.class, config.getTenantId());
        return json == null ? "[]" : json;
    }

    @Override
    public boolean push(IntegrationConfig config, String payloadJson) throws Exception {
        requireView(config);
        Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<>() {
        });
        String bizType = String.valueOf(payload.getOrDefault("bizType", config.getCode()));
        String bizKey = String.valueOf(payload.getOrDefault("bizKey", config.getCode()));
        writebackMapper.writeback(config.getTenantId(), bizType, bizKey, payloadJson);
        return true;
    }

    private String requireView(IntegrationConfig config) {
        String endpoint = config.getEndpoint();
        if (endpoint == null || !IDENTIFIER.matcher(endpoint).matches()) {
            throw new IllegalArgumentException("invalid DB_VIEW endpoint (expected a SQL view identifier)");
        }
        return endpoint;
    }
}
