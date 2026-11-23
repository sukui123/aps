package com.aps.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 创建用户请求（PF-01）。 */
public record CreateUserRequest(@NotBlank String username,
                                @NotBlank @Size(min = 8, max = 128) String password,
                                String displayName,
                                List<String> roles) {
}
