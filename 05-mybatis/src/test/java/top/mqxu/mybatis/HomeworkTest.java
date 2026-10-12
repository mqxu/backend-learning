package top.mqxu.mybatis;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import top.mqxu.mybatis.entity.Course;
import top.mqxu.mybatis.entity.Order;
import top.mqxu.mybatis.entity.Product;
import top.mqxu.mybatis.entity.User;
import top.mqxu.mybatis.service.CourseService;
import top.mqxu.mybatis.service.OrderService;
import top.mqxu.mybatis.service.ProductService;
import top.mqxu.mybatis.service.UserService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * docs/05-mybatis.md 第 8.2 节课后练习的答案与验证
 *
 * <p>进阶二（连接池耗尽）需要调小连接池再并发压测，放在第 8.4 节用脚本做，
 * 这里不重复。其余四题各对应一条用例。</p>
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class HomeworkTest {

    @Resource
    private UserService userService;

    @Resource
    private CourseService courseService;

    @Resource
    private ProductService productService;

    @Resource
    private OrderService orderService;

    @Autowired
    private MockMvc mockMvc;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("TRUNCATE TABLE t_user");
        jdbcTemplate.execute("TRUNCATE TABLE t_course");
        jdbcTemplate.execute("TRUNCATE TABLE t_order");
        jdbcTemplate.execute("TRUNCATE TABLE t_product");
    }

    private Course course(String name, String teacher, int credit) {
        Course course = new Course();
        course.setName(name);
        course.setTeacher(teacher);
        course.setCredit(credit);
        return course;
    }

    private Product product(String name, String price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        productService.save(product);
        return product;
    }

    /* ==================== 基础一 ==================== */

    @Test
    @DisplayName("基础一：LambdaUpdateWrapper 按 username 更新 email，不存在的用户名影响 0 行")
    void basic1UpdateEmailByUsername() {
        User user = new User();
        user.setUsername("zhangsan");
        user.setAge(20);
        user.setEmail("zhangsan@old.top");
        userService.createUser(user);

        assertThat(userService.modifyEmailByUsername("zhangsan", "zhangsan@mqxu.top")).isTrue();
        assertThat(userService.getById(user.getId()).getEmail()).isEqualTo("zhangsan@mqxu.top");

        // Wrapper 更新走的是 set 语句，没有实体参与，所以乐观锁版本号不会被改动
        assertThat(userService.getById(user.getId()).getVersion()).isZero();

        assertThat(userService.modifyEmailByUsername("nobody", "nobody@mqxu.top")).isFalse();
    }

    /* ==================== 基础二 ==================== */

    @Test
    @DisplayName("基础二：@JsonFormat 让 DATE / TIME / DATETIME 按指定格式进出")
    void basic2JsonFormatRoundTrip() throws Exception {
        String body = """
                {
                  "username": "formatUser",
                  "age": 20,
                  "birthday": "1998-05-05",
                  "workTime": "08:45:00",
                  "lastLoginTime": "2026-10-12 09:30:00"
                }
                """;

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                // 入参是空格分隔的日期时间，能正常解析
                .andExpect(jsonPath("$.birthday").value("1998-05-05"))
                .andExpect(jsonPath("$.workTime").value("08:45:00"))
                .andExpect(jsonPath("$.lastLoginTime").value("2026-10-12 09:30:00"))
                // 出参同样是约定格式，不是默认的 ISO 带 T 写法
                .andExpect(jsonPath("$.createTime",
                        matchesPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")));
    }

    /* ==================== 进阶一 ==================== */

    @Test
    @DisplayName("进阶一：t_course 批量新增回填主键，批量删除按主键删除")
    void advanced1CourseBatchSaveAndRemove() {
        List<Course> courses = List.of(
                course("数据结构", "王老师", 4),
                course("操作系统", "李老师", 4),
                course("计算机网络", "张老师", 3));

        assertThat(courseService.saveCourses(courses)).isTrue();
        // saveBatch 会把自增主键回填到每个对象上
        assertThat(courses).allSatisfy(c -> assertThat(c.getId()).isNotNull());
        assertThat(courseService.count()).isEqualTo(3);

        assertThat(courseService.removeCourses(List.of(courses.get(0).getId(), courses.get(1).getId())))
                .isTrue();
        assertThat(courseService.count()).isEqualTo(1);
        assertThat(courseService.listCourses()).extracting(Course::getName).containsExactly("计算机网络");
    }

    /* ==================== 挑战 ==================== */

    @Test
    @DisplayName("挑战一：库存不足时，已经执行的扣库存被回滚，订单也没写进去")
    void challenge1OrderTransactionRollsBack() {
        Product product = product("限量键盘", "399.00", 5);

        assertThatThrownBy(() -> orderService.createOrder(1L, product.getId(), 6))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("库存不足");

        // 扣库存这一步先执行过，事务回滚后库存应该回到 5
        assertThat(productService.getById(product.getId()).getStock()).isEqualTo(5);
        assertThat(orderService.count()).isZero();
    }

    @Test
    @DisplayName("挑战二：库存充足时下单成功，订单带主键与自动填充字段，库存同步扣减")
    void challenge2OrderSucceeds() {
        Product product = product("机械键盘", "399.00", 10);

        Order order = orderService.createOrder(1L, product.getId(), 2);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getOrderNo()).startsWith("NO");
        assertThat(order.getAmount()).isEqualByComparingTo("798.00");
        assertThat(order.getQuantity()).isEqualTo(2);
        assertThat(order.getStatus()).isEqualTo(1);
        assertThat(order.getCreateTime()).isNotNull();
        assertThat(order.getUpdateTime()).isNotNull();
        assertThat(order.getDeleted()).isZero();
        assertThat(order.getVersion()).isZero();

        assertThat(productService.getById(product.getId()).getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("挑战三：取消订单刷新 updateTime 并递增 version，删除走逻辑删除")
    void challenge3CancelAndLogicalDelete() {
        Product product = product("机械键盘", "399.00", 10);
        Order order = orderService.createOrder(1L, product.getId(), 1);

        assertThat(orderService.cancelOrder(order.getId())).isTrue();
        Order cancelled = orderService.getById(order.getId());
        assertThat(cancelled.getStatus()).isZero();
        assertThat(cancelled.getVersion()).isEqualTo(1);

        assertThat(orderService.removeOrder(order.getId())).isTrue();
        assertThat(orderService.getById(order.getId())).isNull();

        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_order WHERE id = ?", Integer.class, order.getId());
        assertThat(deleted).isEqualTo(1);
    }
}
