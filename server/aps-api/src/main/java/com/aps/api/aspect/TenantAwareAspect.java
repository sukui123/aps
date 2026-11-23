package com.aps.api.aspect;

import com.aps.common.constant.Constants;
import com.aps.domain.service.DataScope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * 在事务绑定连接上执行 SET LOCAL（PF-02）。事务通知 order=100，本切面 order=200，
 * 保证运行在事务开启之后、业务方法之前；无租户上下文（登录/白名单）时不设置。
 */
@Aspect
@Component
@Order(200)
public class TenantAwareAspect {

    private final DataSource dataSource;

    public TenantAwareAspect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || "
            + "@within(org.springframework.transaction.annotation.Transactional)")
    public Object apply(ProceedingJoinPoint joinPoint) throws Throwable {
        DataScope scope = DataScope.current();
        if (scope.getTenantId() == null) {
            return joinPoint.proceed();
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            setLocal(connection, Constants.RLS_TENANT_SETTING, String.valueOf(scope.getTenantId()));
            if (scope.hasFactoryRestriction()) {
                setLocal(connection, Constants.RLS_FACTORY_SETTING, scope.factorySettingValue());
            }
            return joinPoint.proceed();
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private void setLocal(Connection connection, String setting, String value) throws Exception {
        // set_config(..., true) 等价于 SET LOCAL：仅在当前事务内生效
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config(?, ?, true)")) {
            statement.setString(1, setting);
            statement.setString(2, value);
            statement.execute();
        }
    }
}
