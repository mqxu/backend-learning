package top.mqxu.test.client;

/**
 * 库存服务客户端。真实环境是一次远程调用，测试时会被 mock 替换。
 *
 * @author mqxu
 */
public interface InventoryClient {

    /**
     * 查询某件商品的可售库存
     *
     * @param productId 商品 ID
     * @return 可售数量
     */
    int availableStock(Long productId);
}
