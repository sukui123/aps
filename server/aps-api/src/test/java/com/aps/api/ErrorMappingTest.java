package com.aps.api;

import com.aps.common.error.CommonErrorCode;
import com.aps.common.error.GlobalExceptionHandler;
import com.aps.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 错误码→HTTP 状态映射（D2：10002→401，10003→403，10004→409）。 */
class ErrorMappingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsAuthAndConflictCodes() {
        assertEquals(HttpStatus.UNAUTHORIZED.value(), status(CommonErrorCode.UNAUTHORIZED));
        assertEquals(HttpStatus.FORBIDDEN.value(), status(CommonErrorCode.FORBIDDEN));
        assertEquals(HttpStatus.CONFLICT.value(), status(CommonErrorCode.IDEMPOTENCY_CONFLICT));
        assertEquals(HttpStatus.BAD_REQUEST.value(), status(CommonErrorCode.PARAM_ERROR));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), status(CommonErrorCode.INTERNAL_ERROR));
    }

    private int status(CommonErrorCode code) {
        return handler.handleBusiness(new BusinessException(code)).getStatusCode().value();
    }
}
