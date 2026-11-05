package top.mqxu.security.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.mqxu.security.config.SecurityProperties;
import top.mqxu.security.entity.SysRole;
import top.mqxu.security.entity.SysUser;
import top.mqxu.security.mapper.SysRoleMapper;
import top.mqxu.security.mapper.SysUserMapper;

import java.util.UUID;

/**
 * 短信登录查人：按手机号查库。
 * <p>
 * 与 DbUserDetailsService 是两个不同的 Bean，容器里同时存在两个 UserDetailsService，
 * 按类型注入会报 NoUniqueBeanDefinitionException，注入时必须用 Bean 名或 @Qualifier 区分。
 *
 * @author mqxu
 */
@Slf4j
@Service("phoneUserDetailsService")
public class PhoneUserDetailsService implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final UserAssembler assembler;
    private final boolean autoRegister;

    public PhoneUserDetailsService(SysUserMapper userMapper,
                                   SysRoleMapper roleMapper,
                                   UserAssembler assembler,
                                   SecurityProperties properties) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.assembler = assembler;
        this.autoRegister = properties.getSms().isAutoRegister();
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, phone));

        if (user == null) {
            if (!autoRegister) {
                throw new UsernameNotFoundException("手机号未注册：" + mask(phone));
            }
            user = register(phone);
        }
        return assembler.toUserDetails(user);
    }

    /**
     * 首次短信登录即注册。
     * <p>
     * 密码列存一个随机串的明文占位，密码登录时没有任何人能猜到，等于这个账号只能用短信进。
     * 真实业务要在“发码”前就校验手机号是否已注册，否则任何知道手机号的人都能建号。
     */
    private SysUser register(String phone) {
        SysUser user = new SysUser();
        user.setUsername(phone);
        user.setPhone(phone);
        user.setPassword("{noop}" + UUID.randomUUID());
        user.setNickname("短信用户");
        user.setEnabled(true);
        user.setLocked(false);
        userMapper.insert(user);

        SysRole role = new SysRole();
        role.setUserId(user.getId());
        role.setRoleCode("USER");
        role.setGrantType(SysRole.TYPE_ROLE);
        roleMapper.insert(role);

        log.info("手机号 {} 首次登录，已自动建号 id={}", mask(phone), user.getId());
        return user;
    }

    private String mask(String phone) {
        return phone == null || phone.length() < 7
                ? phone
                : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
