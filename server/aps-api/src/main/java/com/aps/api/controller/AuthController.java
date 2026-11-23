package com.aps.api.controller;

import com.aps.api.config.JwtClaims;
import com.aps.api.config.JwtProperties;
import com.aps.api.config.JwtTokenProvider;
import com.aps.api.dto.LoginRequest;
import com.aps.api.dto.LoginResponse;
import com.aps.api.security.SecuritySupport;
import com.aps.api.security.UserPrincipal;
import com.aps.common.result.Result;
import com.aps.domain.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录/登出/当前用户（PF-01）。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, JwtTokenProvider tokenProvider, JwtProperties jwtProperties) {
        this.authService = authService;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResult result = authService.login(
                request.username(), request.password(), jwtProperties.getExpirationMs());
        AuthService.UserProfile user = result.user();
        String token = tokenProvider.createToken(new JwtClaims(
                String.valueOf(user.userId()), user.tenantId(), user.username(),
                user.roles(), user.factoryIds(), result.jti(), result.expiresAt(), result.tokenVersion()));
        return Result.ok(new LoginResponse(token, "Bearer", jwtProperties.getExpirationMs() / 1000, user));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization) {
        JwtClaims claims = tokenProvider.parse(authorization.substring(7));
        authService.logout(claims.jti(), claims.expiresAt());
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<AuthService.UserProfile> me() {
        UserPrincipal principal = SecuritySupport.principal();
        return Result.ok(authService.profile(principal.getTenantId(), principal.getUserId()));
    }

}
