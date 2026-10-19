package top.mqxu.redis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description LoginResponse
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String phone;
    private String message;
    /**
     * token 有效期，单位秒
     */
    private Long expiresIn;

    public LoginResponse(String token, String phone, long expiresIn) {
        this.token = token;
        this.phone = phone;
        this.message = "登录成功";
        this.expiresIn = expiresIn;
    }
}