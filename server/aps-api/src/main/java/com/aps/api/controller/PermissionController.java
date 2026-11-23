package com.aps.api.controller;

import com.aps.api.security.SecuritySupport;
import com.aps.common.result.Result;
import com.aps.domain.model.SysPermission;
import com.aps.domain.service.PermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 权限码字典（PF-02）。 */
@RestController
@RequestMapping("/api/v1/permissions")
@PreAuthorize("hasAuthority('role:manage')")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public Result<List<SysPermission>> list() {
        return Result.ok(permissionService.listPermissions(SecuritySupport.tenantId()));
    }
}
