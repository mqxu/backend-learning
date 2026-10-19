package top.mqxu.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.mqxu.redis.config.DemoProperties;
import top.mqxu.redis.dto.CacheResult;
import top.mqxu.redis.entity.Goods;
import top.mqxu.redis.mapper.GoodsMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 手动维护缓存，即 Cache-Aside 模式：读的时候先看缓存，写的时候更新库再删缓存
 *
 * @author mqxu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsCacheService {

    /** 业务 key 统一带模块前缀，便于按前缀查找与批量清理 */
    private static final String KEY_PREFIX = "goods:detail:";

    /** 数据不存在时写进缓存的占位标记，让下一次查询命中缓存而不是继续打库 */
    private static final String NULL_PLACEHOLDER = "NULL";

    /** 重建缓存时用的互斥锁 */
    private static final String REBUILD_LOCK_PREFIX = "lock:goods:rebuild:";

    private final GoodsMapper goodsMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final DemoProperties properties;

    /**
     * 读商品：先查缓存，没有再查库并回填。
     * 查不到数据时写入空值标记，避免同一个不存在的 id 反复打库。
     */
    public CacheResult<Goods> getById(Long id) {
        long start = System.nanoTime();
        String key = KEY_PREFIX + id;

        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return fromCache(cached, start);
        }

        Goods goods = goodsMapper.selectById(id);
        cacheIt(key, goods);
        return new CacheResult<>(goods, "db", costMs(start));
    }

    /**
     * 热点 key 失效时的处理：未命中先用 SETNX 抢锁，只放一个请求去回源，
     * 其余请求短暂等待后重读缓存，避免同一时刻全部压到数据库上。
     */
    public CacheResult<Goods> getByIdWithMutex(Long id) {
        long start = System.nanoTime();
        String key = KEY_PREFIX + id;

        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return fromCache(cached, start);
        }

        String lockKey = REBUILD_LOCK_PREFIX + id;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
        if (!Boolean.TRUE.equals(locked)) {
            // 没抢到锁，说明有别的请求正在重建，等一小会儿再读缓存
            sleepQuietly(80);
            Object retried = redisTemplate.opsForValue().get(key);
            if (retried != null) {
                return fromCache(retried, start);
            }
            return new CacheResult<>(goodsMapper.selectById(id), "db-fallback", costMs(start));
        }

        try {
            // 拿到锁后复查一次：可能上一个持锁线程刚好写完缓存
            Object checked = redisTemplate.opsForValue().get(key);
            if (checked != null) {
                return fromCache(checked, start);
            }
            Goods goods = goodsMapper.selectById(id);
            cacheIt(key, goods);
            return new CacheResult<>(goods, "db", costMs(start));
        } finally {
            // 这里的锁只服务于重建，简单删除即可；对外部业务的锁要求唯一值比对后再删，见 LockController
            stringRedisTemplate.delete(lockKey);
        }
    }

    /** 改价格：先更新数据库，成功后再删缓存，下次读取自然回源 */
    public boolean updatePrice(Long id, BigDecimal price) {
        Goods goods = new Goods();
        goods.setId(id);
        goods.setPrice(price);
        boolean updated = goodsMapper.updateById(goods) > 0;
        if (updated) {
            evict(id);
        }
        return updated;
    }

    /** 删掉某个商品的缓存 */
    public boolean evict(Long id) {
        return Boolean.TRUE.equals(redisTemplate.delete(KEY_PREFIX + id));
    }

    private CacheResult<Goods> fromCache(Object cached, long start) {
        if (NULL_PLACEHOLDER.equals(cached)) {
            return new CacheResult<>(null, "cache-empty", costMs(start));
        }
        if (cached instanceof Goods goods) {
            return new CacheResult<>(goods, "cache", costMs(start));
        }
        log.warn("缓存里的值类型不是 Goods，实际是 {}", cached.getClass());
        return new CacheResult<>(null, "cache-invalid", costMs(start));
    }

    private void cacheIt(String key, Goods goods) {
        if (goods == null) {
            redisTemplate.opsForValue().set(key, NULL_PLACEHOLDER, properties.getCache().getNullTtl());
            return;
        }
        redisTemplate.opsForValue().set(key, goods, ttlWithJitter());
    }

    /**
     * 在基础 TTL 上加一个随机秒数。
     * 大批 key 用同一个过期时间，一旦同时失效就会一起打库，这就是缓存雪崩的成因。
     */
    private Duration ttlWithJitter() {
        long base = properties.getCache().getTtl().toSeconds();
        long jitter = ThreadLocalRandom.current().nextLong(properties.getCache().getJitterSeconds() + 1L);
        return Duration.ofSeconds(base + jitter);
    }

    private double costMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
