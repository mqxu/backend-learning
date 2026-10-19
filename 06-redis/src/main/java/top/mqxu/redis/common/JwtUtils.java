package top.mqxu.redis.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 的签发与校验。
 * <p>
 * payload 里放手机号（sub）、签发时间（iat）、过期时间（exp），整个串用服务端密钥签名，
 * 服务端不保存任何会话状态。
 *
 * @author mqxu
 */
@Component
public class JwtUtils {

    private final SecretKey secretKey;
    private final long expireSeconds;

    public JwtUtils(@Value("${mqxu.jwt.secret}") String secret,
                    @Value("${mqxu.jwt.expire-seconds}") long expireSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    /**
     * 签发 token
     *
     * @param phone 手机号，写进 sub
     * @return 三段式的 JWT 字符串
     */
    public String create(String phone) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(phone)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 校验签名与有效期，返回载荷。签名不对、结构不对、已过期都抛 40100。
     *
     * @param token 不带 Bearer 前缀的 token
     * @return 载荷
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }
}
