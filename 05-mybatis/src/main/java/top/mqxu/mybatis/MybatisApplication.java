package top.mqxu.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description MybatisApplication
 **/
@SpringBootApplication
@MapperScan(basePackages = "top.mqxu.mybatis.mapper")
public class MybatisApplication {
    public static void main(String[] args) {
        SpringApplication.run(MybatisApplication.class, args);
    }
}
