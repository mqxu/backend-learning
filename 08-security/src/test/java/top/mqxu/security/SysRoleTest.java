package top.mqxu.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.mqxu.security.entity.SysRole;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 角色与权限点的字符串转换。看起来只是一行 if，错了却会让所有 hasRole 悄悄失效。
 *
 * @author mqxu
 */
class SysRoleTest {

    @Test
    @DisplayName("ROLE 类型补 ROLE_ 前缀，对上 hasRole(\"ADMIN\")")
    void roleGetsPrefix() {
        SysRole role = new SysRole();
        role.setRoleCode("ADMIN");
        role.setGrantType(SysRole.TYPE_ROLE);

        assertThat(role.toAuthority()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("PERM 类型原样使用，对上 hasAuthority(\"ARTICLE_DELETE\")")
    void permissionStaysAsIs() {
        SysRole perm = new SysRole();
        perm.setRoleCode("ARTICLE_DELETE");
        perm.setGrantType(SysRole.TYPE_PERM);

        assertThat(perm.toAuthority()).isEqualTo("ARTICLE_DELETE");
    }

    @Test
    @DisplayName("grant_type 为空时按角色处理，避免历史数据把权限整条丢掉")
    void nullGrantTypeTreatedAsRole() {
        SysRole role = new SysRole();
        role.setRoleCode("USER");
        role.setGrantType(null);

        assertThat(role.toAuthority()).isEqualTo("ROLE_USER");
    }
}
