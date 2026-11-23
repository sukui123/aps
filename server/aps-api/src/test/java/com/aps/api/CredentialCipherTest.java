package com.aps.api;

import com.aps.infra.integration.CredentialCipher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 集成凭据 AES-GCM 加解密与掩码（PF-04 验收 #13，D3）。 */
class CredentialCipherTest {

    private static final String SECRET = "unit-test-integration-secret-at-least-32-chars";

    @Test
    void roundTripAndMask() {
        CredentialCipher cipher = new CredentialCipher(SECRET);
        String cipherText = cipher.encrypt("AK=abc;SK=xyz");
        assertNotNull(cipherText);
        assertTrue(cipherText.startsWith("v1:"));
        assertFalse(cipherText.contains("abc"));
        assertEquals("AK=abc;SK=xyz", cipher.decrypt(cipherText));
        assertEquals("****", CredentialCipher.mask("anything"));
        assertNull(CredentialCipher.mask(null));
    }

    @Test
    void failsWithoutSecretOrWithWrongSecret() {
        assertFalse(new CredentialCipher("").configured());
        assertThrows(IllegalStateException.class, () -> new CredentialCipher("").encrypt("x"));

        String cipherText = new CredentialCipher(SECRET).encrypt("secret-value");
        assertThrows(IllegalStateException.class,
                () -> new CredentialCipher("another-unit-test-secret-at-least-32-chars")
                        .decrypt(cipherText));
    }
}
