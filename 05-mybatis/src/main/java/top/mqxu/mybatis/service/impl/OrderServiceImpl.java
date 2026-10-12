package top.mqxu.mybatis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.mqxu.mybatis.entity.Order;
import top.mqxu.mybatis.entity.Product;
import top.mqxu.mybatis.mapper.OrderMapper;
import top.mqxu.mybatis.mapper.ProductMapper;
import top.mqxu.mybatis.service.OrderService;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单服务实现
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final ProductMapper productMapper;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Order createOrder(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("购买数量必须大于 0");
        }

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new IllegalArgumentException("商品不存在：" + productId);
        }

        int stockBefore = product.getStock();

        // 第一步：扣库存，先把库改了
        product.setStock(stockBefore - quantity);
        productMapper.updateById(product);

        // 第二步：业务校验放在写库之后，库存成负数就抛异常。
        // 这里故意不提前 return，就是为了让“扣库存已经执行”这件事暴露在回滚面前
        if (product.getStock() < 0) {
            throw new IllegalStateException("库存不足：剩余 " + stockBefore + "，需要 " + quantity);
        }

        // 第三步：写订单
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(1);
        save(order);
        return order;
    }

    @Override
    public List<Order> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getId));
    }

    @Override
    public boolean cancelOrder(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在：" + orderId);
        }
        if (Integer.valueOf(0).equals(order.getStatus())) {
            return false;
        }
        order.setStatus(0);
        return updateById(order);
    }

    @Override
    public boolean removeOrder(Long orderId) {
        return removeById(orderId);
    }

    /**
     * 订单号：时间戳加四位随机数，够本地演示用
     */
    private String generateOrderNo() {
        return "NO" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
