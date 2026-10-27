package top.mqxu.security.vo;

import java.time.Instant;
import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 登录响应。只回令牌与身份信息，不回密码
 **/
public record LoginResponse(

        String token,
        String tokenType,
        Instant expiresAt,
        String username,
        Set<String> roles
) {
}
