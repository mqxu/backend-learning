package top.mqxu.redis.dto;

import lombok.Data;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description LoginRequest
 **/
@Data
public class LoginRequest {
    private String phone;
    private String code;
}