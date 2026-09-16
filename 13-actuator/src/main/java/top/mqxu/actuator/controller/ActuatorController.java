package top.mqxu.actuator.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description ActuatorController
 **/
@RestController
@RequestMapping("/actuator")
public class ActuatorController {

    @GetMapping("/hello")
    public String hello() {
        return "应用监控 模块";
    }
}
