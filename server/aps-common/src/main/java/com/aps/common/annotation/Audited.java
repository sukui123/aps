package com.aps.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 关键操作审计埋点（PF-03）。AuditAspect 在同一事务内把 actor/action/target/before/after/
 * traceId 写入 audit_log。E4 甘特调整需标注 {@code action = AuditActions.PLAN_TASK_ADJUST}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 审计动作码，见 AuditActions。 */
    String action();

    /** 目标对象类型，例如 SysUser / IntegrationConfig / PlanTask。 */
    String targetType() default "";

    /**
     * 目标业务实体 ID 的 SpEL 表达式，禁止再用「第一个 Number 参数」猜测
     * （领域方法普遍以 {@code Long tenantId} 为首参，猜测会把租户 ID 写成 targetId）。
     * 可用变量：所有方法参数名 + {@code result}。示例：
     * <ul>
     *   <li>{@code "#result"} —— 返回新建实体 ID 的 create 操作</li>
     *   <li>{@code "#userId"} / {@code "#taskId"} —— 以 ID 为入参的更新操作</li>
     *   <li>{@code "#config.id"} —— 以实体为入参</li>
     *   <li>{@code "#result?.taskId()"} —— 返回记录/包装对象</li>
     * </ul>
     * 留空时 AuditAspect 退化为改进后的启发式（显式跳过 tenantId 参数）。
     */
    String target() default "";
}
