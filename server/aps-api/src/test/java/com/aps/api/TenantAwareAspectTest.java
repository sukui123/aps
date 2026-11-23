package com.aps.api;

import com.aps.api.aspect.TenantAwareAspect;
import com.aps.common.context.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 租户上下文在事务连接上转换为 SET LOCAL（PF-02 验收 #9）。 */
class TenantAwareAspectTest {

    @AfterEach
    void clean() {
        TenantContext.clear();
    }

    @Test
    void setsLocalTenantOnBoundConnection() throws Throwable {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");

        TenantContext.set(42L, List.of(5L), 1L, "u", "jti");
        Object result = new TenantAwareAspect(dataSource).apply(joinPoint);

        assertEquals("ok", result);
        verify(statement).setString(1, "app.tenant_id");
        verify(statement).setString(2, "42");
        verify(statement, times(2)).execute();
        verify(statement).setString(1, "app.factory_ids");
        verify(statement).setString(2, "5");
    }
}
