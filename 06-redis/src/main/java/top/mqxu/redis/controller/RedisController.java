package top.mqxu.redis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis 各数据类型的常用操作
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/redis")
@RequiredArgsConstructor
public class RedisController {

    private final StringRedisTemplate stringRedisTemplate;

    /** String：最基础的类型，文本、数字、序列化后的 JSON 都放它里面 */
    @PostMapping("/string")
    public Map<String, Object> setString(@RequestParam String key,
                                        @RequestParam String value,
                                        @RequestParam(defaultValue = "600") long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("value", value);
        result.put("ttlSeconds", stringRedisTemplate.getExpire(key));
        return result;
    }

    @GetMapping("/string")
    public String getString(@RequestParam String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /** 剩余存活时间，-1 表示没设过期，-2 表示 key 不存在 */
    @GetMapping("/ttl")
    public Map<String, Object> ttl(@RequestParam String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("ttlSeconds", stringRedisTemplate.getExpire(key));
        return result;
    }

    @DeleteMapping("/key")
    public boolean delete(@RequestParam String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }

    /** Hash：一个 key 下挂多个字段，适合只读写对象的某几个属性 */
    @PostMapping("/hash")
    public Map<Object, Object> putHash(@RequestParam String key, @RequestParam Map<String, String> fields) {
        // RequestParam 收到的 Map 里包含 key 这个参数本身，写进 hash 前先剔掉
        Map<String, String> values = new LinkedHashMap<>(fields);
        values.remove("key");
        stringRedisTemplate.opsForHash().putAll(key, values);
        return stringRedisTemplate.opsForHash().entries(key);
    }

    @GetMapping("/hash")
    public Map<Object, Object> getHash(@RequestParam String key) {
        return stringRedisTemplate.opsForHash().entries(key);
    }

    /** List：有序可重复，可以当队列或最新动态列表 */
    @PostMapping("/list")
    public List<String> pushList(@RequestParam String key, @RequestParam List<String> values) {
        stringRedisTemplate.opsForList().rightPushAll(key, values);
        return stringRedisTemplate.opsForList().range(key, 0, -1);
    }

    @GetMapping("/list")
    public List<String> getList(@RequestParam String key) {
        return stringRedisTemplate.opsForList().range(key, 0, -1);
    }

    /** Set：无序不重复，适合去重和求交集 */
    @PostMapping("/set")
    public Set<String> addSet(@RequestParam String key, @RequestParam List<String> values) {
        stringRedisTemplate.opsForSet().add(key, values.toArray(String[]::new));
        return stringRedisTemplate.opsForSet().members(key);
    }

    /** 求两个集合的交集，例如两个用户共同关注的商品 */
    @GetMapping("/set/intersect")
    public Set<String> intersect(@RequestParam String key1, @RequestParam String key2) {
        return stringRedisTemplate.opsForSet().intersect(key1, key2);
    }

    /** ZSet：元素带分数，写入后天然按分数排序 */
    @PostMapping("/zset")
    public Map<String, Object> addZSet(@RequestParam String key,
                                       @RequestParam String member,
                                       @RequestParam double score) {
        stringRedisTemplate.opsForZSet().add(key, member, score);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("member", member);
        result.put("score", score);
        result.put("size", stringRedisTemplate.opsForZSet().size(key));
        return result;
    }

    /** 取排行榜前若干名，分数从高到低 */
    @GetMapping("/zset/top")
    public List<Map<String, Object>> topZSet(@RequestParam String key,
                                             @RequestParam(defaultValue = "5") int top) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, top - 1L);
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
                .map(tuple -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("member", tuple.getValue());
                    item.put("score", tuple.getScore());
                    return item;
                })
                .toList();
    }

    /** 计数器：INCR 在 Redis 里是单线程原子的，适合统计访问量、做限流 */
    @PostMapping("/counter/incr")
    public Map<String, Object> increment(@RequestParam String key,
                                         @RequestParam(defaultValue = "1") long delta) {
        Long current = stringRedisTemplate.opsForValue().increment(key, delta);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("current", current);
        return result;
    }
}
