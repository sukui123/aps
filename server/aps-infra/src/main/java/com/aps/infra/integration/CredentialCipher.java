package com.aps.infra.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 集成凭据 AES-GCM 加解密（D3）。密钥只来自环境变量 APS_INTEGRATION_SECRET，
 * 未配置时对加解密 fail-fast；密文格式为 v1:&lt;base64(iv)&gt;:&lt;base64(ciphertext)&gt;。
 */
@Component
public class CredentialCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public CredentialCipher(@Value("${aps.integration.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            this.key = null;
        } else {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(secret.getBytes(StandardCharsets.UTF_8));
                this.key = new SecretKeySpec(digest, "AES");
            } catch (Exception e) {
                throw new IllegalStateException("cannot derive integration credential key", e);
            }
        }
    }

    public boolean configured() {
        return key != null;
    }

    public String encrypt(String plain) {
        requireKey();
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ct.length);
            buffer.put(iv).put(ct);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("credential encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        requireKey();
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            if (!stored.startsWith(PREFIX)) {
                throw new IllegalArgumentException("unsupported credential ciphertext version");
            }
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (raw.length <= IV_LENGTH) {
                throw new IllegalArgumentException("invalid credential ciphertext");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            System.arraycopy(raw, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("credential decryption failed", e);
        }
    }

    /** 接口返回与日志统一的掩码，绝不明文回显。 */
    public static String mask(String value) {
        return (value == null || value.isEmpty()) ? null : "****";
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("APS_INTEGRATION_SECRET not configured; "
                    + "integration credential operations are disabled");
        }
    }
}
