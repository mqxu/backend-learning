package top.mqxu.security.vo;

import top.mqxu.security.model.Role;
import top.mqxu.security.model.UserAccount;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 用户视图对象：对外只暴露白名单字段，密码字段压根不进来
 **/
public record UserProfile(

        String username,
        String nickname,
        Set<String> roles,
        List<String> authorities,
        boolean enabled
) {

    public static UserProfile of(UserAccount account) {
        Set<String> roles = account.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new UserProfile(account.getUsername(), account.getNickname(),
                roles, Role.authorities(account.getRoles()), account.isEnabled());
    }
}
