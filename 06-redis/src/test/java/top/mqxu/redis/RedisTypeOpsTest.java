package top.mqxu.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 五种数据类型的接口测试，覆盖 /redis 下的全部端点
 *
 * <p>用例统一使用 test: 前缀的 key，@BeforeEach 里先清掉，避免残留数据影响断言，
 * 也避免动到演示用的 key。</p>
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class RedisTypeOpsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearKeys() {
        deleteKeys("test:*");
    }

    @Test
    @DisplayName("String：写入后能读到原值，TTL 按传入的秒数生效")
    void stringShouldBeReadableWithTtl() throws Exception {
        mockMvc.perform(post("/redis/string")
                        .param("key", "test:name")
                        .param("value", "mqxu")
                        .param("ttlSeconds", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("mqxu"))
                .andExpect(jsonPath("$.ttlSeconds").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/redis/string").param("key", "test:name"))
                .andExpect(status().isOk())
                .andExpect(content().string("mqxu"));
    }

    @Test
    @DisplayName("String：key 不存在时读回空串，TTL 返回 -2")
    void absentKeyShouldReportMinusTwo() throws Exception {
        mockMvc.perform(get("/redis/string").param("key", "test:absent"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mockMvc.perform(get("/redis/ttl").param("key", "test:absent"))
                .andExpect(jsonPath("$.ttlSeconds").value(-2));
    }

    @Test
    @DisplayName("Hash：多个字段写进同一个 key，key 参数本身不会变成字段")
    void hashShouldStoreFieldsButNotKeyParam() throws Exception {
        mockMvc.perform(post("/redis/hash")
                        .param("key", "test:user")
                        .param("name", "amy")
                        .param("city", "suzhou"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("amy"))
                .andExpect(jsonPath("$.city").value("suzhou"))
                .andExpect(jsonPath("$.key").doesNotExist());

        mockMvc.perform(get("/redis/hash").param("key", "test:user"))
                .andExpect(jsonPath("$.name").value("amy"));
    }

    @Test
    @DisplayName("List：按插入顺序返回，允许重复")
    void listShouldKeepInsertionOrder() throws Exception {
        mockMvc.perform(post("/redis/list")
                        .param("key", "test:news")
                        .param("values", "a", "b", "c"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0]").value("a"))
                .andExpect(jsonPath("$[2]").value("c"));

        mockMvc.perform(get("/redis/list").param("key", "test:news"))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("Set：重复成员只留一份")
    void setShouldDropDuplicates() throws Exception {
        mockMvc.perform(post("/redis/set")
                        .param("key", "test:like")
                        .param("values", "1", "1", "2", "3"))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("Set：两个集合求交集")
    void setIntersectShouldReturnCommonMembers() throws Exception {
        mockMvc.perform(post("/redis/set").param("key", "test:u1")
                .param("values", "1", "2", "3"));
        mockMvc.perform(post("/redis/set").param("key", "test:u2")
                .param("values", "2", "3", "4"));

        mockMvc.perform(get("/redis/set/intersect")
                        .param("key1", "test:u1")
                        .param("key2", "test:u2"))
                .andExpect(jsonPath("$", containsInAnyOrder("2", "3")));
    }

    @Test
    @DisplayName("ZSet：按分数从高到低取前几名")
    void zSetTopShouldBeOrderedByScoreDesc() throws Exception {
        mockMvc.perform(post("/redis/zset").param("key", "test:score").param("member", "alice").param("score", "88"));
        mockMvc.perform(post("/redis/zset").param("key", "test:score").param("member", "bob").param("score", "95"));
        mockMvc.perform(post("/redis/zset").param("key", "test:score").param("member", "cindy").param("score", "79"));

        mockMvc.perform(get("/redis/zset/top").param("key", "test:score").param("top", "3"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].member").value("bob"))
                .andExpect(jsonPath("$[0].score").value(95.0))
                .andExpect(jsonPath("$[2].member").value("cindy"));
    }

    @Test
    @DisplayName("计数器：INCR 支持指定步长，累加结果正确")
    void counterShouldAccumulate() throws Exception {
        mockMvc.perform(post("/redis/counter/incr").param("key", "test:pv").param("delta", "1"))
                .andExpect(jsonPath("$.current").value(1));

        mockMvc.perform(post("/redis/counter/incr").param("key", "test:pv").param("delta", "5"))
                .andExpect(jsonPath("$.current").value(6));
    }

    @Test
    @DisplayName("删除 key：存在的返回 true，不存在返回 false")
    void deleteShouldReportWhetherKeyExisted() throws Exception {
        mockMvc.perform(post("/redis/string").param("key", "test:tmp").param("value", "x"));

        mockMvc.perform(delete("/redis/key").param("key", "test:tmp"))
                .andExpect(jsonPath("$").value(true));

        mockMvc.perform(delete("/redis/key").param("key", "test:tmp"))
                .andExpect(jsonPath("$").value(false));
    }

    private void deleteKeys(String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        assertThat(keys).isNotNull();
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
