package top.mqxu.security.sms;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * 短信验证码凭证。
 * <p>
 * 登录前 principal 是手机号、credentials 是验证码、authenticated=false；
 * 登录成功后由 Provider 用带 authorities 的构造器重新造一个，principal 换成 UserDetails、credentials 置空。
 *
 * @author mqxu
 */
public class SmsCodeAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private Object credentials;

    /**
     * 登录前：前端提交的手机号与验证码
     */
    public SmsCodeAuthenticationToken(String phone, String code) {
        // Security 7 的 AbstractAuthenticationToken 有两个接收 Collection 的构造器，
        // 直接写 super(null) 会编译不过（reference to ... is ambiguous），必须强转告诉编译器选哪个
        super((Collection<? extends GrantedAuthority>) null);
        this.principal = phone;
        this.credentials = code;
        setAuthenticated(false);
    }

    /**
     * 认证成功后由 Provider 创建
     */
    public SmsCodeAuthenticationToken(Object principal,
                                      Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = null;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    /**
     * 不允许事后把未认证的 Token 改成已认证，否则可能出现“通过了认证检查却没有任何权限”的用户。
     */
    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException("请使用带 authorities 的构造方法");
        }
        super.setAuthenticated(false);
    }

    /**
     * 验证码用完就抹掉，不要留在内存里
     */
    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        this.credentials = null;
    }
}
