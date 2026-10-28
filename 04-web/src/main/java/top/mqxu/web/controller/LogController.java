package top.mqxu.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description LogController 演示过滤器如何拿到各类请求参数
 **/
@RestController
@RequestMapping("/api/log")
@Slf4j
public class LogController {

    /**
     * 查询参数：GET /api/log/hello?name=张三&age=18
     */
    @GetMapping("/hello")
    public String getHello(@RequestParam(required = false, defaultValue = "world") String name, @RequestParam(required = false, defaultValue = "20") Integer age) {
        log.info("调用 LogController 接口，参数是：{},{}", name, age);
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return "hello " + name + ", age=" + age;
    }
}
