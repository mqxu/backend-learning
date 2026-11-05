package top.mqxu.test.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单试算结果
 *
 * @param originAmount  原始金额
 * @param discount      优惠金额
 * @param finalAmount   应付金额
 * @param appliedRules  命中的优惠规则，便于前端展示"已优惠 40 元"
 * @author mqxu
 */
public record OrderResult(BigDecimal originAmount,
                          BigDecimal discount,
                          BigDecimal finalAmount,
                          List<String> appliedRules) {
}
