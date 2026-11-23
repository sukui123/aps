package com.aps.api;

import com.aps.common.context.TenantContext;
import com.aps.domain.service.DataScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 租户上下文 ThreadLocal 生命周期（PF-02 验收 #9）。 */
class TenantContextTest {

    @AfterEach
    void clean() {
        TenantContext.clear();
    }

    @Test
    void setReadAndClear() {
        assertTrue(TenantContext.isEmpty());
        TenantContext.set(11L, List.of(2L, 3L), 99L, "planner", "jti-1");
        assertEquals(11L, TenantContext.tenantId());
        assertEquals(99L, TenantContext.userId());
        assertEquals(List.of(2L, 3L), TenantContext.factoryIds());

        DataScope scope = DataScope.current();
        assertEquals(11L, scope.getTenantId());
        assertTrue(scope.hasFactoryRestriction());
        assertEquals("2,3", scope.factorySettingValue());

        TenantContext.clear();
        assertTrue(TenantContext.isEmpty());
        assertFalse(DataScope.current().hasFactoryRestriction());
    }
}
