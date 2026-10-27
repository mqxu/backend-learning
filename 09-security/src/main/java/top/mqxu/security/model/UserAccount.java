package top.mqxu.security.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 用户账号。内存版演示对象，真实项目里对应数据库表
 * <p>
 * password 字段存的是 BCrypt 密文，任何接口都不应把它返回给前端。
 **/
@Getter
public class UserAccount {

    private final String username;
    @Setter
    private String password;
    @Setter
    private String nickname;
    @Setter
    private Set<Role> roles;
    @Setter
    private boolean enabled;

    public UserAccount(String username, String password, String nickname, Set<Role> roles, boolean enabled) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.roles = roles;
        this.enabled = enabled;
    }
}
