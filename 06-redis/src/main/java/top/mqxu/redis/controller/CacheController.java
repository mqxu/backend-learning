package top.mqxu.redis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.redis.dto.CacheResult;
import top.mqxu.redis.entity.Goods;
import top.mqxu.redis.service.GoodsCacheService;
import top.mqxu.redis.service.GoodsService;
import top.mqxu.redis.service.GoodsSpringCacheService;

import java.math.BigDecimal;
import java.util.List;

/**
 * 缓存的几种做法对照：直查库、手动维护缓存、注解声明式缓存
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/cache/goods")
@RequiredArgsConstructor
public class CacheController {

    private final GoodsService goodsService;
    private final GoodsCacheService goodsCacheService;
    private final GoodsSpringCacheService springCacheService;

    /** 直接查数据库，每次请求都会打库 */
    @GetMapping("/db/{id}")
    public CacheResult<Goods> fromDb(@PathVariable Long id) {
        long start = System.nanoTime();
        Goods goods = goodsService.getFromDb(id);
        return new CacheResult<>(goods, "db", costMs(start));
    }

    /** 手动维护缓存：先查缓存，未命中查库并回填，查不到写空值标记 */
    @GetMapping("/manual/{id}")
    public CacheResult<Goods> manual(@PathVariable Long id) {
        return goodsCacheService.getById(id);
    }

    /** 手动维护缓存，并在未命中时用互斥锁只放一个请求去回源 */
    @GetMapping("/manual-mutex/{id}")
    public CacheResult<Goods> manualWithMutex(@PathVariable Long id) {
        return goodsCacheService.getByIdWithMutex(id);
    }

    /** 注解缓存：先按 id 查询，再对比第一次与第二次的响应耗时 */
    @GetMapping("/anno/{id}")
    public CacheResult<Goods> annotation(@PathVariable Long id) {
        long start = System.nanoTime();
        Goods goods = springCacheService.getById(id);
        return new CacheResult<>(goods, "anno（是否命中看日志）", costMs(start));
    }

    /** 注解缓存列表 */
    @GetMapping("/anno/list")
    public CacheResult<List<Goods>> annotationList() {
        long start = System.nanoTime();
        List<Goods> list = springCacheService.listAll();
        return new CacheResult<>(list, "anno（是否命中看日志）", costMs(start));
    }

    /** 改价：更新数据库后删缓存，下次读取自然回源 */
    @PutMapping("/{id}/price")
    public CacheResult<Goods> updatePrice(@PathVariable Long id, @RequestParam BigDecimal price) {
        goodsCacheService.updatePrice(id, price);
        return goodsCacheService.getById(id);
    }

    /** 改价：注解版，@CachePut 会把新值直接写回缓存 */
    @PutMapping("/{id}/price-anno")
    public CacheResult<Goods> updatePriceByAnnotation(@PathVariable Long id, @RequestParam BigDecimal price) {
        long start = System.nanoTime();
        Goods goods = springCacheService.updatePrice(id, price);
        return new CacheResult<>(goods, "anno-put", costMs(start));
    }

    /** 只删缓存，不动数据库，用来观察下一次查询是否重新打库 */
    @DeleteMapping("/manual/{id}/cache")
    public boolean evict(@PathVariable Long id) {
        return goodsCacheService.evict(id);
    }

    /** 删注解缓存里的单个商品，连带列表缓存一起清掉 */
    @DeleteMapping("/anno/{id}")
    public String evictAnno(@PathVariable Long id) {
        springCacheService.evictDetailAndList(id);
        return "已清掉 goods 区域下 id=" + id + " 与 all 两处缓存";
    }

    /** 清空注解缓存区域 */
    @DeleteMapping("/anno")
    public String evictAllAnno() {
        springCacheService.evictAll();
        return "已清空 goods 缓存区域";
    }

    private double costMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }
}
