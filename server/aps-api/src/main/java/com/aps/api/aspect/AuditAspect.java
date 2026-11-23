package com.aps.api.aspect;

import com.aps.common.annotation.Audited;
import com.aps.common.constant.Constants;
import com.aps.common.context.TenantContext;
import com.aps.domain.model.AuditLog;
import com.aps.domain.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 关键操作留痕（PF-03）：@Audited 方法成功同事务写审计，失败用独立事务写失败留痕。 */
@Aspect
@Component
@Order(300)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final int MAX_JSON = 4000;
    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AuditAspect(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = null;
        Throwable failure = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            try {
                AuditLog entry = build(joinPoint, audited, result, failure);
                if (failure == null) {
                    auditService.record(entry);
                } else {
                    auditService.recordStandalone(entry);
                }
            } catch (Exception e) {
                // 审计失败不能吞掉业务异常，但必须留下可排查日志
                log.error("audit write failed, action={}, traceId={}", audited.action(),
                        MDC.get(Constants.TRACE_ID), e);
            }
        }
    }

    private AuditLog build(ProceedingJoinPoint joinPoint, Audited audited, Object result, Throwable failure) {
        AuditLog entry = new AuditLog();
        entry.setTenantId(TenantContext.tenantId());
        entry.setActor(actor());
        entry.setAction(audited.action());
        entry.setTargetType(audited.targetType());
        entry.setTargetId(targetId(joinPoint, audited, result));
        entry.setBeforeJson(truncate(serialize(safeArgs(joinPoint))));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("outcome", failure == null ? "SUCCESS" : "FAILED");
        if (failure != null) {
            after.put("error", String.valueOf(failure.getMessage()));
        } else if (result != null) {
            after.put("result", result);
        }
        entry.setAfterJson(truncate(serialize(after)));
        entry.setTraceId(MDC.get(Constants.TRACE_ID));
        return entry;
    }

    private String actor() {
        if (TenantContext.username() != null) {
            return TenantContext.username();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    /** 口令/凭据类参数绝不进入 before_json。 */
    private Map<String, Object> safeArgs(ProceedingJoinPoint joinPoint) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return safe;
        }
        Method method = signature.getMethod();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            String name = names == null || i >= names.length ? "arg" + i : names[i];
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("password") || lower.contains("credential") || lower.contains("secret")) {
                safe.put(name, "****");
                continue;
            }
            safe.put(name, args[i]);
        }
        return safe;
    }

    /**
     * 解析被变更的业务实体 ID（PF-03 可追责）。优先使用 {@link Audited#target()} 的显式 SpEL；
     * 未声明时才走改进后的启发式，并显式跳过 tenantId，避免把租户 ID 当作 targetId。
     */
    private String targetId(ProceedingJoinPoint joinPoint, Audited audited, Object result) {
        MethodSignature signature = joinPoint.getSignature() instanceof MethodSignature ms ? ms : null;
        String[] names = signature == null ? null : signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (!audited.target().isBlank()) {
            try {
                Expression expression = EXPRESSION_CACHE.computeIfAbsent(audited.target(), SPEL_PARSER::parseExpression);
                StandardEvaluationContext ctx = new StandardEvaluationContext();
                for (int i = 0; i < args.length; i++) {
                    String name = names == null || i >= names.length ? "arg" + i : names[i];
                    ctx.setVariable(name, args[i]);
                }
                ctx.setVariable("result", result);
                Object value = expression.getValue(ctx);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (Exception e) {
                log.warn("audit target expression failed, action={}, expr={}", audited.action(), audited.target(), e);
            }
        }

        // 兜底：按参数名显式跳过 tenantId/凭据，优先取实体 getId()，其次取明确命名的 id 参数。
        for (int i = 0; i < args.length; i++) {
            String name = names == null || i >= names.length ? "arg" + i : names[i];
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("tenant") || lower.contains("password")
                    || lower.contains("credential") || lower.contains("secret")) {
                continue;
            }
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            try {
                Method getId = arg.getClass().getMethod("getId");
                Object value = getId.invoke(arg);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (ReflectiveOperationException ignored) {
                // 非实体参数，继续找
            }
            if ((lower.endsWith("id") || lower.endsWith("key")) && arg instanceof Number number) {
                return String.valueOf(number);
            }
        }
        return null;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"serializationError\":true}";
        }
    }

    private String truncate(String json) {
        return json.length() > MAX_JSON ? json.substring(0, MAX_JSON) : json;
    }
}
