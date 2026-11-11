package com.campus.activity.controller.app;

import com.campus.activity.common.Result;
import com.campus.activity.sms.SmsAuthService;
import com.campus.activity.sms.SmsSendVO;
import com.campus.activity.sms.dto.SmsLoginRequest;
import com.campus.activity.sms.dto.SmsSendCodeRequest;
import com.campus.activity.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 手机号验证码登录（两个接口都不需要登录）。
 *
 * <p>先调 send-code 发码，再调 login 用“手机号 + 验证码”换 token；
 * token 与微信登录一致，后续请求带 Authorization: Bearer &lt;token&gt; 即可。</p>
 */
@RestController
@RequestMapping("/api/app/auth/sms")
@RequiredArgsConstructor
public class SmsAuthController {

    private final SmsAuthService smsAuthService;

    /** 发送验证码：Mock 通道且 expose-code=true 时响应里带 mockCode */
    @PostMapping("/send-code")
    public Result<SmsSendVO> sendCode(@Valid @RequestBody SmsSendCodeRequest request) {
        return Result.ok(smsAuthService.sendCode(request.phone()));
    }

    /** 手机号 + 验证码登录 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody SmsLoginRequest request) {
        return Result.ok(smsAuthService.login(request));
    }
}
