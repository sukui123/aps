package com.aps.api;

import com.aps.common.exception.BusinessException;
import com.aps.domain.service.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 应用级密码策略（PF-01 验收 #3）。 */
class PasswordPolicyTest {

    @Test
    void acceptsStrongPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Admin@12345"));
    }

    @Test
    void rejectsWeakPassword() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("short1"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("onlyletters"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("12345678"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(null));
    }
}
