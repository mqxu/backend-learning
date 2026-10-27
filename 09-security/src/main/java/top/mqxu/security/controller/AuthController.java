package top.mqxu.security.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;
import top.mqxu.security.dto.LoginRequest;
import top.mqxu.security.security.SecurityUser;
import top.mqxu.security.service.AuthService;
import top.mqxu.security.vo.LoginResponse;
import top.mqxu.security.vo.UserProfile;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 认证接口
 **/
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 唯一放行的接口，其余都要带令牌
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 用令牌换当前用户信息，前端刷新页面时用它恢复登录态
     */
    @GetMapping("/me")
    public Result<UserProfile> me(@AuthenticationPrincipal SecurityUser user) {
        return Result.ok(UserProfile.of(user.account()));
    }
}
