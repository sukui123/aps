package com.aps.api.it;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CI 运行时：Testcontainers 拉起一次性 PG15，Flyway 从 V1 跑到 V4 + seed。
 * 无 Docker 的环境整类跳过（如实记为 Skipped，不伪装通过）。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"aps.worker.enabled=false"})
@AutoConfigureMockMvc
class PlatformIntegrationTest extends AbstractPlatformIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("aps_it").withUsername("aps_it").withPassword("aps_it");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "integration-test-secret-at-least-32-characters");
        registry.add("aps.integration.secret", () -> "integration-test-credentials-secret-32");
    }
}
