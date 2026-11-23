package com.aps.api.controller;

import com.aps.api.dto.ChangePasswordRequest;
import com.aps.api.dto.CreateUserRequest;
import com.aps.api.dto.StatusRequest;
import com.aps.api.security.SecuritySupport;
import com.aps.common.result.PageResult;
import com.aps.common.result.Result;
import com.aps.domain.model.SysUser;
import com.aps.domain.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理（PF-01）：功能权限 user:manage。 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('user:manage')")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public Result<PageResult<SysUser>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(authService.listUsers(SecuritySupport.tenantId(), page, Math.min(pageSize, 200)));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody CreateUserRequest request) {
        Long id = authService.createUser(SecuritySupport.tenantId(), request.username(), request.password(),
                request.displayName(), request.roles(), SecuritySupport.username());
        return Result.ok(id);
    }

    @PutMapping("/{id}/password")
    public Result<Void> changePassword(@PathVariable Long id, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecuritySupport.tenantId(), id, request.password(), SecuritySupport.username());
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        authService.changeStatus(SecuritySupport.tenantId(), id, request.status(), SecuritySupport.username());
        return Result.ok();
    }
}
