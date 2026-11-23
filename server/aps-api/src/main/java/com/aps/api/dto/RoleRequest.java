package com.aps.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** 角色创建/更新请求（PF-02）。 */
public record RoleRequest(@NotBlank String code, String name, List<String> permissions) {
}
