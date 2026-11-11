package com.campus.activity.controller.app;

import com.campus.activity.common.Result;
import com.campus.activity.common.UserContext;
import com.campus.activity.dto.BindRequest;
import com.campus.activity.dto.LoginRequest;
import com.campus.activity.service.AuthService;
import com.campus.activity.vo.LoginVO;
import com.campus.activity.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/bind")
    public Result<UserVO> bind(@Valid @RequestBody BindRequest request) {
        return Result.ok(authService.bind(UserContext.requireUserId(), request));
    }

    /** 登出：把当前 token 的 jti 拉黑，之后再用它访问一律 401 */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(authorization);
        return Result.ok();
    }
}
