package com.aps.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 数据源配置请求（PF-04）；凭据只写不读，回显恒为 ****。 */
public record IntegrationConfigRequest(@NotBlank String code,
                                       @NotBlank String name,
                                       @NotBlank String type,
                                       String endpoint,
                                       String credentials,
                                       String signatureType,
                                       String ipAllowlist,
                                       String cronExpr,
                                       Boolean enabled) {
}
