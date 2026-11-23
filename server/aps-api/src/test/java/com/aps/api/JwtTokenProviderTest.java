package com.aps.api;

import com.aps.api.config.JwtClaims;
import com.aps.api.config.JwtProperties;
import com.aps.api.config.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** JWT claims 完整性验签（PF-01/PF-02 验收 #1/#2）。 */
class JwtTokenProviderTest {

    private JwtTokenProvider provider() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-at-least-32-characters-long");
        return new JwtTokenProvider(properties);
    }

    @Test
    void carriesTenantRolesFactoryAndJti() {
        JwtTokenProvider provider = provider();
        Instant expires = Instant.now().plus(1, ChronoUnit.HOURS);
        String token = provider.createToken(new JwtClaims("1001", 7L, "planner",
                List.of("PLANNER"), List.of(3L, 4L), "jti-123", expires, 2L));

        JwtClaims parsed = provider.parse(token);
        assertEquals("1001", parsed.subject());
        assertEquals(7L, parsed.tenantId());
        assertEquals("planner", parsed.username());
        assertEquals(List.of("PLANNER"), parsed.roles());
        assertEquals(List.of(3L, 4L), parsed.factoryIds());
        assertEquals("jti-123", parsed.jti());
        assertEquals(2L, parsed.tokenVersion());
        assertNotNull(parsed.expiresAt());
    }

    @Test
    void rejectsTamperedToken() {
        JwtTokenProvider provider = provider();
        String token = provider.createToken(new JwtClaims("1", 1L, "u", List.of(), List.of(), "j", null, 0L));
        assertThrows(JwtException.class, () -> provider.parse(token + "tampered"));
    }
}
