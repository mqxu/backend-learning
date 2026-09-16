package top.mqxu.file.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description FileController
 **/
@RestController
@RequestMapping("/file")
public class FileController {

    @GetMapping("/hello")
    public String hello() {
        return "文件上传下载 模块";
    }
}
