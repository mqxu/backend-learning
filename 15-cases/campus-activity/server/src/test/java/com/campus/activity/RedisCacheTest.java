package com.campus.activity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注解缓存测试：分类接口第一次查库并写 Redis，第二次直接命中缓存。
 *
 * <p>注意：测试会真实连接本地 Redis（database 11），
 * 断言完成后删除缓存 key，避免影响其他用例。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class RedisCacheTest {

    /** RedisConfig 中 cacheManager 的前缀 campus:cache: + cacheName + "::" + key */
    private static final String CATEGORIES_CACHE_KEY = "campus:cache:categories::all";

    /** 热门活动缓存 key，limit=5 对应 @Cacheable(key = "#limit") */
    private static final String HOT_CACHE_KEY = "campus:cache:activity-hot::5";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void cleanCache() {
        stringRedisTemplate.delete(List.of(CATEGORIES_CACHE_KEY, HOT_CACHE_KEY));
    }

    @Test
    @DisplayName("分类接口连续请求两次后 Redis 中出现 campus:cache:categories::all")
    void categoriesAreCachedInRedis() throws Exception {
        stringRedisTemplate.delete(CATEGORIES_CACHE_KEY);

        // 第一次查库并写缓存，第二次应命中缓存；两次响应必须一致
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/app/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data[0].name").value("文体活动"))
                    // 缓存中的值反序列化后字段仍然完整
                    .andExpect(jsonPath("$.data.length()").value(6));
        }

        assertThat(stringRedisTemplate.hasKey(CATEGORIES_CACHE_KEY))
                .as("分类查询后应写入 Redis 注解缓存")
                .isTrue();

        Long ttl = stringRedisTemplate.getExpire(CATEGORIES_CACHE_KEY, TimeUnit.SECONDS);
        assertThat(ttl).as("缓存应带 TTL").isNotNull().isPositive().isLessThanOrEqualTo(600L);
    }

    @Test
    @DisplayName("热门活动接口连续请求两次后命中 activity-hot 缓存")
    void hotActivitiesAreCachedInRedis() throws Exception {
        stringRedisTemplate.delete(HOT_CACHE_KEY);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/app/activities/hot").param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    // 时间字段是 String，缓存反序列化后仍能正常输出
                    .andExpect(jsonPath("$.data[0].startTime").isNotEmpty());
        }

        assertThat(stringRedisTemplate.hasKey(HOT_CACHE_KEY))
                .as("热门活动查询后应写入 activity-hot 缓存")
                .isTrue();
    }
}
