package top.mqxu.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 密码登录请求
 *
 * @author mqxu
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password) {
}
