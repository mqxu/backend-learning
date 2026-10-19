package top.mqxu.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import top.mqxu.redis.dto.CacheResult;
import top.mqxu.redis.entity.Goods;
import top.mqxu.redis.service.GoodsCacheService;
import top.mqxu.redis.service.GoodsService;
import top.mqxu.redis.service.GoodsSpringCacheService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 缓存的三种做法对照测试：直查库、手动维护、注解声明式
 *
 * <p>每个用例前都重跑一遍建表与初始化脚本，让 t_goods 回到初始状态，
 * 再清掉本模块用到的缓存 key，保证断言只受当前用例影响。</p>
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class GoodsCacheTest {

    private static final String DETAIL_KEY = "goods:detail:";
    private static final String ANNO_PREFIX = "cache:goods::";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private GoodsCacheService goodsCacheService;

    @Autowired
    private GoodsSpringCacheService springCacheService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void reset() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
        }
        deleteKeys("goods:detail:*");
        deleteKeys("cache:goods*");
        deleteKeys("lock:goods:rebuild:*");
    }

    @Test
    @DisplayName("手动缓存：第一次回源数据库，第二次命中缓存")
    void manualCacheShouldHitOnSecondRead() {
        CacheResult<Goods> first = goodsCacheService.getById(1L);
        CacheResult<Goods> second = goodsCacheService.getById(1L);

        assertThat(first.getSource()).isEqualTo("db");
        assertThat(second.getSource()).isEqualTo("cache");
        assertThat(second.getData().getId()).isEqualTo(1L);
        assertThat(second.getData().getName()).isEqualTo("机械键盘 K87");
    }

    @Test
    @DisplayName("缓存里存的是带类型信息的 JSON，读回来能直接还原成 Goods")
    void cachedValueShouldDeserializeBackToEntity() {
        goodsCacheService.getById(1L);

        Object cached = redisTemplate.opsForValue().get(DETAIL_KEY + 1);
        assertThat(cached).isInstanceOf(Goods.class);
        assertThat(((Goods) cached).getPrice()).isEqualByComparingTo(new BigDecimal("399.00"));
    }

    @Test
    @DisplayName("防穿透：查不存在的 id 写入空值标记，第二次不再回源")
    void missingIdShouldBeCachedAsEmptyMarker() {
        CacheResult<Goods> first = goodsCacheService.getById(9999L);
        CacheResult<Goods> second = goodsCacheService.getById(9999L);

        assertThat(first.getSource()).isEqualTo("db");
        assertThat(first.getData()).isNull();
        assertThat(second.getSource()).isEqualTo("cache-empty");
        assertThat(second.getData()).isNull();
    }

    @Test
    @DisplayName("防雪崩：空值标记的存活时间短于正常缓存")
    void emptyMarkerShouldHaveShorterTtl() {
        goodsCacheService.getById(2L);
        goodsCacheService.getById(9999L);

        Long normalTtl = redisTemplate.getExpire(DETAIL_KEY + 2);
        Long emptyTtl = redisTemplate.getExpire(DETAIL_KEY + 9999);

        assertThat(normalTtl).isNotNull().isGreaterThan(600L);
        assertThat(emptyTtl).isNotNull().isPositive().isLessThanOrEqualTo(120L);
    }

    @Test
    @DisplayName("防击穿：互斥锁版同样会回填缓存，且用完把锁释放掉")
    void mutexVersionShouldCacheAndReleaseLock() {
        CacheResult<Goods> first = goodsCacheService.getByIdWithMutex(3L);
        CacheResult<Goods> second = goodsCacheService.getByIdWithMutex(3L);

        assertThat(first.getSource()).isEqualTo("db");
        assertThat(second.getSource()).isEqualTo("cache");
        assertThat(redisTemplate.hasKey("lock:goods:rebuild:3")).isFalse();
    }

    @Test
    @DisplayName("改价格：更新数据库后删掉缓存，下次读取重新回源")
    void updatePriceShouldEvictCache() {
        goodsCacheService.getById(1L);
        assertThat(redisTemplate.hasKey(DETAIL_KEY + 1)).isTrue();

        boolean updated = goodsCacheService.updatePrice(1L, new BigDecimal("299.00"));

        assertThat(updated).isTrue();
        assertThat(redisTemplate.hasKey(DETAIL_KEY + 1)).isFalse();
        assertThat(goodsService.getFromDb(1L).getPrice()).isEqualByComparingTo(new BigDecimal("299.00"));
    }

    @Test
    @DisplayName("注解缓存：第一次执行方法体并写缓存，第二次直接命中")
    void annotationCacheShouldHitOnSecondRead() {
        springCacheService.getById(4L);
        assertThat(redisTemplate.hasKey(ANNO_PREFIX + 4)).isTrue();

        Goods cached = springCacheService.getById(4L);
        assertThat(cached.getId()).isEqualTo(4L);
        assertThat(cached.getName()).isEqualTo("降噪耳机 X5");
    }

    @Test
    @DisplayName("注解缓存：cache-null-values 打开后，查不到的 id 也会被缓存")
    void annotationCacheShouldStoreNullValue() {
        assertThat(springCacheService.getById(88888L)).isNull();
        assertThat(redisTemplate.hasKey(ANNO_PREFIX + 88888)).isTrue();

        // 空值由 Spring 自己写入，格式与普通值不同，这里只关心第二次调用仍然返回 null
        assertThat(springCacheService.getById(88888L)).isNull();
    }

    @Test
    @DisplayName("@CachePut：改价后新值直接覆盖缓存，不需要先删")
    void cachePutShouldOverwriteCachedValue() {
        springCacheService.getById(5L);

        Goods updated = springCacheService.updatePrice(5L, new BigDecimal("199.00"));

        assertThat(updated.getPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
        Object cached = redisTemplate.opsForValue().get(ANNO_PREFIX + 5);
        assertThat(cached).isInstanceOf(Goods.class);
        assertThat(((Goods) cached).getPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
    }

    @Test
    @DisplayName("列表缓存单独占一个 key，@Caching 能同时清掉明细与列表")
    void listCacheShouldBeEvictedTogetherWithDetail() {
        springCacheService.getById(1L);
        springCacheService.listAll();
        assertThat(redisTemplate.hasKey(ANNO_PREFIX + "all")).isTrue();

        springCacheService.evictDetailAndList(1L);

        assertThat(redisTemplate.hasKey(ANNO_PREFIX + 1)).isFalse();
        assertThat(redisTemplate.hasKey(ANNO_PREFIX + "all")).isFalse();
    }

    @Test
    @DisplayName("@CacheEvict(allEntries = true)：清空整个 goods 区域")
    void evictAllShouldClearWholeRegion() {
        springCacheService.getById(1L);
        springCacheService.listAll();
        assertThat(keys(ANNO_PREFIX + "*")).isNotEmpty();

        springCacheService.evictAll();

        assertThat(keys(ANNO_PREFIX + "*")).isEmpty();
    }

    @Test
    @DisplayName("改价接口：返回的是回源后的最新值")
    void updatePriceEndpointShouldReturnFreshValue() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/cache/goods/6/price").param("price", "59.90"))
                .andExpect(jsonPath("$.data.price").value(59.90))
                .andExpect(jsonPath("$.data.id").value(6));
    }

    @Test
    @DisplayName("查询接口：直查库入口每次都标记为 db，手动缓存入口第二次标记为 cache")
    void cacheEndpointsShouldReportSource() throws Exception {
        mockMvc.perform(get("/cache/goods/db/1"))
                .andExpect(jsonPath("$.source").value("db"))
                .andExpect(jsonPath("$.data.name").value("机械键盘 K87"));

        mockMvc.perform(get("/cache/goods/manual/1"))
                .andExpect(jsonPath("$.source").value("db"));
        mockMvc.perform(get("/cache/goods/manual/1"))
                .andExpect(jsonPath("$.source").value("cache"));
    }

    private Set<String> keys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        assertThat(keys).isNotNull();
        return keys;
    }

    private void deleteKeys(String pattern) {
        Set<String> keys = keys(pattern);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
