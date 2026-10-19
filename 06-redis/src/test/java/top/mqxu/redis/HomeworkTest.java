package top.mqxu.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import top.mqxu.redis.entity.Goods;
import top.mqxu.redis.mapper.GoodsMapper;
import top.mqxu.redis.service.GoodsService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 课后练习的参考实现，题目见 docs/06-redis.md 第 8.2 节
 *
 * @author mqxu
 */
@SpringBootTest
class HomeworkTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void reset() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
        }
        Set<String> keys = stringRedisTemplate.keys("homework:*");
        assertThat(keys).isNotNull();
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("练习 6：购物车用 Hash 存，加购与减购都是原子操作")
    void homework6ShoppingCart() {
        String key = "homework:cart:1001";

        stringRedisTemplate.opsForHash().increment(key, "1", 2);
        stringRedisTemplate.opsForHash().increment(key, "1", 1);
        stringRedisTemplate.opsForHash().increment(key, "2", 1);
        stringRedisTemplate.opsForHash().increment(key, "1", -3);

        Map<Object, Object> cart = stringRedisTemplate.opsForHash().entries(key);
        assertThat(cart).containsEntry("1", "0").containsEntry("2", "1");

        // 数量减到 0 的条目由业务代码删掉
        stringRedisTemplate.opsForHash().delete(key, "1");
        assertThat(stringRedisTemplate.opsForHash().entries(key)).hasSize(1);
    }

    @Test
    @DisplayName("练习 7：逻辑过期——过期后仍立刻返回旧值，只放一个线程去重建")
    void homework7LogicalExpire() {
        String key = "homework:logical:1";
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("data", "旧值");
        wrapper.put("expireAt", System.currentTimeMillis() - 1000);
        // 逻辑上已过期，物理上故意设得很长，让 key 不会真的消失
        redisTemplate.opsForValue().set(key, wrapper, Duration.ofMinutes(30));

        @SuppressWarnings("unchecked")
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(key);
        assertThat(cached).isNotNull();
        long expireAt = ((Number) cached.get("expireAt")).longValue();

        String returned = (String) cached.get("data");
        AtomicInteger rebuildCount = new AtomicInteger();

        if (expireAt < System.currentTimeMillis()) {
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key + ":rebuild", "1", Duration.ofSeconds(5));
            if (Boolean.TRUE.equals(locked)) {
                rebuildCount.incrementAndGet();
                // 真实场景在这里开异步线程查库并回写缓存
            }
        }

        assertThat(returned).isEqualTo("旧值");
        assertThat(rebuildCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("练习 8：按接口维度限流，并返回剩余可用次数")
    void homework8ApiLimitRemaining() {
        String api = "/orders";
        String user = "u1001";
        int windowSeconds = 60;
        int maxRequests = 3;
        long windowIndex = System.currentTimeMillis() / 1000 / windowSeconds;
        String key = "homework:limit:" + api + ":" + user + ":" + windowIndex;

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("count", count);
            item.put("remaining", Math.max(0L, maxRequests - count));
            item.put("allowed", count != null && count <= maxRequests);
            results.add(item);
        }

        assertThat(results.get(2)).containsEntry("remaining", 0L).containsEntry("allowed", true);
        assertThat(results.get(3)).containsEntry("remaining", 0L).containsEntry("allowed", false);
    }

    @Test
    @DisplayName("练习 9：先删缓存再更新库，中间进来的一次读会把旧值写回缓存")
    void homework9DeleteThenUpdateLeavesStaleCache() {
        String key = "homework:stale:1";
        Goods before = goodsService.getFromDb(1L);
        BigDecimal oldPrice = before.getPrice();

        // 1. 缓存里先放好旧值
        redisTemplate.opsForValue().set(key, before, Duration.ofMinutes(10));

        // 2. 先删缓存
        redisTemplate.delete(key);

        // 3. 并发场景下，另一个请求在删缓存之后、更新库之前读到旧值并写回缓存。
        //    这里为了结果可复现，把这一次读手工放在更新之前
        Goods readInWindow = goodsService.getFromDb(1L);
        redisTemplate.opsForValue().set(key, readInWindow, Duration.ofMinutes(10));

        // 4. 再更新库，此时缓存里留下的是旧值
        Goods update = new Goods();
        update.setId(1L);
        update.setPrice(oldPrice.add(new BigDecimal("10.00")));
        goodsMapper.updateById(update);

        Object cached = redisTemplate.opsForValue().get(key);
        assertThat(cached).isInstanceOf(Goods.class);
        assertThat(((Goods) cached).getPrice()).isEqualByComparingTo(oldPrice);
        assertThat(goodsService.getFromDb(1L).getPrice()).isEqualByComparingTo(oldPrice.add(new BigDecimal("10.00")));
    }
}
