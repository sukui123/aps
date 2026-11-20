package com.aps.common.error;

import com.aps.common.constant.Constants;
import com.aps.common.exception.BusinessException;
import com.aps.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
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

    /** 1xxxx/4xxxx → 4xx，其余（9xxxx）→ 5xx。 */
    private HttpStatus httpStatus(int code) {
        int group = code / 10000;
        if (group == 1 || group == 4) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
