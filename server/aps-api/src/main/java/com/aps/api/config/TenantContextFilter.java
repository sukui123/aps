package com.aps.api.config;

import com.aps.api.security.UserPrincipal;
import com.aps.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 把已认证主体的租户/数据范围写入 ThreadLocal，请求结束清理（PF-02）。 */
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
                TenantContext.set(p.getTenantId(), p.getFactoryIds(), p.getUserId(),
                        p.getUsername(), p.getJti());
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
