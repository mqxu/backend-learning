package top.mqxu.logging.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.logging.common.LogMaskUtil;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description LoggingController
 **/
@RestController
@RequestMapping("/logging")
@Slf4j
public class LoggingController {
    //private static final Logger logger = LoggerFactory.getLogger(LoggingController.class);

    @GetMapping("/hello")
    public String hello() {
        return "日志管理 模块";
    }

    @GetMapping("/demo")
    public String demo() {
        log.info("处理 /log/demo 请求");
        log.warn("这是一条警告日志");
        log.error("这是一条错误日志");
        String name = "张三";
        log.info("用户 {} 登录成功", name);         // ✅ 推荐
        log.info("用户 " + name + " 登录成功");     // ❌ 禁止
        log.info("用户手机号：{}", LogMaskUtil.maskPhone("13812345678"));
        return "ok";
    }
}
