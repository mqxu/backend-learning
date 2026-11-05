package top.mqxu.test.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.test.common.Result;
import top.mqxu.test.dto.OrderRequest;
import top.mqxu.test.dto.OrderResult;
import top.mqxu.test.service.OrderService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * 试算：传金额与会员等级，返回优惠与应付
     */
    @PostMapping("/calculate")
    public Result<OrderResult> calculate(@Valid @RequestBody OrderRequest request) {
        return Result.ok(orderService.calculate(request));
    }

    /**
     * 快捷试算：用查询参数，方便 curl 手测
     */
    @GetMapping("/quick-calculate")
    public Result<OrderResult> quickCalculate(@RequestParam BigDecimal amount,
                                             @RequestParam(defaultValue = "NORMAL") String memberLevel) {
        return Result.ok(orderService.calculate(new OrderRequest(1L, amount, memberLevel, 1)));
    }

    /**
     * 演示用：返回应用与测试相关信息，确认模块可用
     */
    @GetMapping("/hello")
    public Result<Map<String, Object>> hello() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("module", "11-test");
        data.put("hint", "执行 mvn -pl 11-test test 可以跑全部测试用例");
        data.put("coverage", "target/site/jacoco/index.html");
        return Result.ok(data);
    }
}
