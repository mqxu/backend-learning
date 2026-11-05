package top.mqxu.security.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import top.mqxu.security.entity.SysUser;
import top.mqxu.security.mapper.SysUserMapper;

/**
 * 密码登录查人：按 username 查库。
 * <p>
 * 只要容器里有这个 Bean，Security 自动配置的随机密码用户就不会再生成。
 * 查不到时只能抛 UsernameNotFoundException，不能返回 null，
 * 否则会被当成系统故障返回 500，而不是认证失败的 401。
 *
 * @author mqxu
 */
@Service("dbUserDetailsService")
public class DbUserDetailsService implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final UserAssembler assembler;

    public DbUserDetailsService(SysUserMapper userMapper, UserAssembler assembler) {
        this.userMapper = userMapper;
        this.assembler = assembler;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        return assembler.toUserDetails(user);
    }
}
