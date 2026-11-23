package com.aps.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 用户启停请求（PF-01）。 */
public record StatusRequest(@NotBlank String status) {
}
