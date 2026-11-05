package top.mqxu.actuator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务指标。
 * <p>
 * Micrometer 的命名约定：{@code business.orders.created} 会被 Prometheus 导出成
 * {@code business_orders_created_total}。三种仪表的分工：
 * Counter 只增不减，Timer 记录耗时分布，Gauge 反映某个瞬时值。
 *
 * @author mqxu
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    /** 下单成功次数 */
    private final Counter ordersCreated;

    /** 当前处理中的订单数，Gauge 每次抓取时读它的当前值 */
    private final AtomicInteger processing = new AtomicInteger();

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.ordersCreated = Counter.builder("business.orders.created")
                .description("累计创建成功的订单数")
                .register(registry);
        registry.gauge("business.orders.processing", processing);
    }

    public void orderCreated() {
        ordersCreated.increment();
    }

    public void orderFailed(String reason) {
        registry.counter("business.orders.failed", "reason", reason).increment();
    }

    /**
     * 记录一次订单处理耗时。成功与失败分成两个 tag，面板上能分开画线。
     *
     * @param millis 耗时毫秒
     * @param result success / failure
     */
    public void recordHandle(long millis, String result) {
        Timer.builder("business.order.handle")
                .description("订单处理耗时")
                .tag("result", result)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(Duration.ofMillis(millis));
    }

    public void enter() {
        processing.incrementAndGet();
    }

    public void leave() {
        processing.decrementAndGet();
    }
}
