package com.aps.infra.service;

import com.aps.common.annotation.Audited;
import com.aps.common.constant.AuditActions;
import com.aps.common.constant.PermissionCodes;
import com.aps.common.error.CommonErrorCode;
import com.aps.common.exception.BusinessException;
import com.aps.domain.model.SysPermission;
import com.aps.domain.model.SysRole;
import com.aps.domain.model.SysRolePermission;
import com.aps.domain.model.SysUserRole;
import com.aps.domain.service.PermissionService;
import com.aps.infra.mapper.SysPermissionMapper;
import com.aps.infra.mapper.SysRoleMapper;
import com.aps.infra.mapper.SysRolePermissionMapper;
import com.aps.infra.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** RBAC + 过渡数据范围实现（PF-02）。权限码按 userId 走 Caffeine 短 TTL 缓存。 */
@Service
public class PermissionServiceImpl implements PermissionService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final Environment environment;

    public PermissionServiceImpl(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper,
                                 SysUserRoleMapper userRoleMapper,
                                 SysRolePermissionMapper rolePermissionMapper,
                                 Environment environment) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.environment = environment;
    }

    @Override
    @Cacheable(cacheNames = "userPermissions", key = "#tenantId + ':' + #userId")
    public List<String> permissionCodes(Long tenantId, Long userId) {
        List<String> roles = rolesOf(tenantId, userId);
        if (roles.contains("ADMIN")) {
            return PermissionCodes.ALL;
        }
        List<String> codes = permissionCodesOfRoles(tenantId, roles);
        if (codes.isEmpty()) {
            return List.of();
        }
        List<SysPermission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getTenantId, tenantId)
                        .in(SysPermission::getCode, codes)
                        .eq(SysPermission::getIsDeleted, false));
        return permissions.stream().map(SysPermission::getCode).distinct().toList();
    }

    @Override
    public List<String> rolesOf(Long tenantId, Long userId) {
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, tenantId)
                .eq(SysUserRole::getUserId, userId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = links.stream().map(SysUserRole::getRoleId).toList();
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds)
                        .eq(SysRole::getIsDeleted, false))
                .stream().map(SysRole::getCode).distinct().toList();
    }

    @Override
    public List<Long> factoryIdsOf(Long tenantId, Long userId) {
        // B1 未消项：过渡来源是 aps.data-scope.<roleCode>.factory-ids 配置（仅开发引导用）。
        Set<Long> result = new LinkedHashSet<>();
        for (String role : rolesOf(tenantId, userId)) {
            String configured = environment.getProperty("aps.data-scope." + role + ".factory-ids", "");
            for (String part : configured.split(",")) {
                if (!part.isBlank()) {
                    result.add(Long.parseLong(part.trim()));
                }
            }
        }
        return new ArrayList<>(result);
    }

    @Override
    public List<SysRole> listRoles(Long tenantId) {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)
                .eq(SysRole::getIsDeleted, false)
                .orderByAsc(SysRole::getId));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "userPermissions", allEntries = true)
    @Audited(action = AuditActions.ROLE_CREATE, targetType = "SysRole", target = "#result")
    public Long createRole(Long tenantId, String code, String name, List<String> permissionCodes, String actor) {
        Long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId).eq(SysRole::getCode, code)
                .eq(SysRole::getIsDeleted, false));
        if (exists != null && exists > 0) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setTenantId(tenantId);
        role.setCode(code);
        role.setName(name);
        roleMapper.insert(role);
        bindPermissions(tenantId, role, permissionCodes);
        return role.getId();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "userPermissions", allEntries = true)
    @Audited(action = AuditActions.ROLE_UPDATE, targetType = "SysRole", target = "#roleId")
    public void updateRole(Long tenantId, Long roleId, String name, List<String> permissionCodes, String actor) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getId, roleId).eq(SysRole::getTenantId, tenantId)
                .eq(SysRole::getIsDeleted, false));
        if (role == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        }
        role.setName(name);
        roleMapper.updateById(role);
        bindPermissions(tenantId, role, permissionCodes);
    }

    @Override
    public List<SysPermission> listPermissions(Long tenantId) {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getTenantId, tenantId)
                .eq(SysPermission::getIsDeleted, false)
                .orderByAsc(SysPermission::getCode));
    }

    private List<String> permissionCodesOfRoles(Long tenantId, List<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return List.of();
        }
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)
                .in(SysRole::getCode, roleCodes)
                .eq(SysRole::getIsDeleted, false));
        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getTenantId, tenantId)
                        .in(SysRolePermission::getRoleId, roleIds))
                .stream().map(SysRolePermission::getPermissionId).distinct().collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getTenantId, tenantId)
                        .in(SysPermission::getId, permissionIds)
                        .eq(SysPermission::getIsDeleted, false))
                .stream().map(SysPermission::getCode).distinct().toList();
    }

    private void bindPermissions(Long tenantId, SysRole role, List<String> codes) {
        if (codes == null) {
            return;
        }
        List<SysPermission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getTenantId, tenantId)
                        .in(SysPermission::getCode, codes)
                        .eq(SysPermission::getIsDeleted, false));
        if (permissions.size() != codes.stream().distinct().count()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "权限码不存在");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getTenantId, tenantId)
                .eq(SysRolePermission::getRoleId, role.getId()));
        for (SysPermission permission : permissions) {
            SysRolePermission link = new SysRolePermission();
            link.setTenantId(tenantId);
            link.setRoleId(role.getId());
            link.setPermissionId(permission.getId());
            rolePermissionMapper.insert(link);
        }
    }
}
