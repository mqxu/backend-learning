package top.mqxu.zhihu.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.zhihu.common.Result;
import top.mqxu.zhihu.service.UserService;
import top.mqxu.zhihu.vo.UserProfileVO;

/**
 * 个人主页。
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Result<UserProfileVO> profile(@PathVariable Long id) {
        return Result.ok(userService.profile(id));
    }
}
