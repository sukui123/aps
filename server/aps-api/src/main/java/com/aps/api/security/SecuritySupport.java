package com.aps.api.security;

import com.aps.common.error.CommonErrorCode;
import com.aps.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 从 SecurityContext 取当前主体；Web 层用它注入租户/操作人，避免信任客户端传参。 */
public final class SecuritySupport {

    private SecuritySupport() {
    }

    public static UserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return p;
    }

    public static Long tenantId() {
        Long tenantId = principal().getTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "缺少租户上下文");
        }
        return tenantId;
    }

    public static String username() {
        return principal().getUsername();
    }
}
