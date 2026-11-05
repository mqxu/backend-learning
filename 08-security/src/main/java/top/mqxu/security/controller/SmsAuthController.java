package top.mqxu.security.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;
import top.mqxu.security.config.SecurityProperties;
import top.mqxu.security.dto.LoginResponse;
import top.mqxu.security.dto.SmsLoginRequest;
import top.mqxu.security.security.JwtService;
import top.mqxu.security.sms.SmsCodeAuthenticationToken;
import top.mqxu.security.sms.SmsCodeService;

import java.util.List;

/**
 * 短信登录。分两步：先发码，再用“手机号 + 验证码”换 token。
 * <p>
 * 关键点：短信登录不是“又写一个登录接口”，而是给 Security 增加一种凭证类型。
 * 这里只负责把 SmsCodeAuthenticationToken 交给 AuthenticationManager，
 * 校验、账号状态检查、权限装配全部由 SmsCodeAuthenticationProvider 完成。
 *
 * @author mqxu
 */
@Validated
@RestController
@RequestMapping("/api/auth/sms")
public class SmsAuthController {

    private final SmsCodeService smsCodeService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final boolean exposeCode;

    public SmsAuthController(SmsCodeService smsCodeService,
                             AuthenticationManager authenticationManager,
                             JwtService jwtService,
                             SecurityProperties properties) {
        this.smsCodeService = smsCodeService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.exposeCode = properties.getSms().isExposeCode();
    }

    /**
     * 第 1 步：发送验证码
     */
    @PostMapping("/code")
    public Result<String> sendCode(
            @RequestParam
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone) {
        String code = smsCodeService.sendCode(phone);
        // 演示模式把验证码回显出来，生产环境必须关掉 expose-code
        return Result.ok(exposeCode
                ? "验证码已发送（演示模式，验证码：" + code + "）"
                : "验证码已发送");
    }

    /**
     * 第 2 步：用手机号 + 验证码换 token
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody SmsLoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new SmsCodeAuthenticationToken(request.phone(), request.code()));

        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String token = jwtService.generate(authentication.getName(), authorities);
        return Result.ok(new LoginResponse(token, "Bearer",
                jwtService.getExpireSeconds(), authentication.getName(), authorities));
    }
}
