package top.mqxu.mybatis.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description MybatisController
 **/
@RestController
@RequestMapping("/mybatis")
public class MybatisController {

    @GetMapping("/hello")
    public String hello() {
        return "数据访问 模块";
    }
}
