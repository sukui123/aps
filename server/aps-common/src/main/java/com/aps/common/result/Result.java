package com.aps.common.result;

import com.aps.common.constant.Constants;
import com.aps.common.error.CommonErrorCode;
import com.aps.common.error.ErrorCode;
import org.slf4j.MDC;

import java.time.Instant;

/**
 * 统一返回体（技术方案 §5.1）：
 * { "code": 0, "message": "ok", "data": {...}, "traceId": "...", "timestamp": "..." }
 */
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private Instant timestamp;

    public Result() {
    }

    private Result(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.timestamp = Instant.now();
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, CommonErrorCode.SUCCESS.getMessage(), data, MDC.get(Constants.TRACE_ID));
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null, MDC.get(Constants.TRACE_ID));
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, MDC.get(Constants.TRACE_ID));
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
