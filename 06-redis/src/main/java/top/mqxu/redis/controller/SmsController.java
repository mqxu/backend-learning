package top.mqxu.redis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.mqxu.redis.common.Result;
import top.mqxu.redis.dto.LoginRequest;
import top.mqxu.redis.dto.LoginResponse;
import top.mqxu.redis.service.SmsService;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description SmsController
 **/
@RestController
@RequestMapping("/sms")
@RequiredArgsConstructor
public class SmsController {
    private final SmsService smsService;

    /**
     * 发送验证码。模拟通道下把验证码放在 data 里返回，方便本地联调
     */
    @PostMapping("/send")
    public Result<String> send(@RequestParam String phone) {
        String mockCode = smsService.sendSms(phone);
        return Result.ok(mockCode == null ? "短信发送成功" : mockCode);
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        return Result.ok(smsService.login(loginRequest));
    }
}
