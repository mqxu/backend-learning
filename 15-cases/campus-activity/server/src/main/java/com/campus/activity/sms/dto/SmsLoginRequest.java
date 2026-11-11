package com.campus.activity.sms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 手机号 + 验证码登录请求。
 *
 * <p>验证码用 String，不要用 int，否则前导 0 会丢。</p>
 */
public record SmsLoginRequest(
        @NotBlank(message = "手机号不能为空")
        String phone,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
        String code) {
}
