package top.mqxu.security.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import top.mqxu.security.repository.InMemoryUserRepository;
import top.mqxu.security.security.SecurityUser;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 按用户名加载账号，登录校验与 JWT 过滤器共用
 * <p>
 * 换数据库时只改这里的数据来源，DaoAuthenticationProvider 与过滤器都不用动。
 **/
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final InMemoryUserRepository repository;

    public AccountUserDetailsService(InMemoryUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repository.findByUsername(username)
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + username));
    }
}
