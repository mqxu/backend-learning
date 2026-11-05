package com.campus.activity.controller.app;

import com.campus.activity.common.Result;
import com.campus.activity.common.UserContext;
import com.campus.activity.dto.BindRequest;
import com.campus.activity.dto.LoginRequest;
import com.campus.activity.security.RequireLogin;
import com.campus.activity.service.AuthService;
import com.campus.activity.vo.LoginVO;
import com.campus.activity.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/auth")
@RequiredArgsConstructor
public class AppAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @RequireLogin
    @PostMapping("/bind")
    public Result<UserVO> bind(@Valid @RequestBody BindRequest request) {
        return Result.ok(authService.bind(UserContext.requireUserId(), request));
    }
}
