package top.mqxu.logging.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description LoggingController
 **/
@RestController
@RequestMapping("/logging")
public class LoggingController {

    @GetMapping("/hello")
    public String hello() {
        return "日志管理 模块";
    }
}
