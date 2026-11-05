package top.mqxu.security.dto;

import java.util.List;

/**
 * 登录成功后的返回体
 *
 * @author mqxu
 */
public record LoginResponse(String token,
                            String tokenType,
                            long expiresIn,
                            String username,
                            List<String> authorities) {
}
