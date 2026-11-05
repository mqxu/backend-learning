package top.mqxu.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import top.mqxu.config.properties.AppProperties;
import top.mqxu.config.properties.StudentProperties;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConfigBindingTest {

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private StudentProperties studentProperties;

    @Autowired
    private Environment environment;

    @Test
    void 前缀绑定生效且支持宽松绑定() {
        assertThat(appProperties.getName()).isEqualTo("配置管理模块");
        assertThat(appProperties.getMaxCount()).isEqualTo(100);
    }

    @Test
    void 占位符引用被解析成实际值() {
        assertThat(appProperties.getAuthor()).isEqualTo("许莫淇");
    }

    @Test
    void 复杂结构绑定完整() {
        assertThat(studentProperties.getHobbies()).hasSize(3).contains("编程");
        assertThat(studentProperties.getScores()).containsEntry("math", 95);
        assertThat(studentProperties.getAddress().getCity()).isEqualTo("南京市");
        assertThat(studentProperties.getCourses()).hasSize(2);
        assertThat(studentProperties.getCourses().get(0).getCredit()).isEqualTo(4);
    }

    @Test
    void 默认激活开发环境() {
        assertThat(environment.getActiveProfiles()).contains("dev");
    }
}
