package top.mqxu.security;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description SecurityApplication
 **/
@SpringBootApplication
@MapperScan(basePackages = "top.mqxu.security.mapper")
public class SecurityApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityApplication.class, args);
    }
}
