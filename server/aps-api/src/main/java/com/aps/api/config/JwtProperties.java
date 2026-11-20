package com.aps.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** JWT 配置：secret 只从环境变量 APS_JWT_SECRET 注入（技术方案 §8.6）。 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs = 3_600_000L;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.length() < 32) {
            // fail-fast：不打印密钥值，只给出状态提示
            throw new IllegalStateException("jwt.secret missing or too short; set APS_JWT_SECRET (min 32 chars)");
        }
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
