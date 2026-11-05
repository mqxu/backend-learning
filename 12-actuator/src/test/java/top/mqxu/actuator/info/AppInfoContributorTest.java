package top.mqxu.actuator.info;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InfoContributor 的单元测试。
 * <p>
 * 它只依赖 {@code Environment}，用 {@link MockEnvironment} 造一个就行，不需要容器。
 *
 * @author mqxu
 */
@DisplayName("应用信息")
class AppInfoContributorTest {

    @Test
    @DisplayName("塞进去的模块信息与负责人信息都在")
    @SuppressWarnings("unchecked")
    void contributesModuleAndOwner() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "12-应用监控")
                .withProperty("server.port", "8012");
        AppInfoContributor contributor = new AppInfoContributor(environment);

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        assertThat(info.getDetails()).containsKeys("module", "owner");

        Map<String, Object> module = (Map<String, Object>) info.getDetails().get("module");
        assertThat(module)
                .containsEntry("name", "12-应用监控")
                .containsEntry("port", "8012")
                .containsKey("startedAt");

        Map<String, Object> owner = (Map<String, Object>) info.getDetails().get("owner");
        assertThat(owner).containsEntry("team", "backend-learning");
    }
}
