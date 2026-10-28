package com.campus.activity.controller.app;

import com.campus.activity.common.Result;
import com.campus.activity.common.UserContext;
import com.campus.activity.security.RequireLogin;
import com.campus.activity.service.AuthService;
import com.campus.activity.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/user")
@RequiredArgsConstructor
public class AppUserController {

    private final AuthService authService;

    @RequireLogin
    @GetMapping("/profile")
    public Result<UserVO> profile() {
        return Result.ok(authService.currentUser(UserContext.requireUserId()));
    }
}
