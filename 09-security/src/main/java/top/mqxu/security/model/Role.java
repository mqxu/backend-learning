package top.mqxu.security.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 角色与权限点的映射，权限点命名约定「资源:动作」
 * <p>
 * 角色给的是粗粒度身份，权限点才是接口鉴权的依据。
 **/
public enum Role {

    ADMIN(Set.of("user:read", "user:write", "report:read")),
    USER(Set.of("report:read"));

    private final Set<String> permissions;

    Role(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<String> permissions() {
        return permissions;
    }

    /**
     * hasRole("ADMIN") 实际比对的是 ROLE_ADMIN，前缀由 Security 补，别手动写进配置里
     */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * 把角色摊平成 GrantedAuthority 字符串：角色名 + 该角色的全部权限点
     */
    public static List<String> authorities(Collection<Role> roles) {
        return roles.stream()
                .flatMap(role -> Stream.concat(Stream.of(role.authority()), role.permissions().stream()))
                .distinct()
                .toList();
    }
}
