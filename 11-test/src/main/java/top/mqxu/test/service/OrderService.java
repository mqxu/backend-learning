package top.mqxu.test.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.test.client.InventoryClient;
import top.mqxu.test.common.BusinessException;
import top.mqxu.test.dto.OrderRequest;
import top.mqxu.test.dto.OrderResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单试算。规则刻意做得简单，便于用测试把每条分支覆盖到。
 * <p>
 * 规则：
 * 满 300 减 40，满 100 减 10（取其一，不叠加）
 * 金卡会员在满减之后再打 95 折
 * 最终金额不低于 0.01
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal THRESHOLD_HIGH = new BigDecimal("300");
    private static final BigDecimal THRESHOLD_LOW = new BigDecimal("100");
    private static final BigDecimal FULL_CUT_HIGH = new BigDecimal("40");
    private static final BigDecimal FULL_CUT_LOW = new BigDecimal("10");
    private static final BigDecimal GOLD_RATE = new BigDecimal("0.05");
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    private final InventoryClient inventoryClient;

    public OrderResult calculate(OrderRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.amountInvalid();
        }

        // 库存校验依赖外部服务，测试里由 mock 决定返回值
        int stock = inventoryClient.availableStock(1L);
        if (stock < request.quantity()) {
            throw BusinessException.stockNotEnough(request.quantity());
        }

        List<String> rules = new ArrayList<>();
        BigDecimal discount = BigDecimal.ZERO;

        if (request.amount().compareTo(THRESHOLD_HIGH) >= 0) {
            discount = FULL_CUT_HIGH;
            rules.add("满 300 减 40");
        } else if (request.amount().compareTo(THRESHOLD_LOW) >= 0) {
            discount = FULL_CUT_LOW;
            rules.add("满 100 减 10");
        }

        if ("GOLD".equalsIgnoreCase(request.memberLevel())) {
            BigDecimal afterCut = request.amount().subtract(discount);
            discount = discount.add(afterCut.multiply(GOLD_RATE));
            rules.add("金卡会员 95 折");
        }

        discount = discount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalAmount = request.amount().subtract(discount).max(MIN_AMOUNT).setScale(2, RoundingMode.HALF_UP);
        return new OrderResult(request.amount(), discount, finalAmount, rules);
    }
}
