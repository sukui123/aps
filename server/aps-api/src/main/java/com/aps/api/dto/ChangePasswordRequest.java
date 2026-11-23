package com.aps.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 改密请求（PF-01）。 */
public record ChangePasswordRequest(@NotBlank String password) {
}
