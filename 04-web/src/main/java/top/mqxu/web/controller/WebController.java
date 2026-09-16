package top.mqxu.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description WebController
 **/
@RestController
@RequestMapping("/web")
public class WebController {

    @GetMapping("/hello")
    public String hello() {
        return "Web 开发进阶 模块";
    }
}
