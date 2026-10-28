package com.campus.activity.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
        @NotBlank(message = "请填写账号") String username,
        @NotBlank(message = "请填写密码") String password
) {
}
