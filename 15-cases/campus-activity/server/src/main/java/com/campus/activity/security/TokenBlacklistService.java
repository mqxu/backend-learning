package com.campus.activity.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 黑名单：登出时把 token 的 jti 写入 Redis，请求进来时先查一遍。
 * <p>
 * key 形如 campus:auth:blacklist:&lt;jti&gt;，TTL 取 token 的剩余有效期，
 * 过期后自动消失，不需要额外的清理任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

    public static final String KEY_PREFIX = "campus:auth:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /** 拉黑一个 jti，ttl 为 token 的剩余有效期；已经过期或 ttl 非法时直接跳过 */
    public void block(String jti, Duration ttl) {
        if (jti == null || jti.isBlank() || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    /**
     * 判断 jti 是否已被拉黑。
     * Redis 不可用时不阻断请求（否则整个服务都不可用），只记日志，安全上相当于降级为“只校验签名与有效期”。
     */
    public boolean isBlocked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception e) {
            log.warn("JWT 黑名单查询失败，按未拉黑处理：{}", e.getMessage());
            return false;
        }
    }
}
