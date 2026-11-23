package com.aps.api.controller;

import com.aps.api.dto.RoleRequest;
import com.aps.api.security.SecuritySupport;
import com.aps.common.result.Result;
import com.aps.domain.model.SysRole;
import com.aps.domain.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 角色管理（PF-02）：功能权限 role:manage。 */
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasAuthority('role:manage')")
public class RoleController {

    private final PermissionService permissionService;

    public RoleController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public Result<List<SysRole>> list() {
        return Result.ok(permissionService.listRoles(SecuritySupport.tenantId()));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody RoleRequest request) {
        return Result.ok(permissionService.createRole(SecuritySupport.tenantId(), request.code(),
                request.name(), request.permissions(), SecuritySupport.username()));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        permissionService.updateRole(SecuritySupport.tenantId(), id, request.name(),
                request.permissions(), SecuritySupport.username());
        return Result.ok();
    }
}
