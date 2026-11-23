package com.aps.api.dto;

import com.aps.domain.service.AuthService.UserProfile;

/** 登录响应（PF-01）：token + 身份/权限事实。 */
public record LoginResponse(String token, String tokenType, long expiresIn, UserProfile user) {
}
