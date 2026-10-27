package top.mqxu.security.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;
import top.mqxu.security.dto.CreateUserRequest;
import top.mqxu.security.security.SecurityUser;
import top.mqxu.security.service.UserService;
import top.mqxu.security.vo.UserProfile;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号管理接口，由 URL 规则（/admin/**）与方法注解双重把关
 **/
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<List<UserProfile>> list() {
        return Result.ok(userService.listProfiles());
    }

    /**
     * 显式要求 user:write 权限点：即便某天把 /admin/** 的规则放宽，这里仍是第二道防线
     */
    @PostMapping
    @PreAuthorize("hasAuthority('user:write')")
    public Result<UserProfile> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.ok(userService.create(request));
    }

    @DeleteMapping("/{username}")
    public Result<Void> delete(@PathVariable String username, @AuthenticationPrincipal SecurityUser user) {
        userService.delete(username, user.getUsername());
        return Result.ok();
    }
}
