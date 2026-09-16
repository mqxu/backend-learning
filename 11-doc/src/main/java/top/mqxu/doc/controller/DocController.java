package top.mqxu.doc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description DocController
 **/
@RestController
@RequestMapping("/doc")
public class DocController {

    @GetMapping("/hello")
    public String hello() {
        return "接口文档 模块";
    }
}
