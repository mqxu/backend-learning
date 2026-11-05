package top.mqxu.mybatis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.mqxu.mybatis.entity.User;
import top.mqxu.mybatis.service.UserService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    void 逻辑删除的记录查不到() {
        assertThat(userService.getById(5L)).isNull();
        assertThat(userService.count()).isEqualTo(5);
    }

    @Test
    void 新增时自动填充创建时间() {
        User user = new User();
        user.setUsername("auto-fill-test");
        user.setPassword("x");
        user.setGender(1);

        userService.save(user);

        User saved = userService.getById(user.getId());
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
        assertThat(saved.getDeleted()).isZero();
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void 按关键词模糊匹配用户名或昵称() {
        List<User> users = userService.searchByKeyword("张");
        assertThat(users).isNotEmpty();
    }

    @Test
    void 充值后余额按金额增加() {
        User before = userService.getById(2L);
        BigDecimal origin = before.getBalance();

        userService.recharge(2L, new BigDecimal("100"));

        assertThat(userService.getById(2L).getBalance())
                .isEqualByComparingTo(origin.add(new BigDecimal("100")));
    }
}
