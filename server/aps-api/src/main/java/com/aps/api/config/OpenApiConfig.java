package com.aps.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** SpringDoc OpenAPI 契约（对齐技术方案 §5）。 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apsOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("APS 排产系统 API")
                .version("0.1.0")
                .description("骨架阶段：接口契约以技术方案 §5 为准，业务实现后续迭代"));
    }
}
