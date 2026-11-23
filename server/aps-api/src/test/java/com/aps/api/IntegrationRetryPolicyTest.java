package com.aps.api;

import com.aps.domain.service.IntegrationRetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** 失败退避 1/2/4/8/16 分钟与 DEAD 终态（PF-04 验收 #15）。 */
class IntegrationRetryPolicyTest {

    @Test
    void backoffSequence() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertEquals(now.plusSeconds(60), IntegrationRetryPolicy.nextRetryAt(1, now));
        assertEquals(now.plusSeconds(120), IntegrationRetryPolicy.nextRetryAt(2, now));
        assertEquals(now.plusSeconds(240), IntegrationRetryPolicy.nextRetryAt(3, now));
        assertEquals(now.plusSeconds(480), IntegrationRetryPolicy.nextRetryAt(4, now));
        assertEquals(now.plusSeconds(960), IntegrationRetryPolicy.nextRetryAt(5, now));
    }

    @Test
    void deadAfterMaxRetries() {
        assertFalse(IntegrationRetryPolicy.isDead(5));
        assertTrue(IntegrationRetryPolicy.isDead(6));
    }
}
