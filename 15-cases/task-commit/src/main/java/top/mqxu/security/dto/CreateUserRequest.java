package top.mqxu.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import top.mqxu.security.model.Role;

import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 新增用户请求。密码明文只在这个对象里出现，落库前必须加密
 **/
public record CreateUserRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度 3 到 32 个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度 6 到 64 个字符")
        String password,

        @NotBlank(message = "昵称不能为空")
        String nickname,

        @NotEmpty(message = "至少指定一个角色")
        Set<Role> roles
) {
}
