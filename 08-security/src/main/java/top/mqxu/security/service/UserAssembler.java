package top.mqxu.security.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import top.mqxu.security.entity.SysRole;
import top.mqxu.security.entity.SysUser;
import top.mqxu.security.mapper.SysRoleMapper;

import java.util.List;

/**
 * 把库里的账号行组装成 Security 认识的 UserDetails。
 * <p>
 * 密码登录和短信登录查人的条件不同（username / phone），但装权限、装配账号状态是同一步，
 * 抽出来免得写两遍。
 *
 * @author mqxu
 */
@Component
public class UserAssembler {

    private final SysRoleMapper roleMapper;

    public UserAssembler(SysRoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    public UserDetails toUserDetails(SysUser user) {
        return User.withUsername(user.getUsername())
                // 库里存的已经是带 {bcrypt} 前缀的密文，这里原样传，由 DelegatingPasswordEncoder 解析
                .password(user.getPassword())
                .authorities(authoritiesOf(user.getId()))
                .disabled(!Boolean.TRUE.equals(user.getEnabled()))
                .accountLocked(Boolean.TRUE.equals(user.getLocked()))
                .build();
    }

    /**
     * 查这个账号的角色与权限点。hasRole("ADMIN") 找的是 ROLE_ADMIN，
     * 库里存的是 ADMIN，前缀在 SysRole#toAuthority 里补。
     */
    public List<GrantedAuthority> authoritiesOf(Long userId) {
        List<SysRole> roles = roleMapper.selectList(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getUserId, userId));
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.toAuthority()))
                .toList();
    }
}
