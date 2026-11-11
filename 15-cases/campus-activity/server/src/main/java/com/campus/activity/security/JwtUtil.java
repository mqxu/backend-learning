package com.campus.activity.security;

import com.campus.activity.common.LoginUser;
import com.campus.activity.config.CampusProperties;
import com.campus.activity.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 的签发与解析。
 * <p>
 * 除了身份信息，token 里额外放一个 jti（唯一 id）：登出时把 jti 写进 Redis 黑名单，
 * 在 token 自然过期前都能被识别成“已失效的旧凭证”。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final int hours;

    public JwtUtil(CampusProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("campus.jwt.secret 长度至少 32 个字符");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.hours = properties.getJwt().getAccessTokenHours();
    }

    /** 签发 token：sub 存用户 id，jti 存唯一标识，过期时间由配置决定 */
    public String generate(SysUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole())
                .claim("orgId", user.getOrgId())
                .claim("nickname", user.getNickname() == null ? "" : user.getNickname())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(hours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验签，返回身份、jti 与过期时间。
     * 解析失败（签名不对、过期、格式错误）统一返回 null，由调用方决定是否放行。
     */
    public TokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            Object orgIdRaw = claims.get("orgId");
            Long orgId = orgIdRaw instanceof Number number ? number.longValue() : null;
            String nickname = claims.get("nickname", String.class);
            LoginUser user = new LoginUser(userId, role, orgId, nickname);
            return new TokenClaims(user, claims.getId(), claims.getExpiration().toInstant());
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析结果：登录身份 + 黑名单要用的 jti + 黑名单 TTL 要用的过期时间 */
    public record TokenClaims(LoginUser user, String jti, Instant expiresAt) {
    }
}
