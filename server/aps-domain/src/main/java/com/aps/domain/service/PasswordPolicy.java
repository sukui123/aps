package com.aps.domain.service;

import com.aps.common.error.CommonErrorCode;
import com.aps.common.exception.BusinessException;

/** 应用级密码策略（PF-01，B3 最小口径：长度≥8 + 字母 + 数字；锁定/有效期延后）。 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    public static void validate(String raw) {
        if (raw == null || raw.length() < MIN_LENGTH || raw.length() > MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "密码长度必须在 " + MIN_LENGTH + "-" + MAX_LENGTH + " 之间");
        }
        boolean letter = false;
        boolean digit = false;
        for (char c : raw.toCharArray()) {
            letter |= Character.isLetter(c);
            digit |= Character.isDigit(c);
        }
        if (!letter || !digit) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "密码必须同时包含字母和数字");
        }
    }
}
