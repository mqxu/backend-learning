package com.campus.activity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 微信登录请求。devMode 打开时可直接传 devOpenId，跳过 code2session。
 */
public record LoginRequest(
        String code,
        String devOpenId,
        String nickname,
        String avatarUrl
) {
}
