package top.mqxu.toolkit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 工具库实战模块启动类
 *
 * @author mqxu
 * @date 2026-10-21
 * @description Hutool 典型功能的工程化案例
 */
@SpringBootApplication
public class ToolkitApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(ToolkitApplication.class, args);
    }
}
