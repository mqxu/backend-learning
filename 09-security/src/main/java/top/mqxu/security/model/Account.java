package top.mqxu.security.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号的领域对象：安全层只认它，不认数据库实体
 * <p>
 * 为什么要多这一层：UserDetailsService 需要“用户 + 角色”，
 * 而实体 User 只有一张表的内容，角色在另一张表。
 * 用 Account 把两者合起来，SecurityUser 就不用知道表结构。
 **/
public record Account(

        Long id,
        String username,
        String password,
        String nickname,
        boolean enabled,
        Set<Role> roles
) {

    /**
     * 角色名，给登录响应与前端菜单用
     */
    public Set<String> roleNames() {
        Set<String> names = new LinkedHashSet<>();
        roles.forEach(role -> names.add(role.name()));
        return names;
    }
}
