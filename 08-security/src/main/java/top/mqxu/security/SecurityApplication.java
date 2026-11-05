package top.mqxu.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import top.mqxu.security.config.SecurityProperties;

/**
 * 启动类
 *
 * @author mqxu
 */
@EnableConfigurationProperties(SecurityProperties.class)
@SpringBootApplication
public class SecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityApplication.class, args);
    }
}
