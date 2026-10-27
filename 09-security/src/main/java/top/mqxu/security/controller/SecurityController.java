package top.mqxu.security.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;
import top.mqxu.security.security.SecurityUser;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 需要登录才能访问的最小示例
 **/
@RestController
@RequestMapping("/security")
public class SecurityController {

    /**
     * 能进到这里说明过滤器已校验令牌，@AuthenticationPrincipal 拿到的就是当前用户
     */
    @GetMapping("/hello")
    public Result<String> hello(@AuthenticationPrincipal SecurityUser user) {
        return Result.ok("认证与授权 模块，当前用户：" + user.getUsername());
    }

    /**
     * 看看当前令牌里到底带了哪些权限
     */
    @GetMapping("/authorities")
    public Result<List<String>> authorities(@AuthenticationPrincipal SecurityUser user) {
        return Result.ok(user.getAuthorities().stream().map(Object::toString).toList());
    }
}
