package com.campus.activity.sms.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 发送短信验证码请求。
 *
 * <p>手机号只做非空校验，具体格式由 SmsCodeService 校验，
 * 这样格式错误能返回 60001 而不是笼统的参数错误。</p>
 */
public record SmsSendCodeRequest(
        @NotBlank(message = "手机号不能为空")
        String phone) {
}
