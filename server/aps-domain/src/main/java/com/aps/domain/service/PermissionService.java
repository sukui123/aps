package com.aps.domain.service;

import com.aps.domain.model.SysPermission;
import com.aps.domain.model.SysRole;

import java.util.List;

/** 功能权限与数据范围端口（PF-02）。实现层按 userId 用 Caffeine 缓存权限码。 */
public interface PermissionService {

    List<String> permissionCodes(Long tenantId, Long userId);

    List<String> rolesOf(Long tenantId, Long userId);

    /**
     * 数据范围（工厂/车间 ID）。B1 未消项前来源是运行时配置/种子，属于过渡实现；
     * 一旦产品确认工厂维度，改为权威映射表，接口签名不变。
     */
    List<Long> factoryIdsOf(Long tenantId, Long userId);

    List<SysRole> listRoles(Long tenantId);

    Long createRole(Long tenantId, String code, String name, List<String> permissionCodes, String actor);

    void updateRole(Long tenantId, Long roleId, String name, List<String> permissionCodes, String actor);

    List<SysPermission> listPermissions(Long tenantId);
}
