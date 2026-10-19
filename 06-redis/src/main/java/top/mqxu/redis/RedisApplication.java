package top.mqxu.redis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import top.mqxu.redis.config.DemoProperties;

/**
 * 启动类
 *
 * @author mqxu
 */
@EnableCaching
@EnableConfigurationProperties(DemoProperties.class)
@SpringBootApplication
public class RedisApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisApplication.class, args);
    }
}
