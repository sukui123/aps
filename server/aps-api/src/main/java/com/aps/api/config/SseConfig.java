package com.aps.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SSE 配置（技术方案 §2.4）：进度先落 DB，SSE 从 DB 读取后推送；断线退化为 GET /runs/{id} 轮询。
 * 长连接超时设为 5 分钟。
 */
@Configuration
public class SseConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(300_000L);
    }
}
