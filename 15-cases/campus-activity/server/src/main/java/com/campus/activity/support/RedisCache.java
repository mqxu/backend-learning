package com.campus.activity.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 基于 {@link StringRedisTemplate} 的 Redis 操作小工具。
 *
 * <p>短信验证码、发送间隔、错误计数、分布式锁都用字符串，
 * 用 StringRedisTemplate 比 JSON 序列化更直观，redis-cli 里也能直接读懂。</p>
 */
@Component
@RequiredArgsConstructor
public class RedisCache {

    private final StringRedisTemplate stringRedisTemplate;

    /** 写入字符串并带上过期时间（秒） */
    public void set(String key, String value, long expireSeconds) {
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(expireSeconds));
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 不存在才写入（SET key value NX EX expire），返回是否写入成功。
     * 短信发送间隔、分布式锁都依赖这个原子操作。
     */
    public boolean setIfAbsent(String key, String value, long expireSeconds) {
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(expireSeconds));
        return Boolean.TRUE.equals(success);
    }

    /**
     * 自增计数，并在第一次自增时设置过期时间，用于固定窗口计数（如当日发送次数）。
     *
     * @return 自增后的值；Redis 异常时返回 null
     */
    public Long increment(String key, long expireSeconds) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            expire(key, expireSeconds);
        }
        return value;
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    public void expire(String key, long expireSeconds) {
        stringRedisTemplate.expire(key, Duration.ofSeconds(expireSeconds));
    }

    /** 写 Hash 单个字段，并刷新整个 Hash 的过期时间 */
    public void hSet(String key, String field, String value, long expireSeconds) {
        stringRedisTemplate.opsForHash().put(key, field, value);
        expire(key, expireSeconds);
    }

    /** 写整个 Hash 并设置过期时间 */
    public void hMSet(String key, Map<String, String> values, long expireSeconds) {
        stringRedisTemplate.opsForHash().putAll(key, values);
        expire(key, expireSeconds);
    }

    public String hGet(String key, String field) {
        Object value = stringRedisTemplate.opsForHash().get(key, field);
        return value == null ? null : String.valueOf(value);
    }

    /** 取整个 Hash，空 Hash 返回空 Map */
    public Map<String, String> hGetAll(String key) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        return entries.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> String.valueOf(entry.getValue())));
    }

    public void hDel(String key, String... fields) {
        stringRedisTemplate.opsForHash().delete(key, (Object[]) fields);
    }
}
