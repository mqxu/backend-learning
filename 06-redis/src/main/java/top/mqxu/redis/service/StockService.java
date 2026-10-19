package top.mqxu.redis.service;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import top.mqxu.redis.config.DemoProperties;
import top.mqxu.redis.entity.Goods;
import top.mqxu.redis.mapper.GoodsMapper;

import java.util.concurrent.TimeUnit;

/**
 * 扣减库存，用来演示并发问题与分布式锁的作用
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private static final String LOCK_PREFIX = "lock:goods:stock:";

    private final GoodsMapper goodsMapper;
    private final RedissonClient redissonClient;
    private final DemoProperties properties;

    /**
     * 朴素扣减：先读、再判断、最后覆盖写，中间没有任何并发保护。
     * 两个线程可能读到同一个库存值，各自减完再先后写回，前一次的扣减就被后一次覆盖掉了。
     */
    public boolean deduct(Long id, int quantity) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || goods.getStock() < quantity) {
            return false;
        }
        Goods update = new Goods();
        update.setId(id);
        update.setStock(goods.getStock() - quantity);
        return goodsMapper.updateById(update) > 0;
    }

    /** 与朴素扣减逻辑相同，只是在读和写之间停一下，把并发窗口放大，方便复现问题 */
    public boolean deductWithDelayedWindow(Long id, int quantity) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || goods.getStock() < quantity) {
            return false;
        }
        sleepQuietly(20);
        Goods update = new Goods();
        update.setId(id);
        update.setStock(goods.getStock() - quantity);
        return goodsMapper.updateById(update) > 0;
    }

    /** 用 Redisson 分布式锁扣库存，同一商品同一时刻只有一个线程进临界区 */
    public boolean deductWithLock(Long id, int quantity) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + id);
        try {
            // 最多等 waitSeconds，拿到锁后 leaseSeconds 自动释放，进程崩了也不会一直占着
            boolean locked = lock.tryLock(properties.getLock().getWaitSeconds(),
                    properties.getLock().getLeaseSeconds(), TimeUnit.SECONDS);
            if (!locked) {
                return false;
            }
            Goods goods = goodsMapper.selectById(id);
            if (goods == null || goods.getStock() < quantity) {
                return false;
            }
            Goods update = new Goods();
            update.setId(id);
            update.setStock(goods.getStock() - quantity);
            return goodsMapper.updateById(update) > 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            // 只有持锁的线程能释放自己的锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 把库存重置回指定值，方便反复做并发实验 */
    public void resetStock(Long id, int stock) {
        Goods update = new Goods();
        update.setId(id);
        update.setStock(stock);
        goodsMapper.updateById(update);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
