package top.mqxu.mybatis.service;

import com.baomidou.mybatisplus.spring.service.IService;
import top.mqxu.mybatis.entity.Order;

import java.util.List;

/**
 * 订单服务，挑战练习的主角
 *
 * @author mqxu
 */
public interface OrderService extends IService<Order> {

    /**
     * 下单：扣库存 + 写订单，两次写操作放在同一个事务里
     *
     * <p>库存不足时抛异常，验证扣掉的库存有没有被回滚、订单有没有写进去。</p>
     *
     * @param userId    下单用户
     * @param productId 商品
     * @param quantity  数量，超过库存即触发回滚
     * @return 落库后的订单，带主键与自动填充字段
     */
    Order createOrder(Long userId, Long productId, int quantity);

    /** 按用户查订单，按 id 倒序 */
    List<Order> listByUser(Long userId);

    /** 取消订单，走实体更新，观察 updateTime 刷新与 version 递增 */
    boolean cancelOrder(Long orderId);

    /** 逻辑删除订单，删除后查询查不到，库里那行 deleted 变 1 */
    boolean removeOrder(Long orderId);
}
