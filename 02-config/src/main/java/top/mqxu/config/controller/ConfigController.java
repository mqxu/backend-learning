package top.mqxu.config.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.config.properties.AppProperties;
import top.mqxu.config.service.EnvService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final ConfigurableEnvironment environment;

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

    /**
     * 演示多环境：激活的 profile、profile 专属文件里的值、@Profile 选中的实现。
     */
    @GetMapping("/profiles")
    public Map<String, Object> getProfiles() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("activeProfiles", List.of(environment.getActiveProfiles()));
        info.put("defaultProfiles", List.of(environment.getDefaultProfiles()));
        info.put("env.name", environment.getProperty("env.name"));
        info.put("env.description", environment.getProperty("env.description"));
        info.put("envService", envService.envInfo());
        return info;
    }

    /**
     * 演示配置优先级：同一个键在命令行、环境变量、yml 里都写了，最终拿到的是优先级最高的那个。
     * 遍历 PropertySource 能把每个来源各自的值摊开看。
     */
    @GetMapping("/priority")
    public Map<String, Object> getPriority() {
        Map<String, Object> sources = new LinkedHashMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable
                    && enumerable.containsProperty("demo.priority")) {
                sources.put(source.getName(), enumerable.getProperty("demo.priority"));
            }
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("最终取值", environment.getProperty("demo.priority"));
        info.put("命中来源数", sources.size());
        info.put("各来源的值", sources);
        info.put("覆盖顺序", "命令行参数 > 系统属性 > 环境变量 > application-{profile}.yml > application.yml");
        return info;
    }
}
