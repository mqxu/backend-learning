package top.mqxu.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 本模块的业务参数，对应 application.yml 里的 demo 前缀
 *
 * @author mqxu
 */
@Data
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

    private Cache cache = new Cache();

    private Lock lock = new Lock();

    private Limit limit = new Limit();

    /** 缓存相关 */
    @Data
    public static class Cache {

        /** 手动写入缓存时的默认存活时间 */
        private Duration ttl = Duration.ofMinutes(10);

        /** 空值标记的存活时间，通常比正常值短一些 */
        private Duration nullTtl = Duration.ofMinutes(2);

        /** TTL 随机抖动幅度，单位秒 */
        private int jitterSeconds = 30;
    }

    /** 分布式锁相关 */
    @Data
    public static class Lock {

        /** 抢锁最长等待秒数 */
        private long waitSeconds = 3;

        /** 持锁自动释放秒数，防止进程崩溃后锁一直不释放 */
        private long leaseSeconds = 10;
    }

    /** 限流相关 */
    @Data
    public static class Limit {

        /** 统计窗口秒数 */
        private int windowSeconds = 60;

        /** 窗口内允许的最大请求数 */
        private int maxRequests = 5;
    }
}
