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

    @PostMapping("/send")
    public Result<String> send(@RequestParam String phone) {
        smsService.sendSms(phone);
        return Result.ok("短信发送成功");
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        return Result.ok(smsService.login(loginRequest));
    }
}
