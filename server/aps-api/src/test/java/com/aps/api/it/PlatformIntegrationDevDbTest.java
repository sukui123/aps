package com.aps.api.it;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 本地开发库运行时：仅在注入了 APS_DB_URL 时启用（连接信息全部来自环境占位符，
 * 源码中无任何地址/口令）。CI 无该变量时自动跳过，由 Testcontainers 子类接管。
 */
@EnabledIfEnvironmentVariable(named = "APS_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"aps.worker.enabled=false",
                "jwt.secret=local-dev-db-test-secret-at-least-32-chars",
                "aps.integration.secret=local-dev-db-integration-secret-32"})
@AutoConfigureMockMvc
class PlatformIntegrationDevDbTest extends AbstractPlatformIntegrationTest {
}
