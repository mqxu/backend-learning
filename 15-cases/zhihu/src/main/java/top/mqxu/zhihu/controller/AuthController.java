package top.mqxu.zhihu.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.zhihu.common.Result;
import top.mqxu.zhihu.common.UserContext;
import top.mqxu.zhihu.dto.LoginRequest;
import top.mqxu.zhihu.dto.RegisterRequest;
import top.mqxu.zhihu.service.AuthService;
import top.mqxu.zhihu.vo.LoginVO;
import top.mqxu.zhihu.vo.UserVO;

/**
 * 认证。
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.current(UserContext.requireUserId()));
    }
}
