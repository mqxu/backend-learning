package top.mqxu.security.sms;

/**
 * 验证码服务。发码与验码只有这两个动作，换存储实现不影响 Provider。
 *
 * @author mqxu
 */
public interface SmsCodeService {

    /**
     * 生成并“发送”验证码。
     *
     * @param phone 手机号
     * @return 生成的验证码，仅供本地调试回显，生产环境不要返回给前端
     */
    String sendCode(String phone);

    /**
     * 校验验证码。通过则立即作废，不通过返回 false。
     */
    boolean verify(String phone, String code);
}
