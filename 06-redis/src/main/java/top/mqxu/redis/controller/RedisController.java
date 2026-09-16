package top.mqxu.redis.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description RedisController
 **/
@RestController
@RequestMapping("/redis")
public class RedisController {

    @GetMapping("/hello")
    public String hello() {
        return "缓存与分布式锁 模块";
    }
}
