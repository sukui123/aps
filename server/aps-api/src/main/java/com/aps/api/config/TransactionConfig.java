package com.aps.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 显式指定事务通知顺序，使 TenantAwareAspect（@Order(200)）运行在事务开启之后，
 * 从而在事务绑定的同一连接上执行 SET LOCAL app.tenant_id。
 */
@Configuration
@EnableTransactionManagement(order = 100)
public class TransactionConfig {
}
