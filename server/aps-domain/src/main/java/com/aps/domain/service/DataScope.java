package com.aps.domain.service;

import com.aps.common.context.TenantContext;

import java.util.List;

/**
 * 数据权限作用域（PF-02）。tenantId 用于 RLS/租户隔离；factoryIds 供 ABAC 谓词使用。
 * B1 消项前 factoryIds 不写入 RLS 策略，仅在应用层过滤（接口已备）。
 */
public final class DataScope {

    private final Long tenantId;
    private final List<Long> factoryIds;

    public DataScope(Long tenantId, List<Long> factoryIds) {
        this.tenantId = tenantId;
        this.factoryIds = factoryIds == null ? List.of() : List.copyOf(factoryIds);
    }

    public static DataScope current() {
        if (TenantContext.isEmpty()) {
            return new DataScope(null, List.of());
        }
        return new DataScope(TenantContext.tenantId(), TenantContext.factoryIds());
    }

    public Long getTenantId() {
        return tenantId;
    }

    public List<Long> getFactoryIds() {
        return factoryIds;
    }

    public boolean hasFactoryRestriction() {
        return !factoryIds.isEmpty();
    }

    /** 写入 PG 会话变量 app.factory_ids 的值；空作用域返回空串。 */
    public String factorySettingValue() {
        if (factoryIds.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Long id : factoryIds) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }
}
