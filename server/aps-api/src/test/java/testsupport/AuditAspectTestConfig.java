package testsupport;

import com.aps.api.aspect.AuditAspect;
import com.aps.api.AuditAspectContractTest.TargetService;
import com.aps.domain.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.mockito.Mockito.mock;

/**
 * 审计切面隔离测试用的最小上下文。故意放在 testsupport 包（不在 com.aps 下），
 * 避免被 ApsApplication 的 @ComponentScan("com.aps") 扫进 @SpringBootTest 全量上下文。
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AuditAspectTestConfig {

    @Bean
    public AuditService auditServiceMock() {
        return mock(AuditService.class);
    }

    @Bean
    public AuditAspect auditAspect(AuditService auditServiceMock) {
        return new AuditAspect(auditServiceMock, new ObjectMapper());
    }

    @Bean
    public TargetService targetService() {
        return new TargetService();
    }
}
