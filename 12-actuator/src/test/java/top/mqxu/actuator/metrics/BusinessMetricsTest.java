package top.mqxu.actuator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指标本身怎么测。
 * <p>
 * 不用起 Spring：{@link SimpleMeterRegistry} 就是 Micrometer 自带的一个内存注册表，
 * 行为与 Prometheus 注册表一致，只是不做导出。测的是"打点打对了吗"，不是"端点通不通"。
 *
 * @author mqxu
 */
@DisplayName("业务指标")
class BusinessMetricsTest {

    private SimpleMeterRegistry registry;

    private BusinessMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new BusinessMetrics(registry);
    }

    @Test
    @DisplayName("下单计数每次加一")
    void counterIncrements() {
        metrics.orderCreated();
        metrics.orderCreated();

        Counter counter = registry.get("business.orders.created").counter();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("失败计数按 reason 分成独立序列")
    void failedCounterTaggedByReason() {
        metrics.orderFailed("stock_empty");
        metrics.orderFailed("stock_empty");
        metrics.orderFailed("pay_timeout");

        assertThat(registry.get("business.orders.failed").tag("reason", "stock_empty").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("business.orders.failed").tag("reason", "pay_timeout").counter().count())
                .isEqualTo(1.0);
        // 两个 reason 是两条序列，不是一条加总的
        assertThat(registry.get("business.orders.failed").counters()).hasSize(2);
    }

    @Test
    @DisplayName("耗时记录进 Timer，次数、总时长、最大值都对")
    void timerRecordsDuration() {
        metrics.recordHandle(100, "success");
        metrics.recordHandle(300, "success");

        Timer timer = registry.get("business.order.handle").tag("result", "success").timer();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(400.0);
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(300.0);
    }

    @Test
    @DisplayName("成功与失败分成两条 Timer 序列")
    void timerSplitByResult() {
        metrics.recordHandle(10, "success");
        metrics.recordHandle(20, "failure");

        assertThat(registry.get("business.order.handle").tag("result", "success").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("business.order.handle").tag("result", "failure").timer().count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("处理中的订单数随 enter / leave 变化")
    void gaugeTracksProcessing() {
        Gauge gauge = registry.get("business.orders.processing").gauge();
        assertThat(gauge.value()).isZero();

        metrics.enter();
        metrics.enter();
        assertThat(gauge.value()).isEqualTo(2.0);

        metrics.leave();
        assertThat(gauge.value()).isEqualTo(1.0);
    }
}
