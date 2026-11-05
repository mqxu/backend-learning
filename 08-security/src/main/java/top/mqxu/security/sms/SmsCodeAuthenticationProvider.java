package top.mqxu.security.sms;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.Assert;

/**
 * 手机号 + 验证码的认证器。
 * <p>
 * 只认 SmsCodeAuthenticationToken，别的凭证一律不接。校验通过后返回一个新的已认证 Token，
 * 权限来自 UserDetails，所以下游的 hasRole / @PreAuthorize 不用为短信登录改任何一行。
 *
 * @author mqxu
 */
public class SmsCodeAuthenticationProvider implements AuthenticationProvider {

    private final SmsCodeService smsCodeService;
    private final UserDetailsService phoneUserDetailsService;

    public SmsCodeAuthenticationProvider(SmsCodeService smsCodeService,
                                         UserDetailsService phoneUserDetailsService) {
        this.smsCodeService = smsCodeService;
        this.phoneUserDetailsService = phoneUserDetailsService;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return SmsCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String phone = (String) authentication.getPrincipal();
        String code = (String) authentication.getCredentials();
        Assert.hasText(phone, "手机号不能为空");

        // 校验通过会立刻删除，同一个码不能用第二次
        if (!smsCodeService.verify(phone, code)) {
            throw new BadCredentialsException("验证码错误或已过期");
        }

        UserDetails user = phoneUserDetailsService.loadUserByUsername(phone);

        // 账号状态检查必须自己做：DaoAuthenticationProvider 是替密码登录做的，不会替这里做。
        // 漏掉这一段，被禁用或锁定的账号照样能靠短信登进来。
        if (!user.isEnabled()) {
            throw new DisabledException("账号已被禁用");
        }
        if (!user.isAccountNonLocked()) {
            throw new LockedException("账号已被锁定");
        }
        if (!user.isAccountNonExpired()) {
            throw new AccountExpiredException("账号已过期");
        }
        if (!user.isCredentialsNonExpired()) {
            throw new CredentialsExpiredException("凭证已过期");
        }

        SmsCodeAuthenticationToken result =
                new SmsCodeAuthenticationToken(user, user.getAuthorities());
        result.setDetails(authentication.getDetails());
        return result;
    }
}
