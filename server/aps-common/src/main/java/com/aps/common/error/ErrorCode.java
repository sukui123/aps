package com.aps.common.error;

/** 业务错误码契约（分段见技术方案 §5.4）。 */
public interface ErrorCode {

    int getCode();

    String getMessage();
}
