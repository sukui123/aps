package com.aps.domain.service;

import com.aps.common.result.PageResult;
import com.aps.domain.model.SysUser;

import java.time.Instant;
import java.util.List;

/** 登录/会话/用户管理端口（PF-01）。令牌签发由 Web 层负责，本服务只产出身份事实。 */
public interface AuthService {

    /** 校验账号状态与口令；失败统一抛 UNAUTHORIZED，不区分账号是否存在。 */
    AuthResult login(String username, String rawPassword, long expirationMs);

    /** 登出：jti 入 token_blacklist（DB 唯一真相源）。 */
    void logout(String jti, Instant expiresAt);

    UserProfile profile(Long tenantId, Long userId);

    Long createUser(Long tenantId, String username, String rawPassword, String displayName,
                    List<String> roleCodes, String actor);

    void changePassword(Long tenantId, Long userId, String rawPassword, String actor);

    void changeStatus(Long tenantId, Long userId, String status, String actor);

    PageResult<SysUser> listUsers(Long tenantId, int page, int pageSize);

    /**
     * 鉴权链路实时查询的账号安全状态（B3）。用户不存在时返回 status=MISSING，
     * 由过滤器统一拒绝，不向调用方暴露账号是否存在。
     */
    UserSecurityState securityState(Long tenantId, Long userId);

    record UserSecurityState(String status, long tokenVersion) {
        public boolean isActive() {
            return "ACTIVE".equalsIgnoreCase(status);
        }
    }

    record AuthResult(UserProfile user, String jti, Instant expiresAt, long tokenVersion) {
    }

    record UserProfile(Long userId, Long tenantId, String username, String displayName,
                       List<String> roles, List<Long> factoryIds, List<String> permissions) {
    }
}
