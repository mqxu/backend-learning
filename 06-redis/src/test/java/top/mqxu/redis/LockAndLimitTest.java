package top.mqxu.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import top.mqxu.redis.entity.Goods;
import top.mqxu.redis.service.GoodsService;
import top.mqxu.redis.service.StockService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 并发扣库存与限流的验证
 *
 * <p>扣库存的两条用例都用 20 个线程同时发起，用 CountDownLatch 保证它们尽量在同一时刻进入，
 * 否则单线程顺序执行是看不出区别的。</p>
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class LockAndLimitTest {

    private static final int THREADS = 20;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StockService stockService;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void reset() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
        }
        deleteKeys("rank:*");
        deleteKeys("lock:goods:*");
        deleteKeys("limit:access:*");
    }

    @Test
    @DisplayName("不加锁：20 个请求都返回成功，库存却没有相应减少")
    void deductWithoutLockShouldLoseUpdates() throws InterruptedException {
        int initialStock = 20;
        stockService.resetStock(1L, initialStock);

        int success = runConcurrently(() -> stockService.deductWithDelayedWindow(1L, 1));
        int left = goodsService.getFromDb(1L).getStock();

        assertThat(success).isEqualTo(THREADS);
        assertThat(initialStock - left).isLessThan(THREADS);
        assertThat(left).isNotNegative();
    }

    @Test
    @DisplayName("Redisson 锁：库存 10 时 20 个请求只有 10 个成功，库存减到 0 不超卖")
    void deductWithRedissonLockShouldNotOversell() throws InterruptedException {
        stockService.resetStock(1L, 10);

        int success = runConcurrently(() -> stockService.deductWithLock(1L, 1));
        int left = goodsService.getFromDb(1L).getStock();

        assertThat(success).isEqualTo(10);
        assertThat(left).isZero();
    }

    @Test
    @DisplayName("Redisson 锁用完即释放，不会遗留锁 key")
    void redissonLockShouldBeReleased() throws InterruptedException {
        stockService.resetStock(2L, 5);

        runConcurrently(() -> stockService.deductWithLock(2L, 1));

        assertThat(redissonClient.getLock("lock:goods:stock:2").isLocked()).isFalse();
    }

    @Test
    @DisplayName("手写 SETNX 锁：锁被占住时请求直接返回失败，不做等待")
    void setnxLockShouldRejectWhenBusy() throws Exception {
        stringRedisTemplate.opsForValue()
                .set("lock:goods:buy:3", "someone-else", Duration.ofSeconds(30));

        mockMvc.perform(post("/lock/buy/setnx").param("id", "3").param("quantity", "1"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.reason").value("没抢到锁，已有请求在处理"));

        stringRedisTemplate.delete("lock:goods:buy:3");

        mockMvc.perform(post("/lock/buy/setnx").param("id", "3").param("quantity", "1"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.strategy").value("setnx"));

        assertThat(stringRedisTemplate.hasKey("lock:goods:buy:3")).isFalse();
    }

    @Test
    @DisplayName("重置与查看库存接口")
    void resetAndQueryStock() throws Exception {
        mockMvc.perform(post("/lock/reset").param("id", "4").param("stock", "66"))
                .andExpect(jsonPath("$.stock").value(66));

        mockMvc.perform(get("/lock/stock/4"))
                .andExpect(jsonPath("$.stock").value(66));
    }

    @Test
    @DisplayName("限流：窗口内前 5 次放行，第 6 次开始拒绝")
    void limitShouldAllowMaxRequestsThenReject() throws Exception {
        String user = "test-limit-user";

        for (int i = 1; i <= 5; i++) {
            int expected = i;
            mockMvc.perform(get("/limit/access").param("user", user))
                    .andExpect(jsonPath("$.count").value(expected))
                    .andExpect(jsonPath("$.allowed").value(true));
        }

        mockMvc.perform(get("/limit/access").param("user", user))
                .andExpect(jsonPath("$.count").value(6))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.maxRequests").value(5));
    }

    @Test
    @DisplayName("限流：计数器在第一个请求时设置过期时间，过期时间等于窗口长度")
    void limitCounterShouldExpireWithWindow() throws Exception {
        mockMvc.perform(get("/limit/access").param("user", "test-ttl-user"))
                .andExpect(jsonPath("$.ttlSeconds").value(60));
    }

    @Test
    @DisplayName("排行榜：预热把销量灌进 ZSet，加分后名次自动变化")
    void rankShouldFollowScoreChanges() throws Exception {
        mockMvc.perform(post("/rank/warmup"))
                .andExpect(jsonPath("$.warmed").value(10))
                .andExpect(jsonPath("$.size").value(10));

        mockMvc.perform(get("/rank/top").param("top", "1"))
                .andExpect(jsonPath("$[0].goodsId").value("6"))
                .andExpect(jsonPath("$[0].name").value("不锈钢保温杯"));

        mockMvc.perform(post("/rank/increase").param("id", "7").param("delta", "5000"))
                .andExpect(jsonPath("$.score").value(5275.0))
                .andExpect(jsonPath("$.rank").value(1));

        mockMvc.perform(get("/rank/top").param("top", "1"))
                .andExpect(jsonPath("$[0].goodsId").value("7"));
    }

    @Test
    @DisplayName("排行榜：查未上榜商品的排名返回 -1")
    void rankOfUnknownGoodsShouldBeMinusOne() throws Exception {
        mockMvc.perform(get("/rank/rank-of").param("id", "9999"))
                .andExpect(jsonPath("$.rank").value(-1));
    }

    @Test
    @DisplayName("加锁扣库存后库里的值确实是减完的结果")
    void lockedDeductShouldPersistCorrectStock() throws Exception {
        stockService.resetStock(5L, 8);

        boolean success = stockService.deductWithLock(5L, 3);

        assertThat(success).isTrue();
        Goods goods = goodsService.getFromDb(5L);
        assertThat(goods.getStock()).isEqualTo(5);
    }

    /**
     * 让 THREADS 个线程尽量在同一时刻执行同一个动作，返回其中成功（true）的个数
     */
    private int runConcurrently(Supplier<Boolean> action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return action.get();
                }));
            }
            start.countDown();

            int success = 0;
            for (Future<Boolean> future : futures) {
                if (Boolean.TRUE.equals(future.get(30, TimeUnit.SECONDS))) {
                    success++;
                }
            }
            return success;
        } catch (Exception e) {
            throw new IllegalStateException("并发任务执行异常", e);
        } finally {
            pool.shutdownNow();
        }
    }

    private void deleteKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        assertThat(keys).isNotNull();
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
