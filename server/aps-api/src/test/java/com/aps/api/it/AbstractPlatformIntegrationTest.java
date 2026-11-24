package com.aps.api.it;

import com.aps.api.config.JwtClaims;
import com.aps.api.config.JwtTokenProvider;
import com.aps.api.dto.LoginRequest;
import com.aps.api.dto.SyncRequest;
import com.aps.domain.service.IntegrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B4：Must 级验收的自动化证据（MockMvc + PG）。由两个子类分别提供 CI（Testcontainers）
 * 与本地开发库（APS_DB_URL）运行时；本类只放断言与辅助方法。
 * 所有写操作使用唯一前缀、并在 finally 中恢复被改动的集成配置。
 */
abstract class AbstractPlatformIntegrationTest {

    /** 仅供沙箱种子账号使用，与 R__seed_platform.sql 的开发账号一致。 */
    private static final String DEV_PASSWORD = "Admin@12345";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IntegrationService integrationService;
    @Autowired
    private JwtTokenProvider tokenProvider;

    // ---------------------------------------------------------------- helpers

    private JsonNode loginBody(String username) throws Exception {
        MvcResult result = mockMvc.perform(request(HttpMethod.POST, "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, DEV_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(0, body.path("code").asInt(), body.toString());
        return body.path("data");
    }

    private String token(String username) throws Exception {
        return token(username, DEV_PASSWORD);
    }

    private String token(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(request(HttpMethod.POST, "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private long tenantId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM tenant WHERE code = ?", Long.class, code);
    }

    private MvcResult perform(HttpMethod method, String url, String token, String body) throws Exception {
        var builder = request(method, url);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // ---------------------------------------------------------------- 验收 #2 #7

    static Stream<Arguments> protectedEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/auth/me"),
                Arguments.of("GET", "/api/v1/users"),
                Arguments.of("POST", "/api/v1/users"),
                Arguments.of("GET", "/api/v1/roles"),
                Arguments.of("POST", "/api/v1/roles"),
                Arguments.of("GET", "/api/v1/permissions"),
                Arguments.of("GET", "/api/v1/audit"),
                Arguments.of("GET", "/api/v1/audit/export"),
                Arguments.of("GET", "/api/v1/integrations/configs"),
                Arguments.of("POST", "/api/v1/integrations/configs"),
                Arguments.of("GET", "/api/v1/integrations/tasks"),
                Arguments.of("POST", "/api/v1/orders/sync"),
                Arguments.of("GET", "/api/v1/master/resources"));
    }

    @ParameterizedTest(name = "{0} {1} anonymous -> 401/10002")
    @MethodSource("protectedEndpoints")
    void anonymousRequestIsRejectedOnEveryProtectedEndpoint(String method, String url) throws Exception {
        MvcResult result = perform(HttpMethod.valueOf(method), url, null, null);
        assertEquals(401, result.getResponse().getStatus(), url);
        assertEquals(10002, json(result).path("code").asInt(), url);
    }

    // ---------------------------------------------------------------- 验收 #6

    @ParameterizedTest(name = "viewer {0} -> 403/10003, admin -> 200")
    @MethodSource("viewerProtectedUrls")
    void viewerIsForbiddenWhereAdminIsAllowed(String url) throws Exception {
        MvcResult viewer = perform(HttpMethod.GET, url, token("viewer"), null);
        assertEquals(403, viewer.getResponse().getStatus(), url);
        assertEquals(10003, json(viewer).path("code").asInt(), url);

        MvcResult admin = perform(HttpMethod.GET, url, token("admin"), null);
        assertEquals(200, admin.getResponse().getStatus(), url);
    }

    static Stream<String> viewerProtectedUrls() {
        return Stream.of("/api/v1/users", "/api/v1/audit", "/api/v1/integrations/configs");
    }

    // ---------------------------------------------------------------- B3 / 验收 #1 #4

    @Test
    void disablingAccountImmediatelyInvalidatesOldToken() throws Exception {
        String admin = token("admin");
        String username = unique("it_disable");
        String createBody = objectMapper.writeValueAsString(Map.of(
                "username", username, "password", "Disable@12345",
                "displayName", "IT disable", "roles", List.of("ADMIN")));
        MvcResult created = perform(HttpMethod.POST, "/api/v1/users", admin, createBody);
        assertEquals(200, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        long userId = json(created).path("data").asLong();

        String oldToken = token(username, "Disable@12345");
        assertEquals(200, perform(HttpMethod.GET, "/api/v1/auth/me", oldToken, null)
                .getResponse().getStatus());

        MvcResult disabled = perform(HttpMethod.PUT, "/api/v1/users/" + userId + "/status", admin,
                "{\"status\":\"DISABLED\"}");
        assertEquals(200, disabled.getResponse().getStatus(), disabled.getResponse().getContentAsString());

        MvcResult rejected = perform(HttpMethod.GET, "/api/v1/auth/me", oldToken, null);
        assertEquals(401, rejected.getResponse().getStatus(), "禁用后旧 token 必须 401");
        assertEquals(10002, json(rejected).path("code").asInt());
    }

    // ---------------------------------------------------------------- B1 / 验收 #10 #12

    @Test
    void auditTargetIdIsBusinessEntityIdAndExportIsTenantScoped() throws Exception {
        String admin = token("admin");
        long defaultTenant = tenantId("DEFAULT");
        String username = unique("it_audit");
        MvcResult created = perform(HttpMethod.POST, "/api/v1/users", admin,
                objectMapper.writeValueAsString(Map.of(
                        "username", username, "password", "Audit@12345",
                        "displayName", "IT audit", "roles", List.of())));
        assertEquals(200, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        long userId = json(created).path("data").asLong();

        // 真实 tenantId-first 签名：target_id 必须是 userId 而不是 tenantId
        MvcResult byBusinessId = perform(HttpMethod.GET,
                "/api/v1/audit?action=USER_CREATE&targetId=" + userId, admin, null);
        assertEquals(200, byBusinessId.getResponse().getStatus());
        JsonNode page = json(byBusinessId).path("data");
        assertTrue(page.path("total").asLong() >= 1, "按业务实体 ID 应能查到审计行");
        assertEquals(String.valueOf(userId), page.path("list").get(0).path("targetId").asText());

        MvcResult byTenantId = perform(HttpMethod.GET,
                "/api/v1/audit?action=USER_CREATE&targetId=" + defaultTenant, admin, null);
        assertEquals(0, json(byTenantId).path("data").path("total").asLong(),
                "target_id 不应为租户 ID（B1 回归）");

        // 导出按租户隔离：DEFAULT 看不到 TENANT_B 的种子锚点，TENANT_B 看得到
        MvcResult defaultExport = perform(HttpMethod.GET, "/api/v1/audit/export", admin, null);
        assertEquals(200, defaultExport.getResponse().getStatus());
        assertFalse(defaultExport.getResponse().getContentAsString().contains("SEED_TENANT_MARKER"));

        MvcResult tenantBExport = perform(HttpMethod.GET, "/api/v1/audit/export", token("admin_b"), null);
        assertEquals(200, tenantBExport.getResponse().getStatus());
        assertTrue(tenantBExport.getResponse().getContentAsString().contains("SEED_TENANT_MARKER"));
    }

    // ---------------------------------------------------------------- 验收 #9

    @Test
    @Transactional
    void forcedRlsPolicyFiltersByTenantContext() {
        long defaultTenant = tenantId("DEFAULT");
        long tenantB = tenantId("TENANT_B");
        // DDL 在 PG 中是事务性的；测试事务回滚后 FORCE 自动撤销，不污染其它用例
        jdbcTemplate.execute("ALTER TABLE audit_log FORCE ROW LEVEL SECURITY");
        jdbcTemplate.execute("SET LOCAL app.tenant_id = '" + defaultTenant + "'");
        Long seenByDefault = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'SEED_TENANT_MARKER'", Long.class);
        assertEquals(0L, seenByDefault);

        jdbcTemplate.execute("SET LOCAL app.tenant_id = '" + tenantB + "'");
        Long seenByTenantB = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'SEED_TENANT_MARKER'", Long.class);
        assertEquals(1L, seenByTenantB);
    }

    // ---------------------------------------------------------------- B2 / 验收 #14

    @Test
    void sameIdempotencyKeyReturnsFirstTaskAndIsIsolatedAcrossTenants() throws Exception {
        String admin = token("admin");
        String adminB = token("admin_b");
        String key = unique("key");
        String biz = unique("biz");

        MvcResult first = sync(admin, key, "ORDER_SYNC", biz);
        MvcResult second = sync(admin, key, "ORDER_SYNC", biz);
        assertEquals(200, first.getResponse().getStatus());
        assertEquals(200, second.getResponse().getStatus());
        JsonNode a = json(first).path("data");
        JsonNode b = json(second).path("data");
        assertTrue(a.path("created").asBoolean());
        assertFalse(b.path("created").asBoolean());
        assertEquals(a.path("taskId").asLong(), b.path("taskId").asLong());

        Long rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM integration_task WHERE idempotency_key = ?", Long.class, key);
        assertEquals(1L, rows, "相同租户相同键只应落一条任务");

        // 跨租户复用同一个键：必须各自建单，不能拿到别的租户的 taskId
        MvcResult other = sync(adminB, key, "ORDER_SYNC", unique("biz"));
        assertEquals(200, other.getResponse().getStatus(), other.getResponse().getContentAsString());
        JsonNode c = json(other).path("data");
        assertTrue(c.path("created").asBoolean(), "跨租户相同键应视为不同任务");
        assertNotEquals(a.path("taskId").asLong(), c.path("taskId").asLong());
        Long allRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM integration_task WHERE idempotency_key = ?", Long.class, key);
        assertEquals(2L, allRows);
    }

    private MvcResult sync(String token, String key, String bizType, String bizKey) throws Exception {
        String body = objectMapper.writeValueAsString(new SyncRequest(bizType, bizKey, key, Map.of("source", "it")));
        return perform(HttpMethod.POST, "/api/v1/orders/sync", token, body);
    }

    // ---------------------------------------------------------------- 验收 #15

    @Test
    void failedRetryDeadAndManualRetryStateMachine() throws Exception {
        String admin = token("admin");
        long defaultTenant = tenantId("DEFAULT");
        MvcResult created = sync(admin, unique("key"), "ORDER_SYNC", unique("biz"));
        long taskId = json(created).path("data").path("taskId").asLong();

        // 关闭该租户所有配置，使 worker 派发必然失败（构造可控失败）
        List<Map<String, Object>> configs = jdbcTemplate.queryForList(
                "SELECT id, enabled FROM integration_config WHERE tenant_id = ?", defaultTenant);
        configs.forEach(c -> jdbcTemplate.update("UPDATE integration_config SET enabled = false WHERE id = ?",
                c.get("id")));
        try {
            jdbcTemplate.update("UPDATE integration_task SET status='FAILED', retry_count=0, "
                    + "next_retry_at = now() - interval '1 minute', last_error='boom' WHERE id = ?", taskId);
            integrationService.processTenant(defaultTenant, 100);
            assertEquals("RETRY", statusOf(taskId));
            assertEquals(1, retryCountOf(taskId));

            jdbcTemplate.update("UPDATE integration_task SET status='RETRY', retry_count=5, "
                    + "next_retry_at = now() - interval '1 minute' WHERE id = ?", taskId);
            integrationService.processTenant(defaultTenant, 100);
            assertEquals("DEAD", statusOf(taskId));
            assertEquals(6, retryCountOf(taskId));

            MvcResult retried = perform(HttpMethod.POST, "/api/v1/integrations/tasks/" + taskId + "/retry",
                    admin, null);
            assertEquals(200, retried.getResponse().getStatus(), retried.getResponse().getContentAsString());
            assertEquals("PENDING", statusOf(taskId));
            assertEquals(0, retryCountOf(taskId));
        } finally {
            configs.forEach(c -> jdbcTemplate.update("UPDATE integration_config SET enabled = ? WHERE id = ?",
                    c.get("enabled"), c.get("id")));
        }
    }

    private String statusOf(long taskId) {
        return jdbcTemplate.queryForObject("SELECT status FROM integration_task WHERE id = ?", String.class, taskId);
    }

    private int retryCountOf(long taskId) {
        return jdbcTemplate.queryForObject("SELECT retry_count FROM integration_task WHERE id = ?", Integer.class, taskId);
    }

    // ---------------------------------------------------------------- 验收 #8（应用层 ABAC 工厂级数据权限）

    @Test
    void factoryScopeFiltersListToVisibleSubtree() throws Exception {
        long tenant = tenantId("DEFAULT");
        String prefix = unique("it_scope");
        // 构造工厂/车间树：A(根) -> A1(子) -> A2(孙)；B(根) -> B1(子)
        long rootA = insertResource(tenant, prefix + "_A", null);
        long childA1 = insertResource(tenant, prefix + "_A1", rootA);
        long grandA2 = insertResource(tenant, prefix + "_A2", childA1);
        long rootB = insertResource(tenant, prefix + "_B", null);
        long childB1 = insertResource(tenant, prefix + "_B1", rootB);
        try {
            // 用 admin 的合法身份事实，仅替换 factoryIds 为 [A]，签一个带工厂范围的 token
            JwtClaims base = tokenProvider.parse(token("admin"));
            String scoped = tokenProvider.createToken(new JwtClaims(
                    base.subject(), base.tenantId(), base.username(), base.roles(),
                    List.of(rootA), base.jti(), base.expiresAt(), base.tokenVersion()));

            MvcResult result = perform(HttpMethod.GET, "/api/v1/master/resources?pageSize=200", scoped, null);
            assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
            JsonNode page = json(result).path("data");
            Set<Long> ids = new HashSet<>();
            page.path("list").forEach(n -> ids.add(n.path("id").asLong()));

            assertTrue(ids.contains(rootA), "工厂 A 根节点应可见");
            assertTrue(ids.contains(childA1), "工厂 A 子节点应可见（子树展开）");
            assertTrue(ids.contains(grandA2), "工厂 A 孙节点应可见（递归 CTE）");
            assertFalse(ids.contains(rootB), "范围外工厂 B 根节点必须不可见");
            assertFalse(ids.contains(childB1), "范围外工厂 B 子节点必须不可见");
        } finally {
            jdbcTemplate.update("DELETE FROM resource WHERE code LIKE ?", prefix + "_%");
        }
    }

    @Test
    void factoryScopeRejectsOutOfScopeGetById() throws Exception {
        long tenant = tenantId("DEFAULT");
        String prefix = unique("it_getscope");
        long rootA = insertResource(tenant, prefix + "_A", null);
        long childA1 = insertResource(tenant, prefix + "_A1", rootA);
        long rootB = insertResource(tenant, prefix + "_B", null);
        try {
            JwtClaims base = tokenProvider.parse(token("admin"));
            String scoped = tokenProvider.createToken(new JwtClaims(
                    base.subject(), base.tenantId(), base.username(), base.roles(),
                    List.of(rootA), base.jti(), base.expiresAt(), base.tokenVersion()));

            // 范围内按 id 读取：200
            MvcResult ok = perform(HttpMethod.GET, "/api/v1/master/resources/" + childA1, scoped, null);
            assertEquals(200, ok.getResponse().getStatus());

            // 越权按 id 读取范围外工厂：403/10003
            MvcResult denied = perform(HttpMethod.GET, "/api/v1/master/resources/" + rootB, scoped, null);
            assertEquals(403, denied.getResponse().getStatus());
            assertEquals(10003, json(denied).path("code").asInt());
        } finally {
            jdbcTemplate.update("DELETE FROM resource WHERE code LIKE ?", prefix + "_%");
        }
    }

    @Test
    void noFactoryRestrictionAdminSeesWholeTenant() throws Exception {
        long tenant = tenantId("DEFAULT");
        String prefix = unique("it_allscope");
        long rootA = insertResource(tenant, prefix + "_A", null);
        long rootB = insertResource(tenant, prefix + "_B", null);
        try {
            String admin = token("admin");
            MvcResult result = perform(HttpMethod.GET, "/api/v1/master/resources?pageSize=200", admin, null);
            assertEquals(200, result.getResponse().getStatus());
            Set<Long> ids = new HashSet<>();
            json(result).path("data").path("list").forEach(n -> ids.add(n.path("id").asLong()));
            assertTrue(ids.contains(rootA), "无工厂限制应可见 A");
            assertTrue(ids.contains(rootB), "无工厂限制应可见 B");
        } finally {
            jdbcTemplate.update("DELETE FROM resource WHERE code LIKE ?", prefix + "_%");
        }
    }

    private long insertResource(long tenant, String code, Long parentId) {
        jdbcTemplate.update("INSERT INTO resource(tenant_id, code, name, type, parent_id) "
                        + "VALUES (?, ?, ?, 'WORKSHOP', ?)",
                tenant, code, code, parentId);
        return jdbcTemplate.queryForObject("SELECT id FROM resource WHERE tenant_id = ? AND code = ?",
                Long.class, tenant, code);
    }
}
