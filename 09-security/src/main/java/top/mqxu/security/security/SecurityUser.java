package top.mqxu.security.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import top.mqxu.security.model.Role;
import top.mqxu.security.model.UserAccount;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description UserDetails 实现，把账号的角色摊平成权限集合
 **/
public class SecurityUser implements UserDetails {

    private final UserAccount account;
    private final List<GrantedAuthority> authorities;

    public SecurityUser(UserAccount account) {
        this.account = account;
        this.authorities = Role.authorities(account.getRoles()).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public UserAccount account() {
        return account;
    }

    /**
     * 只取角色名，供登录响应与 @AuthenticationPrincipal 使用
     */
    public Set<String> roleNames() {
        return account.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return account.getPassword();
    }

    @Override
    public String getUsername() {
        return account.getUsername();
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
        return account.isEnabled();
    }
}
