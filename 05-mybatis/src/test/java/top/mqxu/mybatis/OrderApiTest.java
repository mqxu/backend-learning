package top.mqxu.mybatis;

import com.jayway.jsonpath.JsonPath;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import top.mqxu.mybatis.entity.Product;
import top.mqxu.mybatis.service.OrderService;
import top.mqxu.mybatis.service.ProductService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 挑战练习的接口层验证：商品查询与订单的增删改查
 *
 * <p>本模块没有全局异常处理器（那是 04-web 的内容），业务异常会直接冒泡到 MockMvc，
 * 所以库存不足那条用例用 assertThatThrownBy 断言，而不是断言 500 状态码。</p>
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Resource
    private ProductService productService;

    @Resource
    private OrderService orderService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("TRUNCATE TABLE t_order");
        jdbcTemplate.execute("TRUNCATE TABLE t_product");
    }

    private Product product(String name, String price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        productService.save(product);
        return product;
    }

    @Test
    @DisplayName("GET /products 与 GET /products/{id} 返回库存")
    void listProducts() throws Exception {
        Product keyboard = product("机械键盘", "399.00", 10);
        product("人体工学椅", "1299.00", 3);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("机械键盘"))
                .andExpect(jsonPath("$[0].stock").value(10));

        mockMvc.perform(get("/products/{id}", keyboard.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(10));
    }

    @Test
    @DisplayName("下单、查单、取消、删除四个接口走一遍")
    void orderLifecycle() throws Exception {
        Product product = product("机械键盘", "399.00", 10);

        String created = mockMvc.perform(post("/orders")
                        .param("userId", "1")
                        .param("productId", String.valueOf(product.getId()))
                        .param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNo").isNotEmpty())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.amount").value(798.00))
                .andExpect(jsonPath("$.status").value(1))
                .andReturn().getResponse().getContentAsString();

        long orderId = ((Number) JsonPath.read(created, "$.id")).longValue();

        // 库存同步扣减
        assertThat(productService.getById(product.getId()).getStock()).isEqualTo(8);

        mockMvc.perform(get("/orders").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(orderId));

        mockMvc.perform(put("/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
        assertThat(orderService.getById(orderId).getStatus()).isZero();

        mockMvc.perform(delete("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
        assertThat(orderService.getById(orderId)).isNull();
    }

    @Test
    @DisplayName("库存不足时下单抛异常，库存与订单表都不变")
    void createOrderWithInsufficientStock() {
        Product product = product("限量键盘", "399.00", 5);

        assertThatThrownBy(() -> mockMvc.perform(post("/orders")
                .param("userId", "1")
                .param("productId", String.valueOf(product.getId()))
                .param("quantity", "6")))
                .hasRootCauseMessage("库存不足：剩余 5，需要 6");

        assertThat(productService.getById(product.getId()).getStock()).isEqualTo(5);
        assertThat(orderService.count()).isZero();
    }
}
