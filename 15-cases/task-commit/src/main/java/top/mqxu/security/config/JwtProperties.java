package top.mqxu.security.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description JWT 配置项。密钥走配置与环境变量，不写在代码里
 * <p>
 * HS256 的签名密钥最短 256 bit，即 32 个字节，这里用 @Size 在校验阶段就拦住，
 * 而不是等到签发第一个令牌时抛 WeakKeyException。
 **/
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank
        @Size(min = 32, message = "JWT 密钥至少 32 个字符（HS256 要求 256 bit）")
        String secret,

        @NotBlank
        String issuer,

        @Min(value = 1, message = "JWT 有效期至少 1 分钟")
        long expireMinutes
) {
}
