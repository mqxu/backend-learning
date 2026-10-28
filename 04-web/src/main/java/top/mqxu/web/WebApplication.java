package top.mqxu.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description WebApplication
 **/
@SpringBootApplication
@ServletComponentScan
public class WebApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
