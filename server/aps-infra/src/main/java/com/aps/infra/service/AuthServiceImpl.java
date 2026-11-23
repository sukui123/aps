package com.aps.infra.service;

import com.aps.common.annotation.Audited;
import com.aps.common.constant.AuditActions;
import com.aps.common.constant.Constants;
import com.aps.common.context.TenantContext;
import com.aps.common.error.CommonErrorCode;
import com.aps.common.exception.BusinessException;
import com.aps.common.result.PageResult;
import com.aps.domain.model.AuditLog;
import com.aps.domain.model.SysRole;
import com.aps.domain.model.SysUser;
import com.aps.domain.model.SysUserRole;
import com.aps.domain.model.TokenBlacklist;
import com.aps.domain.service.AuthService;
import com.aps.domain.service.AuditService;
import com.aps.domain.service.PasswordPolicy;
import com.aps.domain.service.PermissionService;
import com.aps.infra.mapper.SysRoleMapper;
import com.aps.infra.mapper.SysUserMapper;
import com.aps.infra.mapper.SysUserRoleMapper;
import com.aps.infra.mapper.TokenBlacklistMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 登录/会话/用户管理实现（PF-01）。 */
@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final TokenBlacklistMapper blacklistMapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public AuthServiceImpl(SysUserMapper userMapper, SysRoleMapper roleMapper,
                           SysUserRoleMapper userRoleMapper, TokenBlacklistMapper blacklistMapper,
                           PasswordEncoder passwordEncoder, PermissionService permissionService,
                           AuditService auditService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.blacklistMapper = blacklistMapper;
        this.passwordEncoder = passwordEncoder;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public AuthResult login(String username, String rawPassword, long expirationMs) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getIsDeleted, false)
                .last("limit 1"));
        // 统一失败文案：不区分账号不存在 / 密码错误 / 账号禁用
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())
                || rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        UserProfile profile = profile(user.getTenantId(), user.getId());
        Instant expiresAt = Instant.now().plusMillis(expirationMs);
        String jti = UUID.randomUUID().toString().replace("-", "");
        audit("LOGIN", user.getTenantId(), username, String.valueOf(user.getId()),
                "{\"result\":\"success\"}", null);
        return new AuthResult(profile, jti, expiresAt, user.getTokenVersion());
    }

    @Override
    @Transactional
    public void logout(String jti, Instant expiresAt) {
        blacklistMapper.addIgnore(jti, expiresAt);
        audit(AuditActions.LOGOUT, TenantContext.tenantId(), TenantContext.username(), jti,
                null, "{\"result\":\"success\"}");
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfile profile(Long tenantId, Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        List<String> roles = permissionService.rolesOf(tenantId, userId);
        List<String> permissions = permissionService.permissionCodes(tenantId, userId);
        return new UserProfile(userId, user.getTenantId(), user.getUsername(), user.getDisplayName(),
                roles, permissionService.factoryIdsOf(tenantId, userId), permissions);
    }

    @Override
    @Transactional
    @Audited(action = AuditActions.USER_CREATE, targetType = "SysUser", target = "#result")
    public Long createUser(Long tenantId, String username, String rawPassword, String displayName,
                           List<String> roleCodes, String actor) {
        PasswordPolicy.validate(rawPassword);
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getIsDeleted, false));
        if (exists != null && exists > 0) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "账号已存在");
        }
        SysUser user = new SysUser();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setStatus("ACTIVE");
        user.setCreatedBy(actor);
        user.setUpdatedBy(actor);
        userMapper.insert(user);
        if (roleCodes != null && !roleCodes.isEmpty()) {
            bindRoles(tenantId, user.getId(), roleCodes);
        }
        return user.getId();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "userPermissions", key = "#tenantId + ':' + #userId")
    @Audited(action = AuditActions.USER_PASSWORD_CHANGE, targetType = "SysUser", target = "#userId")
    public void changePassword(Long tenantId, Long userId, String rawPassword, String actor) {
        PasswordPolicy.validate(rawPassword);
        SysUser user = requireUser(tenantId, userId);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedBy(actor);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "userPermissions", key = "#tenantId + ':' + #userId")
    @Audited(action = AuditActions.USER_STATUS_CHANGE, targetType = "SysUser", target = "#userId")
    public void changeStatus(Long tenantId, Long userId, String status, String actor) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "status 仅允许 ACTIVE/DISABLED");
        }
        SysUser user = requireUser(tenantId, userId);
        user.setStatus(status);
        // 递增 token 版本：该用户此前签发的所有 JWT 立即失效（B3）
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedBy(actor);
        userMapper.updateById(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SysUser> listUsers(Long tenantId, int page, int pageSize) {
        Page<SysUser> req = new Page<>(page, pageSize);
        var result = userMapper.selectPage(req, new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getIsDeleted, false)
                .orderByAsc(SysUser::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSecurityState securityState(Long tenantId, Long userId) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getIsDeleted, false));
        if (user == null) {
            return new UserSecurityState("MISSING", -1L);
        }
        return new UserSecurityState(user.getStatus(), user.getTokenVersion());
    }

    private SysUser requireUser(Long tenantId, Long userId) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getIsDeleted, false));
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void bindRoles(Long tenantId, Long userId, List<String> roleCodes) {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)
                .in(SysRole::getCode, roleCodes)
                .eq(SysRole::getIsDeleted, false));
        if (roles.size() != roleCodes.stream().distinct().count()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "角色编码不存在");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, tenantId)
                .eq(SysUserRole::getUserId, userId));
        for (SysRole role : roles) {
            SysUserRole link = new SysUserRole();
            link.setTenantId(tenantId);
            link.setUserId(userId);
            link.setRoleId(role.getId());
            userRoleMapper.insert(link);
        }
    }

    /** 登录/登出没有 SecurityContext，用显式 record 留痕。 */
    private void audit(String action, Long tenantId, String actor, String targetId,
                       String beforeJson, String afterJson) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setActor(actor != null ? actor : MDC.get(Constants.TRACE_ID));
        log.setAction(action);
        log.setTargetType("SysUser");
        log.setTargetId(targetId);
        log.setBeforeJson(beforeJson);
        log.setAfterJson(afterJson);
        log.setTraceId(MDC.get(Constants.TRACE_ID));
        auditService.record(log);
    }

}
