package top.mqxu.zhihu.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 签发与解析。
 *
 * <p>这里只用 HS256：一把对称密钥，签发和校验都是它。
 * 密钥从配置读，生产环境用环境变量覆盖，不要写死在代码或 yml 里。</p>
 *
 * @author mqxu
 */
@Slf4j
@Component
public class JwtService {

    private final SecretKey key;

    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expire-hours:24}") long expireHours) {
        // HS256 要求密钥不短于 256 bit；短了 jjwt 会直接抛异常，这是好事，别绕过
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofHours(expireHours);
    }

    public String createToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

    /**
     * 解析失败（过期、签名不对、格式错）一律返回 null，由调用方决定怎么处理。
     * 这里不抛异常，是因为"没带 token 的游客"和"token 过期的人"在对外的表现上是一样的。
     */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            log.debug("token 解析失败：{}", e.getMessage());
            return null;
        }
    }
}
