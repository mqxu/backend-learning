package top.mqxu.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 登录请求
 **/
public record LoginRequest(

        @NotBlank(message = "用户名不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
