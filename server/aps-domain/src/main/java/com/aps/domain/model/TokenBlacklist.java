package com.aps.domain.model;

import java.time.Instant;

/** JWT 登出黑名单（V1.token_blacklist），DB 为唯一真相源，不依赖 Redis。 */
public class TokenBlacklist {

    private Long id;
    private String jti;
    private Instant expiresAt;
    private Instant createdAt;

    public TokenBlacklist() {
    }

    public TokenBlacklist(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
