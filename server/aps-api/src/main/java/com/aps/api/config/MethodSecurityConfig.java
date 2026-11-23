package com.aps.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** 开启方法级功能权限（PF-02）：@PreAuthorize("hasAuthority('...')")。 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
