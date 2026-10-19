package top.mqxu.redis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.redis.config.DemoProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用计数器做接口限流
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/limit")
@RequiredArgsConstructor
public class LimitController {

    private final StringRedisTemplate stringRedisTemplate;
    private final DemoProperties properties;

    /**
     * 固定窗口限流：把时间按窗口长度切成一段一段，每段一个计数器。
     * key 里带上窗口编号，过期时间设成窗口长度，窗口过去后计数器自己就没了。
     */
    @GetMapping("/access")
    public Map<String, Object> access(@RequestParam(defaultValue = "guest") String user) {
        int windowSeconds = properties.getLimit().getWindowSeconds();
        int maxRequests = properties.getLimit().getMaxRequests();

        long windowIndex = System.currentTimeMillis() / 1000 / windowSeconds;
        String key = "limit:access:" + user + ":" + windowIndex;

        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // 只在计数器刚建出来时设过期，之后每次自增不再刷新，保证窗口按自然时间滚动
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", user);
        result.put("count", count);
        result.put("maxRequests", maxRequests);
        result.put("windowSeconds", windowSeconds);
        result.put("allowed", count != null && count <= maxRequests);
        result.put("ttlSeconds", stringRedisTemplate.getExpire(key));
        return result;
    }
}
