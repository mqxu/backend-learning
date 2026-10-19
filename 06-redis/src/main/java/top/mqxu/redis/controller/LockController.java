package top.mqxu.redis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.redis.entity.Goods;
import top.mqxu.redis.service.GoodsService;
import top.mqxu.redis.service.StockService;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 分布式锁的三种写法对照
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/lock")
@RequiredArgsConstructor
public class LockController {

    private static final String SETNX_LOCK_PREFIX = "lock:goods:buy:";

    /** 释放锁时先比对持有者标记，值对得上才删，避免误删别人持有的锁 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StockService stockService;
    private final GoodsService goodsService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 不加锁：多个请求同时读到同一个库存值，各自减完再写回，
     * 后写的会覆盖先写的，结果就是扣减次数与实际减少的库存对不上。
     */
    @PostMapping("/buy/no-lock")
    public Map<String, Object> buyWithoutLock(@RequestParam Long id,
                                              @RequestParam(defaultValue = "1") int quantity) {
        boolean success = stockService.deductWithDelayedWindow(id, quantity);
        return result(id, success, "no-lock");
    }

    /** Redisson 分布式锁，同一商品同一时刻只有一个请求能进临界区 */
    @PostMapping("/buy/redisson")
    public Map<String, Object> buyWithRedisson(@RequestParam Long id,
                                               @RequestParam(defaultValue = "1") int quantity) {
        boolean success = stockService.deductWithLock(id, quantity);
        return result(id, success, "redisson");
    }

    /** 手写锁：SETNX 加锁，解锁时用 Lua 比对持有者标记再删 */
    @PostMapping("/buy/setnx")
    public Map<String, Object> buyWithSetNx(@RequestParam Long id,
                                            @RequestParam(defaultValue = "1") int quantity) {
        String lockKey = SETNX_LOCK_PREFIX + id;
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, Duration.ofSeconds(10));
        if (!Boolean.TRUE.equals(locked)) {
            Map<String, Object> busy = new LinkedHashMap<>();
            busy.put("strategy", "setnx");
            busy.put("success", false);
            busy.put("reason", "没抢到锁，已有请求在处理");
            return busy;
        }
        try {
            return result(id, stockService.deduct(id, quantity), "setnx");
        } finally {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), token);
        }
    }

    /** 把库存重置回指定值，方便反复做并发实验 */
    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestParam Long id, @RequestParam int stock) {
        stockService.resetStock(id, stock);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("stock", stock);
        return result;
    }

    /** 查看当前库存 */
    @GetMapping("/stock/{id}")
    public Goods stock(@PathVariable Long id) {
        return goodsService.getFromDb(id);
    }

    /** 查看某个锁 key 的剩余存活时间，用来观察锁有没有被释放 */
    @GetMapping("/info")
    public Map<String, Object> lockInfo(@RequestParam String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("ttlSeconds", stringRedisTemplate.getExpire(key));
        return result;
    }

    private Map<String, Object> result(Long id, boolean success, String strategy) {
        Goods goods = goodsService.getFromDb(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategy", strategy);
        result.put("success", success);
        result.put("stockLeft", goods == null ? null : goods.getStock());
        return result;
    }
}
