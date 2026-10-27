package top.mqxu.security.vo;

import java.time.Instant;
import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 登录响应。只回令牌与身份信息，不回密码
 * <p>
 * 昵称一起带回去，前端登录后立刻能显示“陶老师”这类称呼，省掉一次 /auth/me 请求。
 **/
public record LoginResponse(

        String token,
        String tokenType,
        Instant expiresAt,
        String username,
        String nickname,
        Set<String> roles
) {
}
