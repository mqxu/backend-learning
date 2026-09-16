package top.mqxu.security.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description SecurityController
 **/
@RestController
@RequestMapping("/security")
public class SecurityController {

    @GetMapping("/hello")
    public String hello() {
        return "认证与授权 模块";
    }
}
