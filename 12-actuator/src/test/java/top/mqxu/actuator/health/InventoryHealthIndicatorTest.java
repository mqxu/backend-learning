package top.mqxu.actuator.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 健康检查的两种状态。
 * <p>
 * 这个类没有构造器注入，两个字段靠 {@code @Value} 从配置读。单元测试里用
 * {@link ReflectionTestUtils} 直接设字段，比为了测两个分支去起一个容器划算。
 *
 * @author mqxu
 */
@DisplayName("库存健康检查")
class InventoryHealthIndicatorTest {

    private final InventoryHealthIndicator indicator = new InventoryHealthIndicator();

    @Test
    @DisplayName("依赖正常时返回 UP，并带上判断依据")
    void upWithDetails() {
        ReflectionTestUtils.setField(indicator, "healthy", true);
        ReflectionTestUtils.setField(indicator, "url", "http://inventory-service/health");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsKeys("url", "latencyMs", "checkedAt")
                .containsEntry("url", "http://inventory-service/health");
    }

    @Test
    @DisplayName("依赖不通时返回 DOWN，并说明原因")
    void downWithReason() {
        ReflectionTestUtils.setField(indicator, "healthy", false);
        ReflectionTestUtils.setField(indicator, "url", "http://inventory-service/health");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "连接超时");
    }
}
