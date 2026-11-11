package com.campus.activity.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.activity.entity.SysUser;
import com.campus.activity.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 后台账号密码登录查人：按 username 查 sys_user。
 * <p>
 * 查不到只能抛 UsernameNotFoundException，不能返回 null，否则会被当成系统故障。
 */
@Service("sysUserDetailsService")
@RequiredArgsConstructor
public class SysUserDetailsService implements UserDetailsService {

    private final SysUserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        return new SysUserDetails(user);
    }
}
