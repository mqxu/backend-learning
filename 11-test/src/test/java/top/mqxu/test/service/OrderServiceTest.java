package top.mqxu.test.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import top.mqxu.test.client.InventoryClient;
import top.mqxu.test.common.BusinessException;
import top.mqxu.test.dto.OrderRequest;
import top.mqxu.test.dto.OrderResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderService 的单元测试。
 * <p>
 * 不需要启动 Spring 容器：依赖用 Mockito 造，测试跑得飞快。
 * 这类测试要覆盖的是"业务规则的分支"，不是"框架能不能装配"。
 *
 * @author mqxu
 */
@DisplayName("订单试算规则")
class OrderServiceTest {

    private InventoryClient inventoryClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // 每个用例都拿一个新的 mock，避免用例之间互相影响
        inventoryClient = mock(InventoryClient.class);
        orderService = new OrderService(inventoryClient);
        // 默认给足库存，个别用例再覆盖
        when(inventoryClient.availableStock(anyLong())).thenReturn(100);
    }

    @Nested
    @DisplayName("满减规则")
    class FullCutRules {

        @Test
        @DisplayName("不满 100 不优惠")
        void noDiscountBelow100() {
            OrderResult result = orderService.calculate(
                    new OrderRequest(1L, new BigDecimal("99.00"), "NORMAL", 1));

            assertThat(result.discount()).isEqualByComparingTo("0.00");
            assertThat(result.finalAmount()).isEqualByComparingTo("99.00");
            assertThat(result.appliedRules()).isEmpty();
        }

        @Test
        @DisplayName("满 100 减 10")
        void discountAt100() {
            OrderResult result = orderService.calculate(
                    new OrderRequest(1L, new BigDecimal("100.00"), "NORMAL", 1));

            assertThat(result.discount()).isEqualByComparingTo("10.00");
            assertThat(result.finalAmount()).isEqualByComparingTo("90.00");
            assertThat(result.appliedRules()).containsExactly("满 100 减 10");
        }

        @Test
        @DisplayName("满 300 减 40，与满 100 不叠加")
        void discountAt300() {
            OrderResult result = orderService.calculate(
                    new OrderRequest(1L, new BigDecimal("300.00"), "NORMAL", 1));

            assertThat(result.discount()).isEqualByComparingTo("40.00");
            assertThat(result.appliedRules()).containsExactly("满 300 减 40");
        }
    }

    @Nested
    @DisplayName("会员规则")
    class MemberRules {

        @Test
        @DisplayName("金卡在满减之后再打 95 折")
        void goldMemberExtraDiscount() {
            // 300 - 40 = 260，260 * 5% = 13，合计优惠 53，应付 247
            OrderResult result = orderService.calculate(
                    new OrderRequest(1L, new BigDecimal("300.00"), "GOLD", 1));

            assertThat(result.discount()).isEqualByComparingTo("53.00");
            assertThat(result.finalAmount()).isEqualByComparingTo("247.00");
            assertThat(result.appliedRules()).contains("满 300 减 40", "金卡会员 95 折");
        }

        @Test
        @DisplayName("会员等级大小写不敏感")
        void memberLevelIgnoreCase() {
            OrderResult result = orderService.calculate(
                    new OrderRequest(1L, new BigDecimal("200.00"), "gold", 1));

            assertThat(result.appliedRules()).contains("金卡会员 95 折");
        }

        @Test
        @DisplayName("普通会员不享受额外折扣")
        void normalMemberNoExtra() {
            OrderResult result = orderService.calculate(
                    new OrderRequest(1L, new BigDecimal("200.00"), "NORMAL", 1));

            assertThat(result.appliedRules()).doesNotContain("金卡会员 95 折");
        }
    }

    @Nested
    @DisplayName("边界与异常")
    class EdgeCases {

        @ParameterizedTest(name = "金额 {0} 最终应付 {1}")
        @CsvSource({
                "0.02, 0.02",
                "99.99, 99.99",
                "100.00, 90.00",
                "299.99, 289.99",
                "300.00, 260.00",
                "1000.00, 960.00"
        })
        @DisplayName("多组金额的应付结果")
        void finalAmountMatrix(String amount, String expected) {
            OrderResult result = orderService.calculate(
                    new OrderRequest(1L, new BigDecimal(amount), "NORMAL", 1));

            assertThat(result.finalAmount()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("金额为 0 抛业务异常")
        void zeroAmount() {
            assertThatThrownBy(() -> orderService.calculate(
                    new OrderRequest(1L, BigDecimal.ZERO, "NORMAL", 1)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("金额必须大于 0");
        }

        @Test
        @DisplayName("库存不足抛业务异常")
        void stockNotEnough() {
            when(inventoryClient.availableStock(anyLong())).thenReturn(2);

            assertThatThrownBy(() -> orderService.calculate(
                    new OrderRequest(1L, new BigDecimal("100.00"), "NORMAL", 5)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("库存不足");
        }

        @Test
        @DisplayName("库存够时不会抛异常，且只查一次库存")
        void stockQueriedOnce() {
            orderService.calculate(new OrderRequest(1L, new BigDecimal("100.00"), "NORMAL", 1));

            verify(inventoryClient, times(1)).availableStock(anyLong());
        }

        @Test
        @DisplayName("金额极小时不低于 0.01")
        void minimumAmount() {
            OrderResult result = orderService.calculate(
                    new OrderRequest(1L, new BigDecimal("0.01"), "NORMAL", 1));

            assertThat(result.finalAmount()).isEqualByComparingTo("0.01");
        }
    }
}
