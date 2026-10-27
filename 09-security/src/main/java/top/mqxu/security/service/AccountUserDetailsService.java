package top.mqxu.security.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import top.mqxu.security.security.SecurityUser;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 把账号装成 Spring Security 认识的 UserDetails
 * <p>
 * 换存储只改 UserService，这个类不动。
 **/
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserService userService;

    public AccountUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userService.findByUsername(username)
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + username));
    }
}
