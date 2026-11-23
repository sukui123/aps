package com.aps.api;

import com.aps.api.config.JwtAuthenticationFilter;
import com.aps.api.config.JwtClaims;
import com.aps.api.config.JwtTokenProvider;
import com.aps.domain.service.AuthService;
import com.aps.domain.service.PermissionService;
import com.aps.infra.mapper.TokenBlacklistMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** B3 回归：验签通过但账号被禁用/令牌版本过期时，过滤器必须拒绝建立安全上下文。 */
class JwtAuthenticationFilterTest {

    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final TokenBlacklistMapper blacklistMapper = mock(TokenBlacklistMapper.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AuthService authService = mock(AuthService.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(tokenProvider, blacklistMapper, permissionService, authService);

    private JwtClaims claims(long version) {
        return new JwtClaims("42", 1L, "alice", List.of("ADMIN"), List.of(), "jti-1",
                Instant.now().plusSeconds(3600), version);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-signature");
        return request;
    }

    private void givenState(AuthService.UserSecurityState state) {
        when(tokenProvider.parse("valid-signature")).thenReturn(claims(0L));
        when(blacklistMapper.countActive(any())).thenReturn(0);
        when(authService.securityState(anyLong(), anyLong())).thenReturn(state);
        when(permissionService.permissionCodes(anyLong(), anyLong())).thenReturn(List.of("audit:read"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledAccountTokenIsRejected() throws Exception {
        givenState(new AuthService.UserSecurityState("DISABLED", 0L));

        filter.doFilter(request(), new MockHttpServletResponse(), mock(FilterChain.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void staleTokenVersionIsRejected() throws Exception {
        // 库中版本已因改密/禁用递增，旧 token 的 ver=0 必须被拒
        givenState(new AuthService.UserSecurityState("ACTIVE", 1L));

        filter.doFilter(request(), new MockHttpServletResponse(), mock(FilterChain.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void activeAccountWithMatchingVersionIsAccepted() throws Exception {
        givenState(new AuthService.UserSecurityState("ACTIVE", 0L));

        filter.doFilter(request(), new MockHttpServletResponse(), mock(FilterChain.class));

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
