package top.mqxu.actuator.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 自定义健康检查：模拟检查下游库存服务。
 * <p>
 * 健康检查不该只报 UP/DOWN，还要给出判断依据（withDetail），
 * 出问题时看 /actuator/health 就能知道是哪个依赖挂了。
 * <p>
 * healthy 为 false 时用于演示 DOWN 状态，可以用 --demo.inventory.healthy=false 启动验证。
 *
 * @author mqxu
 */
@Slf4j
@Component("inventory")
public class InventoryHealthIndicator implements HealthIndicator {

    @Value("${demo.inventory.healthy:true}")
    private boolean healthy;

    @Value("${demo.inventory.url:http://inventory-service/health}")
    private String url;

    @Override
    public Health health() {
        long start = System.currentTimeMillis();
        if (!healthy) {
            return Health.down()
                    .withDetail("url", url)
                    .withDetail("reason", "连接超时")
                    .build();
        }
        long cost = System.currentTimeMillis() - start;
        return Health.up()
                .withDetail("url", url)
                .withDetail("latencyMs", cost)
                .withDetail("checkedAt", System.currentTimeMillis())
                .build();
    }
}
