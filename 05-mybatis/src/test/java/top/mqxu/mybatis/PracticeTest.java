package top.mqxu.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import top.mqxu.mybatis.entity.User;
import top.mqxu.mybatis.service.UserService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * docs/05-mybatis.md 第 8.1 节课堂随堂练习的答案与验证
 *
 * <p>每条用例对应练习表里的一题，方法名后的说明写的就是题目要求与观察点。
 * 表里第 5 题的说法有歧义，这里补了一条用例把两种情形都测出来。</p>
 *
 * @author mqxu
 */
@SpringBootTest
class PracticeTest {

    @Resource
    private UserService userService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.execute("TRUNCATE TABLE t_user");
    }

    /**
     * 造一条用户，新增后 id 已回填
     */
    private User seed(String username, int age, boolean vip) {
        User user = new User();
        user.setUsername(username);
        user.setAge(age);
        user.setVip(vip);
        userService.createUser(user);
        return user;
    }

    /* ==================== 练习 1 ==================== */

    @Test
    @DisplayName("练习 1：getById 查不存在的 id，返回 null 且不抛异常")
    void practice1GetByIdReturnsNull() {
        // 先确认调用本身不会抛异常，再断言返回值
        assertThatCode(() -> userService.getById(99999L)).doesNotThrowAnyException();
        assertThat(userService.getById(99999L)).isNull();
    }

    /* ==================== 练习 2 ==================== */

    @Test
    @DisplayName("练习 2：age > 18 且 vip = true，两个条件都要生效")
    void practice2AgeGreaterThan18AndVip() {
        seed("成年会员", 20, true);      // 两个条件都满足
        seed("未成年会员", 17, true);    // age 不满足
        seed("成年非会员", 30, false);   // vip 不满足
        seed("刚好十八岁", 18, true);    // gt 是严格大于，18 不应该命中

        List<User> result = userService.list(new LambdaQueryWrapper<User>()
                .gt(User::getAge, 18)
                .eq(User::getVip, true));

        assertThat(result).extracting(User::getUsername).containsExactly("成年会员");
    }

    /* ==================== 练习 3 ==================== */

    @Test
    @DisplayName("练习 3：新增 age = 200 抛异常，前面的写入被回滚，count 不变")
    void practice3IllegalAgeRollsBack() {
        seed("已有用户", 20, false);
        long countBefore = userService.count();

        User illegal = new User();
        illegal.setUsername("超龄用户");
        illegal.setAge(200);

        assertThatThrownBy(() -> userService.createUser(illegal))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("年龄超出合理范围");

        assertThat(userService.count()).isEqualTo(countBefore);
        // 不只是数量对得上，那条记录确实没落库
        assertThat(userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "超龄用户"))).isNull();
    }

    /* ==================== 练习 4 ==================== */

    @Test
    @DisplayName("练习 4：逻辑删除后 selectById 返回 null，直接查库能看到 deleted = 1")
    void practice4LogicalDelete() {
        User user = seed("待删除用户", 25, false);

        assertThat(userService.removeById(user.getId())).isTrue();

        // 走 MyBatis-Plus 的查询：SQL 被自动追加 deleted = 0，查不到
        assertThat(userService.getById(user.getId())).isNull();

        // 绕过 MyBatis-Plus 用 JdbcTemplate 直接查：物理行还在，deleted 变成 1
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_user WHERE id = ?", Integer.class, user.getId());
        assertThat(deleted).isEqualTo(1);
    }

    /* ==================== 练习 5 ==================== */

    @Test
    @DisplayName("练习 5：两个请求各查一次再更新，后提交的版本号过期，影响 0 行")
    void practice5OptimisticLockRejectsStaleUpdate() {
        User user = seed("并发用户", 30, false);

        // 模拟两个请求各自从库里读了一次，拿到的是同一个 version = 0
        User requestA = userService.getById(user.getId());
        User requestB = userService.getById(user.getId());
        assertThat(requestA.getVersion()).isZero();
        assertThat(requestB.getVersion()).isZero();

        requestA.setNickname("A 改的");
        assertThat(userService.updateById(requestA)).isTrue();

        // B 手里的 version 已经过期，SQL 里 AND version = 0 匹配不到任何行
        requestB.setNickname("B 改的");
        assertThat(userService.updateById(requestB)).isFalse();

        assertThat(userService.getById(user.getId()).getNickname()).isEqualTo("A 改的");
    }

    @Test
    @DisplayName("练习 5 补充：换成同一个实体对象连调两次，两次都会成功")
    void practice5SameInstanceTwiceSucceeds() {
        User user = seed("版本回填", 30, false);

        User same = userService.getById(user.getId());
        same.setNickname("第一次");
        assertThat(userService.updateById(same)).isTrue();

        // 更新成功后乐观锁插件会把新版本号写回实体，所以对象里的 version 变成 1，
        // 第二次更新拼出来的条件是 AND version = 1，正好对上库里那一行，于是照样成功
        assertThat(same.getVersion()).isEqualTo(1);

        same.setNickname("第二次");
        assertThat(userService.updateById(same)).isTrue();
        assertThat(userService.getById(user.getId()).getVersion()).isEqualTo(2);
    }
}
