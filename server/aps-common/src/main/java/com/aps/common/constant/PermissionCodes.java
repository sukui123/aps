package com.aps.common.constant;

import java.util.List;

/** 功能权限码字典（PF-02）。其余业务模块的权限码由对应 Epic 增补并回写种子。 */
public final class PermissionCodes {

    private PermissionCodes() {
    }

    public static final String USER_MANAGE = "user:manage";
    public static final String ROLE_MANAGE = "role:manage";
    public static final String AUDIT_READ = "audit:read";
    public static final String AUDIT_EXPORT = "audit:export";
    public static final String INTEGRATION_MANAGE = "integration:manage";
    public static final String INTEGRATION_READ = "integration:read";
    public static final String ORDER_SYNC = "order:sync";

    /** 平台底座自身定义的全部权限码，供 ADMIN 角色兜底与种子校验使用。 */
    public static final List<String> ALL = List.of(
            USER_MANAGE, ROLE_MANAGE, AUDIT_READ, AUDIT_EXPORT,
            INTEGRATION_MANAGE, INTEGRATION_READ, ORDER_SYNC);
}
