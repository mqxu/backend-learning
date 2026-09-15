package top.mqxu.config.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.config.properties.StudentProperties;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description 案例：@ConfigurationProperties 类型安全绑定
 **/
@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentProperties studentProperties;

    @GetMapping("/info")
    public StudentProperties getStudent() {
        return studentProperties;
    }
}
