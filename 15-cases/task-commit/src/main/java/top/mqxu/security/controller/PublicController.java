package top.mqxu.security.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;

import java.util.Map;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 开放接口，对应 SecurityConfig 里 permitAll 的 /public/**
 **/
@RestController
@RequestMapping("/public")
public class PublicController {

    @GetMapping("/ping")
    public Result<Map<String, String>> ping() {
        return Result.ok(Map.of("status", "ok", "message", "无需认证即可访问"));
    }
}
