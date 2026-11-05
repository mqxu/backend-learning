package top.mqxu.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试：启动完整容器，走真实的 Service 与真实 Bean 装配。
 * <p>
 * 与切片测试的分工：切片测"Web 层行为是否正确"（Service 是假的），
 * 集成测"整条链路是否连通"——真实的 OrderService、真实的库存客户端、真实的序列化。
 * 它更慢，所以数量要少，只挑关键路径。
 * <p>
 * 这里用 MockMvc 而不是真实端口：省去端口占用与网络开销，覆盖的过滤器与解析链路是一样长的。
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("订单接口（集成测试）")
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("完整链路：请求 → Service → 响应")
    void calculateThroughRealStack() throws Exception {
        mockMvc.perform(post("/orders/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": 1, "amount": 300.00, "memberLevel": "GOLD", "quantity": 1}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.originAmount").value(300.00))
                .andExpect(jsonPath("$.data.discount").value(53.00))
                .andExpect(jsonPath("$.data.finalAmount").value(247.00))
                .andExpect(jsonPath("$.data.appliedRules.length()").value(2));
    }

    @Test
    @DisplayName("快捷试算接口可用")
    void quickCalculate() throws Exception {
        mockMvc.perform(get("/orders/quick-calculate")
                        .param("amount", "150")
                        .param("memberLevel", "NORMAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalAmount").value(140.00));
    }

    @Test
    @DisplayName("真实库存客户端返回 100，买 200 件会被拒")
    void stockNotEnoughThroughRealClient() throws Exception {
        mockMvc.perform(post("/orders/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": 1, "amount": 100, "quantity": 200}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(30409));
    }
}
