package top.mqxu.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import top.mqxu.mybatis.entity.User;
import top.mqxu.mybatis.service.UserService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
 * 接口层测试，覆盖 docs/05-mybatis.md 第 6 节验证表里的全部接口
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;
    private User zhangsan;
    private User lisi;

    @BeforeEach
    void prepare() {
        jdbcTemplate.execute("TRUNCATE TABLE t_user");
        admin = seed("admin", "超级管理员", 1, 35, 12000, "GOLD", true, 1);
        zhangsan = seed("zhangsan", "张三", 1, 20, 120, "BRONZE", false, 1);
        lisi = seed("lisi", "李四", 2, 26, 2600, "SILVER", true, 1);
        seed("sunqi", "孙七", 2, 22, 1500, "SILVER", false, 0);

        admin.setTags(List.of("管理员", "Java"));
        userService.modifyUser(admin);
        zhangsan.setTags(List.of("学生", "Java"));
        userService.modifyUser(zhangsan);
    }

    private User seed(String username, String nickname, int gender, int age, int points,
                      String level, boolean vip, int status) {
        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setGender(gender);
        user.setAge(age);
        user.setPoints(points);
        user.setMemberLevel(level);
        user.setVip(vip);
        user.setStatus(status);
        user.setBalance(new BigDecimal("100.00"));
        userService.createUser(user);
        return user;
    }

    private User findByUsername(String username) {
        List<User> users = userService.list(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        return users.isEmpty() ? null : users.get(0);
    }

    private String json(String username, int age) {
        return "{\"username\":\"" + username + "\",\"nickname\":\"" + username + "\",\"age\":" + age + "}";
    }

    /* ==================== 增 ==================== */

    @Test
    @DisplayName("POST /users 新增，返回带 id 与自动填充字段的用户")
    void create() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("wangwu", 31)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("wangwu"))
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.createTime").isNotEmpty());

        assertThat(userService.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("POST /users/batch 批量新增，返回成功条数")
    void createBatch() throws Exception {
        mockMvc.perform(post("/users/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + json("zhouba", 21) + "," + json("wujiu", 22) + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(2));

        assertThat(userService.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("POST /users 接收 MySQL 风格时间字符串与 JSON 标签")
    void createWithRichPayload() throws Exception {
        String body = """
                {
                  "username": "chenshi",
                  "age": 30,
                  "createTime": "2008-03-25 01:58:41",
                  "birthday": "2004-09-30",
                  "workTime": "09:15:00",
                  "balance": 1234.56,
                  "tags": ["Java", "篮球"]
                }
                """;

        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createTime").value("2008-03-25 01:58:41"))
                .andExpect(jsonPath("$.birthday").value("2004-09-30"))
                .andExpect(jsonPath("$.workTime").value("09:15:00"))
                .andExpect(jsonPath("$.tags", hasSize(2)));

        User saved = findByUsername("chenshi");
        assertThat(saved).isNotNull();
        assertThat(saved.getCreateTime()).isEqualTo(LocalDateTime.of(2008, 3, 25, 1, 58, 41));
        assertThat(saved.getBalance()).isEqualByComparingTo("1234.56");
        assertThat(saved.getTags()).containsExactly("Java", "篮球");
    }

    /* ==================== 查 ==================== */

    @Test
    @DisplayName("GET /users/{id} 查询单条")
    void getById() throws Exception {
        mockMvc.perform(get("/users/{id}", lisi.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lisi.getId()))
                .andExpect(jsonPath("$.username").value("lisi"))
                .andExpect(jsonPath("$.memberLevel").value("SILVER"))
                .andExpect(jsonPath("$.vip").value(true));
    }

    @Test
    @DisplayName("GET /users 不传条件返回全部，传条件按条件过滤")
    void listWithCondition() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));

        mockMvc.perform(get("/users").param("gender", "1").param("minAge", "20").param("maxAge", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("zhangsan"));
    }

    @Test
    @DisplayName("GET /users/page 条件分页，逻辑删除的行不计入 total")
    void pageWithCondition() throws Exception {
        userService.removeUser(admin.getId());

        mockMvc.perform(get("/users/page").param("current", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(1))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.records", hasSize(2)));
    }

    @Test
    @DisplayName("GET /users/keyword 模糊匹配 username 或 nickname")
    void keyword() throws Exception {
        mockMvc.perform(get("/users/keyword").param("keyword", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("zhangsan"));
    }

    @Test
    @DisplayName("GET /users/age-range 年龄区间")
    void ageRange() throws Exception {
        mockMvc.perform(get("/users/age-range").param("minAge", "20").param("maxAge", "26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("GET /users/top 按积分取前 N")
    void top() throws Exception {
        mockMvc.perform(get("/users/top").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    @DisplayName("GET /users/usernames 只返回用户名列")
    void usernames() throws Exception {
        mockMvc.perform(get("/users/usernames"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0]").value("admin"));
    }

    @Test
    @DisplayName("GET /users/tag 按 JSON 标签查询")
    void byTag() throws Exception {
        mockMvc.perform(get("/users/tag").param("tag", "Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[1].username").value("zhangsan"));
    }

    @Test
    @DisplayName("GET /users/exists 与 /users/count")
    void existsAndCount() throws Exception {
        mockMvc.perform(get("/users/exists").param("username", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        mockMvc.perform(get("/users/exists").param("username", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));

        mockMvc.perform(get("/users/count").param("gender", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(2));
    }

    @Test
    @DisplayName("GET /users/statistics 汇总统计")
    void statistics() throws Exception {
        mockMvc.perform(get("/users/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.maxAge").value(35))
                .andExpect(jsonPath("$.genderCount", hasSize(2)));
    }

    /* ==================== 改 ==================== */

    @Test
    @DisplayName("PUT /users/{id} 更新，路径 id 覆盖请求体")
    void update() throws Exception {
        mockMvc.perform(put("/users/{id}", zhangsan.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"张三改名\",\"age\":21}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(zhangsan.getId()))
                .andExpect(jsonPath("$.nickname").value("张三改名"))
                .andExpect(jsonPath("$.age").value(21));

        assertThat(userService.getUser(zhangsan.getId()).getNickname()).isEqualTo("张三改名");
    }

    @Test
    @DisplayName("PUT /users/{id}/status 只改状态")
    void updateStatus() throws Exception {
        mockMvc.perform(put("/users/{id}/status", zhangsan.getId()).param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        assertThat(userService.getUser(zhangsan.getId()).getStatus()).isZero();
    }

    @Test
    @DisplayName("POST /users/{id}/recharge 充值")
    void recharge() throws Exception {
        mockMvc.perform(post("/users/{id}/recharge", zhangsan.getId()).param("amount", "50.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(zhangsan.getId()));

        assertThat(userService.getUser(zhangsan.getId()).getBalance()).isEqualByComparingTo("150.50");
    }

    /* ==================== 删 ==================== */

    @Test
    @DisplayName("DELETE /users/{id} 逻辑删除")
    void deleteOne() throws Exception {
        mockMvc.perform(delete("/users/{id}", lisi.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        assertThat(userService.getUser(lisi.getId())).isNull();
        assertThat(userService.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("DELETE /users/batch 批量删除，ids 逗号分隔")
    void deleteBatch() throws Exception {
        mockMvc.perform(delete("/users/batch").param("ids", zhangsan.getId() + "," + lisi.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        assertThat(userService.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("DELETE /users/status/{status} 按状态删除")
    void deleteByStatus() throws Exception {
        mockMvc.perform(delete("/users/status/{status}", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        assertThat(userService.listUsernames()).containsExactly("admin", "zhangsan", "lisi");
    }

    /* ==================== 事务 ==================== */

    @Test
    @DisplayName("年龄超出合理范围时事务回滚，库里不落数据")
    void createWithIllegalAgeRollsBack() {
        assertThatThrownBy(() -> mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("非法用户", 200))))
                .hasRootCauseMessage("年龄超出合理范围，回滚");

        assertThat(userService.count()).isEqualTo(4);
    }
}
