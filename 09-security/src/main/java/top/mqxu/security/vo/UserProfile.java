package top.mqxu.security.vo;

import top.mqxu.security.model.Account;
import top.mqxu.security.model.Role;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号的对外视图：只带白名单字段，密码压根不进来
 **/
public record UserProfile(

        String username,
        String nickname,
        Set<String> roles,
        List<String> authorities,
        boolean enabled
) {

    public static UserProfile of(Account account) {
        Set<String> roles = new LinkedHashSet<>(account.roleNames());
        return new UserProfile(account.username(), account.nickname(),
                roles, Role.authorities(account.roles()), account.enabled());
    }
}
