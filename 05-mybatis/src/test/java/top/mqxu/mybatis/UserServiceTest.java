package top.mqxu.mybatis;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import top.mqxu.mybatis.entity.User;
import top.mqxu.mybatis.service.UserService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service 层单表增删改查与 MyBatis-Plus 特性测试，连本地 MySQL（backend_learning 库），每条用例前清空 t_user
 *
 * @author mqxu
 */
@SpringBootTest
class UserServiceTest {

    @Resource
    private UserService userService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.execute("TRUNCATE TABLE t_user");
    }

    private User newUser(String username, Integer age) {
        User user = new User();
        user.setUsername(username);
        user.setNickname(username + "的昵称");
        user.setAge(age);
        return user;
    }

    /* ==================== 增 ==================== */

    @Test
    @DisplayName("新增：主键回填，createTime / updateTime / deleted / version 自动填充，密码补齐默认值")
    void createFillsAuditFields() {
        User user = newUser("zhangsan", 20);

        assertThat(userService.createUser(user)).isTrue();
        assertThat(user.getId()).isNotNull();

        User saved = userService.getUser(user.getId());
        assertThat(saved.getUsername()).isEqualTo("zhangsan");
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
        assertThat(saved.getDeleted()).isZero();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getPassword()).hasSize(64);
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getMemberLevel()).isEqualTo("BRONZE");
        assertThat(saved.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("JSON、DATE、TIME、DECIMAL、ENUM 字段往返不丢类型")
    void richColumnsRoundTrip() {
        User user = newUser("sunqi", 22);
        user.setTags(List.of("Java", "篮球"));
        user.setBirthday(LocalDate.of(2004, 9, 30));
        user.setWorkTime(LocalTime.of(9, 15));
        user.setBalance(new BigDecimal("1234.56"));
        user.setMemberLevel("SILVER");
        user.setVip(true);
        user.setIntro("这条记录用来验证各类字段的读写。");

        userService.createUser(user);

        User saved = userService.getUser(user.getId());
        assertThat(saved.getTags()).containsExactly("Java", "篮球");
        assertThat(saved.getBirthday()).isEqualTo(LocalDate.of(2004, 9, 30));
        assertThat(saved.getWorkTime()).isEqualTo(LocalTime.of(9, 15));
        assertThat(saved.getBalance()).isEqualByComparingTo("1234.56");
        assertThat(saved.getMemberLevel()).isEqualTo("SILVER");
        assertThat(saved.getVip()).isTrue();
        assertThat(saved.getIntro()).isEqualTo("这条记录用来验证各类字段的读写。");
    }

    @Test
    @DisplayName("批量新增：主键全部回填")
    void createBatchWorks() {
        List<User> users = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> newUser("user" + i, 18 + i))
                .toList();

        assertThat(userService.createUserBatch(users)).isTrue();
        assertThat(userService.count()).isEqualTo(5);
        assertThat(users).allSatisfy(u -> assertThat(u.getId()).isNotNull());
    }

    /* ==================== 改 ==================== */

    @Test
    @DisplayName("更新：updateTime 自动刷新，createTime 不动，版本号加一")
    void modifyRefreshesUpdateTime() {
        User user = newUser("lisi", 26);
        userService.createUser(user);

        jdbcTemplate.update("UPDATE t_user SET update_time = ? WHERE id = ?",
                Timestamp.valueOf("2020-01-01 00:00:00"), user.getId());
        LocalDateTime createTime = userService.getUser(user.getId()).getCreateTime();

        User toUpdate = userService.getUser(user.getId());
        toUpdate.setNickname("李四改");
        assertThat(userService.modifyUser(toUpdate)).isTrue();

        User updated = userService.getUser(user.getId());
        assertThat(updated.getNickname()).isEqualTo("李四改");
        assertThat(updated.getUpdateTime()).isAfter(LocalDateTime.of(2020, 1, 1, 0, 0, 0));
        assertThat(updated.getCreateTime()).isEqualTo(createTime);
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("只改状态：LambdaUpdateWrapper 直接拼 set")
    void modifyStatusWorks() {
        User user = newUser("wangwu", 31);
        userService.createUser(user);

        assertThat(userService.modifyUserStatus(user.getId(), 0)).isTrue();
        assertThat(userService.getUser(user.getId()).getStatus()).isZero();
        assertThat(userService.getUser(user.getId()).getVersion()).isZero();
    }

    @Test
    @DisplayName("充值：先查后改，金额累加")
    void rechargeWorks() {
        User user = newUser("admin", 35);
        user.setBalance(new BigDecimal("100.00"));
        userService.createUser(user);

        assertThat(userService.recharge(user.getId(), new BigDecimal("50.50"))).isTrue();
        assertThat(userService.getUser(user.getId()).getBalance()).isEqualByComparingTo("150.50");
    }

    @Test
    @DisplayName("乐观锁：拿着过期版本号更新，影响 0 行")
    void optimisticLockRejectsStaleUpdate() {
        User user = newUser("zhangsan", 20);
        userService.createUser(user);

        User first = userService.getUser(user.getId());
        User stale = userService.getUser(user.getId());

        first.setNickname("第一次改");
        assertThat(userService.modifyUser(first)).isTrue();

        stale.setNickname("第二次改");
        assertThat(userService.modifyUser(stale)).isFalse();
        assertThat(userService.getUser(user.getId()).getNickname()).isEqualTo("第一次改");
    }

    /* ==================== 删 ==================== */

    @Test
    @DisplayName("逻辑删除：查不到了，物理行还在且 deleted = 1")
    void logicalDeleteKeepsRow() {
        User user = newUser("zhaoliu", 24);
        userService.createUser(user);

        assertThat(userService.removeUser(user.getId())).isTrue();
        assertThat(userService.getUser(user.getId())).isNull();
        assertThat(userService.count()).isZero();

        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_user WHERE id = ?", Integer.class, user.getId());
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("批量删除与按状态删除")
    void batchRemoveWorks() {
        User a = newUser("a", 20);
        User b = newUser("b", 21);
        User c = newUser("c", 22);
        userService.createUserBatch(List.of(a, b, c));
        b.setStatus(0);
        userService.modifyUserStatus(b.getId(), 0);

        assertThat(userService.removeUserBatch(List.of(a.getId(), c.getId()))).isTrue();
        assertThat(userService.count()).isEqualTo(1);

        assertThat(userService.removeUserByStatus(0)).isTrue();
        assertThat(userService.count()).isZero();
    }

    /* ==================== 查 ==================== */

    @Test
    @DisplayName("查询不存在的 id 返回 null，不抛异常")
    void getByIdReturnsNullWhenAbsent() {
        assertThat(userService.getUser(999999L)).isNull();
    }

    @Test
    @DisplayName("分页插件生效：返回 records / total / pages")
    void pageWorks() {
        for (int i = 1; i <= 7; i++) {
            userService.createUser(newUser("user" + i, 18 + i));
        }

        Page<User> page = userService.pageUsers(2, 3);
        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(3);
        assertThat(page.getTotal()).isEqualTo(7);
        assertThat(page.getPages()).isEqualTo(3);
        assertThat(page.getRecords()).hasSize(3);
    }

    /* ==================== 事务 ==================== */

    @Test
    @DisplayName("事务：年龄超出合理范围抛异常，前面的插入被回滚")
    void createRollsBackWhenAgeIllegal() {
        User user = newUser("回滚用户", 200);

        assertThatThrownBy(() -> userService.createUser(user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("年龄超出合理范围");

        assertThat(userService.count()).isZero();
    }
}
