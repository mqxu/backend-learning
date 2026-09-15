package top.mqxu.config.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.config.properties.AppProperties;
import top.mqxu.config.service.EnvService;

/**
 * @author mqxu
 * @date 2026/9/9
 * @description 配置管理案例：@Value、占位符、随机值、SpEL、多环境、@Profile、配置校验
 **/
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final AppProperties appProperties;
    private final EnvService envService;

    // ---------- 基础 @Value 注入 ----------
    @Value("${server.port}")
    private Integer serverPort;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${mqxu.name}")
    private String myName;

    @Value("${mqxu.job}")
    private String myJob;

    // ---------- 占位符引用：app.author 在 yml 中引用了 mqxu.name ----------
    @Value("${app.author}")
    private String author;

    // ---------- 默认值：app.remark 未配置时使用冒号后的默认值 ----------
    @Value("${app.remark:暂无备注}")
    private String remark;

    // ---------- 随机值 ----------
    @Value("${random.uuid}")
    private String randomUuid;

    @Value("${random.int(1,100)}")
    private Integer randomInt;

    // ---------- SpEL 表达式：先解析 ${student.age} 再计算三元表达式 ----------
    @Value("#{${student.age} >= 18 ? '成年' : '未成年'}")
    private String adult;

    // ---------- 多环境配置：值来自 application-{profile}.yml ----------
    @Value("${env.name}")
    private String envName;

    @Value("${env.description}")
    private String envDescription;

    @GetMapping("/basic")
    public String getBasicInfo() {
        return "服务器端口是：" + this.serverPort + "，应用名称是：" + appName;
    }

    @GetMapping("/my")
    public String getMyInfo() {
        return "我的姓名是：" + this.myName + "，职业是：" + myJob;
    }

    @GetMapping("/value")
    public String getValueCases() {
        return "占位符引用 author=" + author
                + "；默认值 remark=" + remark
                + "；随机 UUID=" + randomUuid
                + "；随机整数=" + randomInt
                + "；SpEL adult=" + adult;
    }

    @GetMapping("/env")
    public String getEnv() {
        return "当前环境：" + envName + "，" + envDescription + "；Profile Bean：" + envService.envInfo();
    }

    @GetMapping("/app")
    public AppProperties getApp() {
        return appProperties;
    }
}
