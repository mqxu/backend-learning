package com.campus.activity.security;

import com.campus.activity.entity.SysUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 视角下的后台账号。
 * <p>
 * 角色统一加 ROLE_ 前缀，方法级鉴权才能用 hasRole / hasAnyRole；
 * 状态 0（禁用）映射成 isEnabled=false，登录时由 DaoAuthenticationProvider 抛 DisabledException。
 */
@Getter
public class SysUserDetails implements UserDetails {

    /** 原始账号信息，登录成功后还要拿角色、昵称等拼 LoginVO */
    private final SysUser user;
    private final List<GrantedAuthority> authorities;

    public SysUserDetails(SysUser user) {
        this.user = user;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /** 账号未锁定，本项目没有锁定能力 */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** 账号未过期 */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** 凭证未过期 */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** status=1 正常、0 禁用 */
    @Override
    public boolean isEnabled() {
        return user.getStatus() != null && user.getStatus() == 1;
    }
}
