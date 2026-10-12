package top.mqxu.mybatis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.mybatis.entity.Order;
import top.mqxu.mybatis.service.OrderService;

import java.util.List;

/**
 * 订单接口
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 下单，数量超过库存时返回 500 并整体回滚 */
    @PostMapping
    public Order create(@RequestParam Long userId,
                        @RequestParam Long productId,
                        @RequestParam(defaultValue = "1") int quantity) {
        return orderService.createOrder(userId, productId, quantity);
    }

    /** 按用户查订单 */
    @GetMapping
    public List<Order> listByUser(@RequestParam Long userId) {
        return orderService.listByUser(userId);
    }

    /** 取消订单 */
    @PutMapping("/{id}/cancel")
    public boolean cancel(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }

    /** 逻辑删除订单 */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return orderService.removeOrder(id);
    }
}
