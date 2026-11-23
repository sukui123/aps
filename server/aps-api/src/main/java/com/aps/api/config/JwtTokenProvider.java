package com.aps.api.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** JWT 签发/解析（PF-01/02）：claims 含 sub/tenantId/username/roles/factoryIds/jti/exp。 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.getExpirationMs();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String createToken(JwtClaims claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(claims.subject())
                .id(claims.jti())
                .claim("tenantId", claims.tenantId())
                .claim("username", claims.username())
                .claim("roles", claims.roles())
                .claim("factoryIds", claims.factoryIds())
                .claim("ver", claims.tokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(claims.expiresAt() != null ? claims.expiresAt() : now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /** 验签 + 解析；签名不符/过期/格式错误均抛 JwtException 或其子类。 */
    public JwtClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Number tenant = claims.get("tenantId", Number.class);
        Number ver = claims.get("ver", Number.class);
        List<?> rolesValue = claims.get("roles", List.class);
        List<?> factoriesValue = claims.get("factoryIds", List.class);
        Date expiresAt = claims.getExpiration();
        return new JwtClaims(
                claims.getSubject(),
                tenant == null ? null : tenant.longValue(),
                claims.get("username", String.class),
                rolesValue == null ? List.of() : rolesValue.stream().map(String::valueOf).toList(),
                factoriesValue == null ? List.of()
                        : factoriesValue.stream().map(v -> ((Number) v).longValue()).toList(),
                claims.getId(),
                expiresAt == null ? null : expiresAt.toInstant(),
                ver == null ? 0L : ver.longValue());
    }
}
