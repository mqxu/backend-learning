package top.mqxu.security.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 三个角色与各自的权限点，权限点命名约定“资源:动作”
 * <p>
 * 角色给人看，权限点给接口用。接口只认权限点，将来多一类人（比如教务）时，
 * 加个角色、配好权限点就行，接口代码一行都不用改。
 **/
public enum Role {

    STUDENT(Set.of("task:submit", "task:read")),
    TEACHER(Set.of("task:review", "task:read")),
    ADMIN(Set.of("user:read", "user:write", "task:read", "task:review"));

    private final Set<String> permissions;

    Role(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<String> permissions() {
        return permissions;
    }

    /**
     * hasRole("ADMIN") 实际比对的是 ROLE_ADMIN，前缀由 Security 补，别手动写进配置
     */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * 把角色摊平成权限字符串：角色名 + 该角色的全部权限点
     */
    public static List<String> authorities(Collection<Role> roles) {
        return roles.stream()
                .flatMap(role -> Stream.concat(Stream.of(role.authority()), role.permissions().stream()))
                .distinct()
                .toList();
    }
}
