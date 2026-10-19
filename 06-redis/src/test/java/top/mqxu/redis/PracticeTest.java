package top.mqxu.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 随堂练习的参考实现，题目见 docs/06-redis.md 第 8.1 节
 *
 * <p>用例只使用 practice: 前缀的 key，每条用例结束就删掉，不与演示数据混在一起。</p>
 *
 * @author mqxu
 */
@SpringBootTest
class PracticeTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void clearKeys() {
        Set<String> keys = stringRedisTemplate.keys("practice:*");
        assertThat(keys).isNotNull();
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("练习 1：用 Hash 只改一个字段，其余字段保持不变")
    void practice1UpdateSingleHashField() {
        String key = "practice:goods:hash:1";
        stringRedisTemplate.opsForHash().putAll(key, Map.of(
                "name", "机械键盘 K87", "category", "数码", "price", "399.00"));

        stringRedisTemplate.opsForHash().put(key, "price", "359.00");

        Map<Object, Object> fields = stringRedisTemplate.opsForHash().entries(key);
        assertThat(fields).containsEntry("price", "359.00")
                .containsEntry("name", "机械键盘 K87")
                .containsEntry("category", "数码");
    }

    @Test
    @DisplayName("练习 2：Set 求交集，得到共同关注的商品")
    void practice2IntersectLikes() {
        stringRedisTemplate.opsForSet().add("practice:like:u1", "1", "2", "3");
        stringRedisTemplate.opsForSet().add("practice:like:u2", "2", "3", "4");

        Set<String> common = stringRedisTemplate.opsForSet()
                .intersect("practice:like:u1", "practice:like:u2");

        assertThat(common).containsExactlyInAnyOrder("2", "3");
    }

    @Test
    @DisplayName("练习 3：TTL 抖动后，多个 key 的剩余时间不会完全一致")
    void practice3TtlJitter() {
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String key = "practice:jitter:" + i;
            long ttlSeconds = Duration.ofMinutes(10).toSeconds()
                    + ThreadLocalRandom.current().nextLong(31);
            redisTemplate.opsForValue().set(key, "v", Duration.ofSeconds(ttlSeconds));
            keys.add(key);
        }

        Set<Long> ttls = keys.stream()
                .map(redisTemplate::getExpire)
                .collect(Collectors.toSet());

        // 10 个 key 的基础 TTL 相同，抖动之后剩余时间至少出现两种取值
        assertThat(ttls).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("练习 4：SETNX 做提交去重，5 秒内第二次被挡住")
    void practice4SubmitOnce() {
        String key = "practice:submit:1001";

        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(5));
        Boolean second = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(5));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(stringRedisTemplate.getExpire(key)).isPositive();
    }

    @Test
    @DisplayName("练习 5：取排行榜上某人前后各一名")
    void practice5RankNeighbours() {
        String key = "practice:rank";
        stringRedisTemplate.opsForZSet().add(key, "a", 10);
        stringRedisTemplate.opsForZSet().add(key, "b", 20);
        stringRedisTemplate.opsForZSet().add(key, "c", 30);
        stringRedisTemplate.opsForZSet().add(key, "d", 40);
        stringRedisTemplate.opsForZSet().add(key, "e", 50);

        // 分数从高到低：e(0) d(1) c(2) b(3) a(4)
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(key, "c");
        assertThat(rank).isEqualTo(2L);

        long from = Math.max(0L, rank - 1);
        long to = rank + 1;
        Set<String> neighbours = stringRedisTemplate.opsForZSet().reverseRange(key, from, to);

        assertThat(neighbours).containsExactly("d", "c", "b");
    }
}
