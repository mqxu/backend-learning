package top.mqxu.test.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import top.mqxu.test.common.BusinessException;
import top.mqxu.test.dto.OrderResult;
import top.mqxu.test.service.OrderService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 控制器切片测试。
 * <p>
 * @WebMvcTest 只装配 Web 层（控制器、异常处理、参数解析），不加载数据库与 Service，
 * 启动一两秒就完成。Service 用 @MockitoBean 造一个假的，专注验证"请求进来 → JSON 出去"这条链路。
 *
 * @author mqxu
 */
@WebMvcTest(OrderController.class)
@DisplayName("订单接口（Web 层切片）")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    @DisplayName("正常试算返回 200 与计算结果")
    void calculateOk() throws Exception {
        given(orderService.calculate(any())).willReturn(new OrderResult(
                new BigDecimal("300.00"), new BigDecimal("53.00"),
                new BigDecimal("247.00"), List.of("满 300 减 40", "金卡会员 95 折")));

        mockMvc.perform(post("/orders/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": 1, "amount": 300.00, "memberLevel": "GOLD", "quantity": 1}"""))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.finalAmount").value(247.00))
                .andExpect(jsonPath("$.data.appliedRules.length()").value(2));
    }

    @Test
    @DisplayName("参数校验失败返回 400 与具体原因")
    void calculateBadRequest() throws Exception {
        mockMvc.perform(post("/orders/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": 1, "amount": -1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("金额必须大于 0"));
    }

    @Test
    @DisplayName("缺少必填字段返回 400")
    void calculateMissingField() throws Exception {
        mockMvc.perform(post("/orders/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 100}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户 ID 不能为空"));
    }

    @Test
    @DisplayName("业务异常按业务码映射成 409")
    void calculateStockNotEnough() throws Exception {
        given(orderService.calculate(any())).willThrow(BusinessException.stockNotEnough(5));

        mockMvc.perform(post("/orders/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": 1, "amount": 100, "quantity": 5}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(30409));
    }

    @Test
    @DisplayName("请求体不是合法 JSON 返回 400")
    void calculateBrokenJson() throws Exception {
        mockMvc.perform(post("/orders/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1, \"amount\": "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("模块信息接口返回提示")
    void hello() throws Exception {
        mockMvc.perform(get("/orders/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.module").value("11-test"));
    }

    @Test
    @DisplayName("快捷试算走查询参数")
    void quickCalculate() throws Exception {
        given(orderService.calculate(any())).willReturn(new OrderResult(
                new BigDecimal("100.00"), new BigDecimal("10.00"),
                new BigDecimal("90.00"), List.of("满 100 减 10")));

        mockMvc.perform(get("/orders/quick-calculate")
                        .param("amount", "100")
                        .param("memberLevel", "NORMAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalAmount").value(90.00));
    }

    @Test
    @DisplayName("查询参数类型不对返回 400 而不是 500")
    void quickCalculateBadType() throws Exception {
        mockMvc.perform(get("/orders/quick-calculate").param("amount", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数 amount 格式不正确"));
    }

    @Test
    @DisplayName("缺必填查询参数返回 400")
    void quickCalculateMissingParam() throws Exception {
        mockMvc.perform(get("/orders/quick-calculate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少参数 amount"));
    }
}
