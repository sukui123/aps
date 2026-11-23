package com.aps.api.config;

import java.time.Instant;
import java.util.List;

/** JWT 解析后的身份事实（PF-01/02）。 */
public record JwtClaims(String subject, Long tenantId, String username, List<String> roles,
                        List<Long> factoryIds, String jti, Instant expiresAt, Long tokenVersion) {
}
