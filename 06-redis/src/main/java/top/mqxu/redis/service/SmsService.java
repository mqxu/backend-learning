package top.mqxu.redis.service;

import top.mqxu.redis.dto.LoginRequest;
import top.mqxu.redis.dto.LoginResponse;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description SmsService
 **/
public interface SmsService {
    /**
     * 发送验证码短信。发送失败、校验不通过都通过 BusinessException 往外抛
     *
     * @param phone 手机号
     */
    void sendSms(String phone);

    /**
     * 验证码登录
     *
     * @param loginRequest 登录请求参数
     * @return 登录响应
     */
    LoginResponse login(LoginRequest loginRequest);
}
