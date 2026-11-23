package com.aps.api.controller;

import com.aps.api.security.SecuritySupport;
import com.aps.common.result.PageResult;
import com.aps.common.result.Result;
import com.aps.domain.model.AuditLog;
import com.aps.domain.service.AuditService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** 审计查询/导出（PF-03）：导出受与查询相同的数据作用域过滤。 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public Result<PageResult<AuditLog>> query(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(auditService.query(buildQuery(actor, action, targetType, targetId, from, to,
                page, Math.min(pageSize, 200))));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('audit:export')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        String csv = auditService.exportCsv(buildQuery(actor, action, targetType, targetId, from, to, 1, 200));
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-log.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    private AuditService.Query buildQuery(String actor, String action, String targetType, String targetId,
                                     Instant from, Instant to, int page, int pageSize) {
        return new AuditService.Query(SecuritySupport.tenantId(), actor, action, targetType,
                targetId, from, to, page, pageSize);
    }
}
