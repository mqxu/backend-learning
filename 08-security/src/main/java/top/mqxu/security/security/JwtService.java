package top.mqxu.security.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import top.mqxu.security.config.SecurityProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * JWT 的签发与解析。
 * <p>
 * payload 里放用户名（sub）、权限列表、签发与过期时间。服务端不保存任何会话状态。
 * payload 只是 Base64 编码，不是加密，任何人拿到 token 都能解开，所以不要往里面塞密码、手机号。
 *
 * @author mqxu
 */
@Service
public class JwtService {

    /** 放自定义权限列表的 claim 名 */
    public static final String CLAIM_AUTHORITIES = "authorities";

    private final SecretKey key;
    private final Duration expire;

    public JwtService(SecurityProperties properties) {
        // 密钥长度决定签名算法：64 字节以上是 HS512
        this.key = Keys.hmacShaKeyFor(
                properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.expire = Duration.ofMinutes(properties.getJwt().getExpireMinutes());
    }

    /**
     * 签发 token
     *
     * @param username    写进 sub
     * @param authorities 权限字符串，如 ROLE_ADMIN
     */
    public String generate(String username, List<String> authorities) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_AUTHORITIES, authorities)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expire)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验签名与有效期并返回载荷。签名不对、结构不对、已过期都抛 JwtException。
     */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpireSeconds() {
        return expire.toSeconds();
    }
}
