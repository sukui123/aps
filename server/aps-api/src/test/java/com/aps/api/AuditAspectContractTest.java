package com.aps.api;

import com.aps.common.annotation.Audited;
import com.aps.domain.model.AuditLog;
import com.aps.domain.service.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import testsupport.AuditAspectTestConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Audited 契约（PF-03 验收 #10/#11）：
 * 用与生产一致的 tenantId-first 真实签名，断言 target_id 为业务实体 ID 而非租户 ID，
 * 并覆盖成功/失败两种留痕路径与口令掩码。修复 B1 的假阳性测试。
 */
public class AuditAspectContractTest {

    public record TaskResult(Long taskId) {
    }

    public static class TargetService {
        /** 与 AuthServiceImpl.createUser 同签名：tenantId 是首参。 */
        @Audited(action = "USER_CREATE", targetType = "SysUser", target = "#result")
        public Long createUser(Long tenantId, String username, String rawPassword) {
            return 12345L;
        }

        /** 与 AuthServiceImpl.changeStatus 同签名：显式从命名参数取业务 ID。 */
        @Audited(action = "USER_STATUS_CHANGE", targetType = "SysUser", target = "#userId")
        public void changeStatus(Long tenantId, Long userId, String status) {
            // no-op stub
        }

        /** 与 IntegrationServiceImpl.submitTask 同签名：从结果记录的 taskId() 取 ID。 */
        @Audited(action = "ORDER_SYNC_TRIGGER", targetType = "IntegrationTask", target = "#result?.taskId()")
        public TaskResult submitTask(Long tenantId, String bizType, String bizKey) {
            return new TaskResult(777L);
        }

        /** 失败留痕：仍须用显式表达式取业务 ID，而非 tenantId。 */
        @Audited(action = "ROLE_UPDATE", targetType = "SysRole", target = "#roleId")
        public void updateRole(Long tenantId, Long roleId, String name) {
            throw new IllegalStateException("boom");
        }
    }

    private static AuditLog recorded(AuditService service) {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(service).record(captor.capture());
        return captor.getValue();
    }

    @Test
    void targetIdIsCreatedEntityIdNotTenantIdAndPasswordIsMasked() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AuditAspectTestConfig.class)) {
            TargetService service = context.getBean(TargetService.class);
            AuditService auditService = context.getBean(AuditService.class);

            assertEquals(12345L, service.createUser(1L, "alice", "TopSecret123"));

            AuditLog log = recorded(auditService);
            assertEquals("USER_CREATE", log.getAction());
            assertEquals("SysUser", log.getTargetType());
            // B1 回归点：断言落库 target_id 是新建用户 ID，不是首参 tenantId=1
            assertEquals("12345", log.getTargetId());
            assertNotEquals("1", log.getTargetId());
            assertTrue(log.getBeforeJson().contains("\"tenantId\":1"));
            assertTrue(log.getBeforeJson().contains("\"username\":\"alice\""));
            assertTrue(log.getBeforeJson().contains("****"));
            assertFalse(log.getBeforeJson().contains("TopSecret123"));
            assertTrue(log.getAfterJson().contains("SUCCESS"));
        }
    }

    @Test
    void targetIdIsResolvedFromNamedIdParameter() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AuditAspectTestConfig.class)) {
            TargetService service = context.getBean(TargetService.class);
            AuditService auditService = context.getBean(AuditService.class);

            service.changeStatus(1L, 77L, "DISABLED");

            assertEquals("77", recorded(auditService).getTargetId());
        }
    }

    @Test
    void targetIdIsResolvedFromResultRecord() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AuditAspectTestConfig.class)) {
            TargetService service = context.getBean(TargetService.class);
            AuditService auditService = context.getBean(AuditService.class);

            service.submitTask(1L, "ORDER_SYNC", "key-1");

            assertEquals("777", recorded(auditService).getTargetId());
        }
    }

    @Test
    void failedOperationRecordsStandaloneWithBusinessTargetId() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AuditAspectTestConfig.class)) {
            TargetService service = context.getBean(TargetService.class);
            AuditService auditService = context.getBean(AuditService.class);

            assertThrows(IllegalStateException.class, () -> service.updateRole(1L, 88L, "r"));

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditService).recordStandalone(captor.capture());
            assertEquals("88", captor.getValue().getTargetId());
            assertTrue(captor.getValue().getAfterJson().contains("FAILED"));
        }
    }
}
