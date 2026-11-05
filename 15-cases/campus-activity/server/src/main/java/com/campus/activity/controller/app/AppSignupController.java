package com.campus.activity.controller.app;

import com.campus.activity.common.PageResult;
import com.campus.activity.common.Result;
import com.campus.activity.common.UserContext;
import com.campus.activity.dto.CancelRequest;
import com.campus.activity.dto.SignupRequest;
import com.campus.activity.security.RequireLogin;
import com.campus.activity.service.SignupService;
import com.campus.activity.vo.SignupVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppSignupController {

    private final SignupService signupService;

    @RequireLogin
    @PostMapping("/activities/{id}/signup")
    public Result<SignupVO> signup(@PathVariable Long id, @RequestBody(required = false) SignupRequest request) {
        return Result.ok(signupService.signup(UserContext.get(), id, request));
    }

    @RequireLogin
    @GetMapping("/signups")
    public Result<PageResult<SignupVO>> mine(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(signupService.pageMine(UserContext.requireUserId(), status, page, size));
    }

    @RequireLogin
    @GetMapping("/signups/{id}")
    public Result<SignupVO> detail(@PathVariable Long id) {
        return Result.ok(signupService.signupDetail(UserContext.requireUserId(), id));
    }

    @RequireLogin
    @PostMapping("/signups/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        signupService.cancel(UserContext.get(), id);
        return Result.ok();
    }
}
