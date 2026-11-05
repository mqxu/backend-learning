package top.mqxu.test.client;

import org.springframework.stereotype.Component;

/**
 * 默认实现：固定返回 100 件库存，让应用能独立跑起来。
 * 测试里不会用这个类，而是用 Mockito 造一个可控的假实现。
 *
 * @author mqxu
 */
@Component
public class DefaultInventoryClient implements InventoryClient {

    @Override
    public int availableStock(Long productId) {
        return 100;
    }
}
