package com.aps.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 登录请求（PF-01）。 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
