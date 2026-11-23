package com.aps.common.constant;

/** 审计动作枚举（PF-03）。E1/E2/E5 的 action 由各 Epic 增补，不在本底座硬编码。 */
public final class AuditActions {

    private AuditActions() {
    }

    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_PASSWORD_CHANGE = "USER_PASSWORD_CHANGE";
    public static final String USER_STATUS_CHANGE = "USER_STATUS_CHANGE";
    public static final String ROLE_CREATE = "ROLE_CREATE";
    public static final String ROLE_UPDATE = "ROLE_UPDATE";
    public static final String AUDIT_EXPORT = "AUDIT_EXPORT";
    public static final String INTEGRATION_CONFIG_CREATE = "INTEGRATION_CONFIG_CREATE";
    public static final String INTEGRATION_CONFIG_UPDATE = "INTEGRATION_CONFIG_UPDATE";
    public static final String INTEGRATION_TASK_RETRY = "INTEGRATION_TASK_RETRY";
    public static final String ORDER_SYNC_TRIGGER = "ORDER_SYNC_TRIGGER";

    /** 跨 Epic 契约：E4 的 PATCH /api/v1/plans/{id}/tasks 落地时必须带此 action。 */
    public static final String PLAN_TASK_ADJUST = "PLAN_TASK_ADJUST";
}
