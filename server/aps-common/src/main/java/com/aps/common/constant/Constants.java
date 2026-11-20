package com.aps.common.constant;

/** 全局常量（对齐技术方案 §5.1 API 约定）。 */
public final class Constants {

    private Constants() {
    }

    /** 每请求生成的链路追踪 id，同时写入 MDC 与响应头。 */
    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 写操作幂等键请求头。 */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /** 行级数据权限会话变量（PG RLS 使用，见技术方案 §3.5）。 */
    public static final String RLS_TENANT_SETTING = "app.tenant_id";
    public static final String RLS_FACTORY_SETTING = "app.factory_ids";
}
