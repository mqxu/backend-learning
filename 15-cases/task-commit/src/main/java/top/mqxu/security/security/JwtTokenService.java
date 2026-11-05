package top.mqxu.security.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import top.mqxu.security.config.JwtProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description JWT 签发与解析
 * <p>
 * 令牌里只放身份（sub）与用于展示的角色名。权限一律在过滤器中按用户名回查，
 * 这样改角色能立刻生效，代价是每次请求多一次用户查询。
 **/
@Service
public class JwtTokenService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issue(UserDetails user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.expireMinutes(), ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("roles", rolesOf(user))
                // 固定 HS256。signWith(key) 单参数版本会按密钥长度自动升级算法：
                // 密钥超过 48 字节时签出来的是 HS384，令牌长度与文档示例对不上
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * 校验签名、签发方与过期时间，返回令牌所属用户名
     *
     * @throws io.jsonwebtoken.JwtException 签名不对、格式损坏或已过期
     */
    public String parseUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    private List<String> rolesOf(UserDetails user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
    }

    /**
     * @param token     待下发给客户端的令牌
     * @param expiresAt 过期时间，随响应一起返回，前端可据此提前刷新
     */
    public record IssuedToken(String token, Instant expiresAt) {
    }
}
