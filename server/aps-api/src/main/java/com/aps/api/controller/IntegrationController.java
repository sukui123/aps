package com.aps.api.controller;

import com.aps.api.dto.IntegrationConfigRequest;
import com.aps.api.security.SecuritySupport;
import com.aps.common.result.PageResult;
import com.aps.common.result.Result;
import com.aps.domain.model.IntegrationConfig;
import com.aps.domain.model.IntegrationTask;
import com.aps.domain.service.IntegrationService;
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

import java.time.Instant;
import java.util.List;

/** 集成配置与任务监控（PF-04）。 */
@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('integration:manage')")
    public Result<List<IntegrationConfig>> listConfigs() {
        return Result.ok(integrationService.listConfigs(SecuritySupport.tenantId()));
    }

    @PostMapping("/configs")
    @PreAuthorize("hasAuthority('integration:manage')")
    public Result<Long> createConfig(@Valid @RequestBody IntegrationConfigRequest request) {
        return Result.ok(integrationService.createConfig(SecuritySupport.tenantId(), toConfig(request),
                request.credentials(), SecuritySupport.username()));
    }

    @PutMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('integration:manage')")
    public Result<Void> updateConfig(@PathVariable Long id, @Valid @RequestBody IntegrationConfigRequest request) {
        IntegrationConfig config = toConfig(request);
        config.setId(id);
        integrationService.updateConfig(SecuritySupport.tenantId(), config,
                request.credentials(), SecuritySupport.username());
        return Result.ok();
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('integration:read')")
    public Result<PageResult<IntegrationTask>> listTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "false") boolean failedOnly,
            @RequestParam(required = false) Integer minRetryCount,
            @RequestParam(required = false) Instant nextRetryBefore,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(integrationService.listTasks(SecuritySupport.tenantId(),
                new IntegrationService.TaskQuery(status, bizType, failedOnly, minRetryCount,
                        nextRetryBefore, page, Math.min(pageSize, 200))));
    }

    @PostMapping("/tasks/{id}/retry")
    @PreAuthorize("hasAuthority('integration:manage')")
    public Result<Void> retry(@PathVariable Long id) {
        integrationService.retryTask(SecuritySupport.tenantId(), id, SecuritySupport.username());
        return Result.ok();
    }

    private IntegrationConfig toConfig(IntegrationConfigRequest request) {
        IntegrationConfig config = new IntegrationConfig();
        config.setCode(request.code());
        config.setName(request.name());
        config.setType(request.type());
        config.setEndpoint(request.endpoint());
        config.setSignatureType(request.signatureType() == null ? "NONE" : request.signatureType());
        config.setIpAllowlist(request.ipAllowlist() == null ? "[]" : request.ipAllowlist());
        config.setCronExpr(request.cronExpr());
        config.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        return config;
    }
}
