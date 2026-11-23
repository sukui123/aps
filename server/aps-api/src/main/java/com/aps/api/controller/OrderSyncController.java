package com.aps.api.controller;

import com.aps.api.dto.SyncRequest;
import com.aps.api.security.SecuritySupport;
import com.aps.common.constant.Constants;
import com.aps.common.result.Result;
import com.aps.domain.service.IntegrationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 触发 ERP/MES 同步（PF-04/US-PF04-2），落 integration_task 并双幂等。 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderSyncController {

    private final IntegrationService integrationService;
    private final ObjectMapper objectMapper;

    public OrderSyncController(IntegrationService integrationService, ObjectMapper objectMapper) {
        this.integrationService = integrationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('order:sync')")
    public Result<IntegrationService.SyncResult> sync(
            @RequestBody(required = false) SyncRequest request,
            @RequestHeader(value = Constants.IDEMPOTENCY_KEY_HEADER, required = false) String headerKey)
            throws JsonProcessingException {
        SyncRequest body = request == null ? new SyncRequest(null, null, null, null) : request;
        String bizType = body.bizType() == null || body.bizType().isBlank() ? "ORDER_SYNC" : body.bizType();
        String bizKey = body.bizKey() == null || body.bizKey().isBlank()
                ? (headerKey == null ? String.valueOf(System.currentTimeMillis()) : headerKey)
                : body.bizKey();
        String idempotencyKey = body.idempotencyKey() == null || body.idempotencyKey().isBlank()
                ? headerKey : body.idempotencyKey();
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("bizType", bizType);
        payload.put("bizKey", bizKey);
        if (body.payload() != null) {
            payload.putAll(body.payload());
        }
        return Result.ok(integrationService.submitTask(SecuritySupport.tenantId(), bizType, bizKey,
                idempotencyKey, objectMapper.writeValueAsString(payload), SecuritySupport.username()));
    }
}
