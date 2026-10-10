package top.mqxu.mybatis;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import top.mqxu.mybatis.dto.UserQuery;
import top.mqxu.mybatis.entity.User;
import top.mqxu.mybatis.service.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lambda 条件查询测试，覆盖 eq / like / ge / le / and-or 嵌套 / orderBy / last / select 单列 / apply
 *
 * @author mqxu
 */
@SpringBootTest
class UserLambdaQueryTest {

    @Resource
    private UserService userService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepare() {
        jdbcTemplate.execute("TRUNCATE TABLE t_user");

        seed("admin", "超级管理员", 1, 35, 12000, u -> {
            u.setBalance(new BigDecimal("100.00"));
            u.setMemberLevel("GOLD");
            u.setVip(true);
            u.setTags(List.of("管理员", "Java"));
        });
        seed("zhangsan", "张三", 1, 20, 120, u -> {
            u.setBalance(new BigDecimal("20.50"));
            u.setVip(false);
            u.setTags(List.of("学生", "Java"));
        });
        seed("lisi", "李四", 2, 26, 2600, u -> {
            u.setBalance(new BigDecimal("300.25"));
            u.setMemberLevel("SILVER");
            u.setVip(true);
            u.setTags(List.of("前端", "Vue"));
        });
        seed("sunqi", "孙七", 2, 22, 1500, u -> {
            u.setBalance(BigDecimal.ZERO);
            u.setMemberLevel("SILVER");
            u.setVip(false);
            u.setStatus(0);
            u.setTags(List.of("测试"));
        });
    }

    private void seed(String username, String nickname, int gender, int age, int points, Consumer<User> extra) {
        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setGender(gender);
        user.setAge(age);
        user.setPoints(points);
        extra.accept(user);
        userService.createUser(user);
    }

    private List<String> usernamesOf(List<User> users) {
        return users.stream().map(User::getUsername).toList();
    }

    @Test
    @DisplayName("多条件组合：eq + ge + le 同时生效")
    void listByConditionCombinesFilters() {
        UserQuery query = new UserQuery();
        query.setGender(1);
        query.setMinAge(20);
        query.setMaxAge(30);

        assertThat(usernamesOf(userService.listByCondition(query))).containsExactly("zhangsan");
    }

    @Test
    @DisplayName("关键词同时匹配 username 与 nickname，条件为空时返回全部")
    void listByConditionKeywordMatchesBoth() {
        UserQuery byNickname = new UserQuery();
        byNickname.setKeyword("李");
        assertThat(usernamesOf(userService.listByCondition(byNickname))).containsExactly("lisi");

        UserQuery byUsername = new UserQuery();
        byUsername.setKeyword("admin");
        assertThat(usernamesOf(userService.listByCondition(byUsername))).containsExactly("admin");

        assertThat(userService.listByCondition(new UserQuery())).hasSize(4);
    }

    @Test
    @DisplayName("模糊查询：and 包住 or，逻辑删除的行不会被 OR 带出来")
    void searchByKeywordKeepsOtherConditions() {
        assertThat(usernamesOf(userService.searchByKeyword("李"))).containsExactly("lisi");
        assertThat(usernamesOf(userService.searchByKeyword("zhang"))).containsExactly("zhangsan");
        assertThat(usernamesOf(userService.searchByKeyword("Java"))).isEmpty();

        User removed = new User();
        removed.setUsername("zhaoliu");
        removed.setNickname("赵六");
        removed.setAge(24);
        userService.createUser(removed);
        userService.removeUser(removed.getId());

        assertThat(userService.searchByKeyword("赵")).isEmpty();
    }

    @Test
    @DisplayName("年龄区间查询，按年龄升序返回")
    void listByAgeRangeWorks() {
        assertThat(usernamesOf(userService.listByAgeRange(20, 26)))
                .containsExactly("zhangsan", "sunqi", "lisi");
        assertThat(usernamesOf(userService.listByAgeRange(30, null)))
                .containsExactly("admin");
        assertThat(usernamesOf(userService.listByAgeRange(null, 21)))
                .containsExactly("zhangsan");
    }

    @Test
    @DisplayName("按积分倒序取前 N 条，last 拼 limit")
    void listTopOrdersByPoints() {
        assertThat(usernamesOf(userService.listTop(2))).containsExactly("admin", "lisi");
        assertThat(userService.listTop(100)).hasSize(4);
    }

    @Test
    @DisplayName("只查一列：select + listObjs")
    void listUsernamesReturnsSingleColumn() {
        assertThat(userService.listUsernames())
                .containsExactly("admin", "zhangsan", "lisi", "sunqi");
    }

    @Test
    @DisplayName("JSON 列查询：JSON_CONTAINS 命中标签")
    void listByTagWorks() {
        assertThat(usernamesOf(userService.listByTag("Java"))).containsExactly("admin", "zhangsan");
        assertThat(usernamesOf(userService.listByTag("Vue"))).containsExactly("lisi");
        assertThat(userService.listByTag("不存在")).isEmpty();
    }

    @Test
    @DisplayName("exists 与 count")
    void existsAndCountWork() {
        assertThat(userService.existsUsername("admin")).isTrue();
        assertThat(userService.existsUsername("nobody")).isFalse();
        assertThat(userService.countByGender(1)).isEqualTo(2);
        assertThat(userService.countByGender(2)).isEqualTo(2);
    }

    @Test
    @DisplayName("分组统计：总数、余额合计、最大年龄、按性别分组")
    void statisticsAggregates() {
        Map<String, Object> stat = userService.statistics();

        assertThat(((Number) stat.get("totalCount")).longValue()).isEqualTo(4);
        assertThat(new BigDecimal(stat.get("totalBalance").toString())).isEqualByComparingTo("420.75");
        assertThat(((Number) stat.get("maxAge")).intValue()).isEqualTo(35);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> genderCount = (List<Map<String, Object>>) stat.get("genderCount");
        assertThat(genderCount).hasSize(2);
        assertThat(((Number) genderCount.get(0).get("cnt")).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("条件分页：先过滤再分页")
    void pageByConditionWorks() {
        UserQuery query = new UserQuery();
        query.setMemberLevel("SILVER");

        Page<User> page = userService.pageByCondition(query, 1, 1);
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getPages()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(1);
    }
}
