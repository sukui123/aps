package com.aps.api.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** 认证主体：携带租户/工厂范围与权限码，供方法级鉴权与租户注入共用。 */
public class UserPrincipal implements UserDetails {

    private final Long userId;
    private final Long tenantId;
    private final String username;
    private final String displayName;
    private final String jti;
    private final List<String> roles;
    private final List<Long> factoryIds;
    private final List<String> permissions;

    public UserPrincipal(Long userId, Long tenantId, String username, String displayName, String jti,
                         List<String> roles, List<Long> factoryIds, List<String> permissions) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.displayName = displayName;
        this.jti = jti;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.factoryIds = factoryIds == null ? List.of() : List.copyOf(factoryIds);
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getJti() {
        return jti;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<Long> getFactoryIds() {
        return factoryIds;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        java.util.List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        permissions.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
