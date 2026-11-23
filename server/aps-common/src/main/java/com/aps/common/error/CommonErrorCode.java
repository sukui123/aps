package com.aps.common.error;

/** 通用错误码：0 成功、1xxxx 参数/鉴权、9xxxx 系统内部。 */
public enum CommonErrorCode implements ErrorCode {

    SUCCESS(0, "ok"),
    PARAM_ERROR(10001, "参数错误"),
    UNAUTHORIZED(10002, "未登录或认证失败"),
    FORBIDDEN(10003, "无权限"),
    IDEMPOTENCY_CONFLICT(10004, "幂等冲突"),
    NOT_FOUND(10005, "资源不存在"),
    INTEGRATION_ERROR(50001, "集成处理失败"),
    ADAPTER_UNAVAILABLE(50002, "数据源适配器不可用"),
    INTERNAL_ERROR(90000, "系统内部错误");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
