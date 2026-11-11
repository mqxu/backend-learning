package com.campus.activity.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 简单的 Redis 分布式锁：SET NX EX 抢锁，Lua 脚本比对持有者后释放。
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * String token = redisLock.tryLock(key, 120);
 * if (token == null) {
 *     return; // 其他实例正在执行，直接跳过
 * }
 * try {
 *     // 业务逻辑
 * } finally {
 *     redisLock.unlock(key, token);
 * }
 * }</pre>
 *
 * <p>锁的值用随机 UUID 而不是固定值，释放时先比对再删除，避免误删别人的锁；
 * 同时 EX 保证进程崩溃后锁能自动过期，不会永久卡住任务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    /** 释放锁：只有当前持有者（value 等于自己的 token）才能删除 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试抢锁，不等待（抢不到立即返回 null，适合定时任务“多实例只跑一个”的场景）。
     *
     * @param key          锁 key
     * @param leaseSeconds 锁自动释放时间，必须大于任务最长执行时间
     * @return 持有者 token；null 表示没抢到
     */
    public String tryLock(String key, long leaseSeconds) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, token, Duration.ofSeconds(leaseSeconds));
        if (Boolean.TRUE.equals(acquired)) {
            log.debug("获取 Redis 锁成功: {}", key);
            return token;
        }
        return null;
    }

    /**
     * 释放锁：比对 token 后再删除，避免锁超时后误删其他实例的锁。
     *
     * @param key   锁 key
     * @param token 抢锁时返回的 token
     */
    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
            log.debug("释放 Redis 锁: {}", key);
        } catch (Exception e) {
            // 释放锁失败不应影响业务结果，锁会在租期结束后自动过期
            log.warn("释放 Redis 锁失败: {}, 原因: {}", key, e.getMessage());
        }
    }
}
