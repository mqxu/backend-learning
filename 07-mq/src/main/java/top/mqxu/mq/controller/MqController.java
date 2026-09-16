package top.mqxu.mq.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description MqController
 **/
@RestController
@RequestMapping("/mq")
public class MqController {

    @GetMapping("/hello")
    public String hello() {
        return "消息队列 模块";
    }
}
