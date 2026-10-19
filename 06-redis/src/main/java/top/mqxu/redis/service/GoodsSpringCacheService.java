package top.mqxu.redis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import top.mqxu.redis.entity.Goods;
import top.mqxu.redis.mapper.GoodsMapper;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用 Spring 缓存注解维护缓存，方法体只写数据库逻辑
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class GoodsSpringCacheService {

    private final GoodsMapper goodsMapper;

    /**
     * 命中缓存时方法体不会执行。
     * 查不到数据会返回 null，配合 spring.cache.redis.cache-null-values=true 把 null 也存进缓存，
     * 不需要额外写空值标记就能起到防穿透的作用。
     */
    @Cacheable(cacheNames = "goods", key = "#id")
    public Goods getById(Long id) {
        return goodsMapper.selectById(id);
    }

    /** 查列表，结果放在 goods 区域下固定的 all 键上 */
    @Cacheable(cacheNames = "goods", key = "'all'")
    public List<Goods> listAll() {
        return goodsMapper.selectList(null);
    }

    /** 更新数据库，并把新值写回缓存 */
    @CachePut(cacheNames = "goods", key = "#id")
    public Goods updatePrice(Long id, BigDecimal price) {
        Goods update = new Goods();
        update.setId(id);
        update.setPrice(price);
        if (goodsMapper.updateById(update) == 0) {
            return null;
        }
        return goodsMapper.selectById(id);
    }

    /** 更新后让缓存失效，下次查询重新回源 */
    @CacheEvict(cacheNames = "goods", key = "#id")
    public void evict(Long id) {
        // 只负责清缓存，没有业务逻辑
    }

    /** 同时清掉单个商品与列表两处缓存 */
    @Caching(evict = {
            @CacheEvict(cacheNames = "goods", key = "#id"),
            @CacheEvict(cacheNames = "goods", key = "'all'")
    })
    public void evictDetailAndList(Long id) {
        // 改动了商品数据，明细与列表都要失效
    }

    /** 清空整个 goods 缓存区域 */
    @CacheEvict(cacheNames = "goods", allEntries = true)
    public void evictAll() {
        // 只负责清缓存
    }
}
