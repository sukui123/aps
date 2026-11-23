package com.aps.common.context;

import java.util.List;

/**
 * 租户/数据范围上下文（PF-02）。
 *
 * <p>Web 链路在 JWT 解析后写入，worker 链路从任务行恢复；由 TenantAwareAspect 在同一事务内
 * 转换为 {@code SET LOCAL app.tenant_id}。线程结束时必须 {@link #clear()}，避免线程池串租户。
 */
public final class TenantContext {

    private TenantContext() {
    }

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    /** 不可变的上下文快照。 */
    public static final class Context {
        private final Long tenantId;
        private final List<Long> factoryIds;
        private final List<Long> workshopIds;
        private final Long userId;
        private final String username;
        private final String jti;

        public Context(Long tenantId, List<Long> factoryIds, List<Long> workshopIds,
                       Long userId, String username, String jti) {
            this.tenantId = tenantId;
            this.factoryIds = factoryIds == null ? List.of() : List.copyOf(factoryIds);
            this.workshopIds = workshopIds == null ? List.of() : List.copyOf(workshopIds);
            this.userId = userId;
            this.username = username;
            this.jti = jti;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public List<Long> getFactoryIds() {
            return factoryIds;
        }

        public List<Long> getWorkshopIds() {
            return workshopIds;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getJti() {
            return jti;
        }
    }

    public static void set(Long tenantId, List<Long> factoryIds, Long userId, String username, String jti) {
        HOLDER.set(new Context(tenantId, factoryIds, null, userId, username, jti));
    }

    public static void set(Context context) {
        HOLDER.set(context);
    }

    public static Context get() {
        return HOLDER.get();
    }

    public static Long tenantId() {
        Context ctx = HOLDER.get();
        return ctx == null ? null : ctx.getTenantId();
    }

    public static Long userId() {
        Context ctx = HOLDER.get();
        return ctx == null ? null : ctx.getUserId();
    }

    public static String username() {
        Context ctx = HOLDER.get();
        return ctx == null ? null : ctx.getUsername();
    }

    public static List<Long> factoryIds() {
        Context ctx = HOLDER.get();
        return ctx == null ? List.of() : ctx.getFactoryIds();
    }

    public static boolean isEmpty() {
        return HOLDER.get() == null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
