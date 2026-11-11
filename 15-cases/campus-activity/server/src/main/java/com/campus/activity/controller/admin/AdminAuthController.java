package com.campus.activity.controller.admin;

import com.campus.activity.common.Result;
import com.campus.activity.dto.AdminLoginRequest;
import com.campus.activity.service.AuthService;
import com.campus.activity.vo.LoginVO;
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
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.ok(authService.adminLogin(request));
    }

    /** 登出：后台账号同样把当前 token 的 jti 拉黑 */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(authorization);
        return Result.ok();
    }
}
