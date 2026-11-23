package com.aps.api.config;

import com.aps.api.security.UserPrincipal;
import com.aps.domain.service.AuthService;
import com.aps.domain.service.PermissionService;
import com.aps.infra.mapper.TokenBlacklistMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Bearer JWT → SecurityContext（PF-01/02）：验签、黑名单、权限码装配。 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistMapper blacklistMapper;
    private final PermissionService permissionService;
    private final AuthService authService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, TokenBlacklistMapper blacklistMapper,
                                   PermissionService permissionService, AuthService authService) {
        this.tokenProvider = tokenProvider;
        this.blacklistMapper = blacklistMapper;
        this.permissionService = permissionService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            JwtClaims claims = tokenProvider.parse(header.substring(7));
            if (claims.jti() != null && blacklistMapper.countActive(claims.jti()) > 0) {
                throw new JwtException("token has been revoked");
            }
            Long userId = Long.valueOf(claims.subject());
            // B3：验签通过也不代表会话有效。实时核对账号状态与令牌版本，
            // 禁用/改密后旧 token 立即被拒；不再等 1h 过期或 10min 缓存自然失效。
            AuthService.UserSecurityState state = authService.securityState(claims.tenantId(), userId);
            long tokenVersion = claims.tokenVersion() == null ? 0L : claims.tokenVersion();
            if (!state.isActive() || state.tokenVersion() != tokenVersion) {
                throw new JwtException("account disabled or credentials changed");
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserPrincipal principal = new UserPrincipal(
                        userId, claims.tenantId(), claims.username(), claims.username(), claims.jti(),
                        claims.roles(), claims.factoryIds(),
                        permissionService.permissionCodes(claims.tenantId(), userId));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            }
        } catch (JwtException | IllegalArgumentException e) {
            // 无认证主体：受保护接口由 EntryPoint 统一 401，白名单接口仍可匿名访问
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
