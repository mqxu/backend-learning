package top.mqxu.security.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import top.mqxu.security.model.Account;
import top.mqxu.security.model.Role;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description UserDetails 实现，把账号的角色摊平成权限集合
 * <p>
 * Spring Security 不认识我们的 Account，只认 UserDetails，
 * 这个类就是两者之间的适配层。换存储时它不用改。
 **/
public class SecurityUser implements UserDetails {

    private final Account account;

    private final List<GrantedAuthority> authorities;

    public SecurityUser(Account account) {
        this.account = account;
        this.authorities = Role.authorities(account.roles()).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public Account account() {
        return account;
    }

    /**
     * 用户 id。做数据归属判断时用它，别拿用户名当外键
     */
    public Long id() {
        return account.id();
    }

    /**
     * 只取角色名，供登录响应与页面菜单使用
     */
    public Set<String> roleNames() {
        return account.roleNames();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return account.password();
    }

    @Override
    public String getUsername() {
        return account.username();
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
        return account.enabled();
    }
}
