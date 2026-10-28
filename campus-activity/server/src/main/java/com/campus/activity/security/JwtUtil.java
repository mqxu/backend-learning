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

    public String generate(SysUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole())
                .claim("orgId", user.getOrgId())
                .claim("nickname", user.getNickname() == null ? "" : user.getNickname())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(hours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /** 解析失败（签名不对、过期、格式错误）统一返回 null，由调用方决定是否放行 */
    public LoginUser parse(String token) {
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
            return new LoginUser(userId, role, orgId, nickname);
        } catch (Exception e) {
            return null;
        }
    }
}
