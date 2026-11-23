package com.aps.common.error;

import com.aps.common.constant.Constants;
import com.aps.common.exception.BusinessException;
import com.aps.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/** 全局异常处理：把异常映射为统一错误体 + 合适 HTTP 状态（技术方案 §5.1/§5.4）。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(httpStatus(e.getCode()))
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(this::format)
                .collect(Collectors.toList());
        String message = details.isEmpty() ? CommonErrorCode.PARAM_ERROR.getMessage() : String.join("; ", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(CommonErrorCode.PARAM_ERROR, message));
    }

    /** 数据唯一约束冲突（如并发幂等）按幂等冲突 409 返回，禁止退化成 90000/500。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.fail(CommonErrorCode.IDEMPOTENCY_CONFLICT, "重复提交冲突，请重试"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e, HttpServletRequest request) {
        // 不打印敏感配置值；只记录 traceId 与路径，详细堆栈交由日志框架
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("unhandled exception, traceId={}, path={}", MDC.get(Constants.TRACE_ID),
                        request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(CommonErrorCode.INTERNAL_ERROR));
    }

    private String format(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

    /**
     * 错误码到 HTTP 状态的映射（技术方案 §5.4，D2 修正）：
     * 10002=未认证→401，10003=无权限→403，10004=幂等冲突→409，
     * 10005=资源不存在→404，其余 1xxxx→400，5xxxx/9xxxx→500。
     */
    private HttpStatus httpStatus(int code) {
        return switch (code) {
            case 10002 -> HttpStatus.UNAUTHORIZED;
            case 10003 -> HttpStatus.FORBIDDEN;
            case 10004 -> HttpStatus.CONFLICT;
            case 10005 -> HttpStatus.NOT_FOUND;
            default -> (code / 10000 == 1) ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
