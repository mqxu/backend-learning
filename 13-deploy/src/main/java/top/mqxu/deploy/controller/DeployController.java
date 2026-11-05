package top.mqxu.deploy.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 部署信息接口。
 * <p>
 * 上线后最常见的问题是"这个实例跑的到底是哪个版本"。把构建信息、运行环境、
 * 启动时间暴露出来，比登录服务器查进程、翻 jar 包快得多。
 *
 * @author mqxu
 * @date 2026/9/16
 * @description DeployController
 **/
@Slf4j
@RestController
@RequestMapping("/deploy")
public class DeployController {

    private final Environment environment;

    /** build-info 目标生成的构建信息；没执行该目标时为 null */
    private final BuildProperties buildProperties;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    private final LocalDateTime startedAt = LocalDateTime.now();

    public DeployController(Environment environment,
                            @org.springframework.beans.factory.annotation.Autowired(required = false)
                            BuildProperties buildProperties) {
        this.environment = environment;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Object> build = new LinkedHashMap<>();
        if (buildProperties != null) {
            build.put("artifact", buildProperties.getArtifact());
            build.put("version", buildProperties.getVersion());
            build.put("time", String.valueOf(buildProperties.getTime()));
        } else {
            build.put("note", "未生成 build-info，执行 mvn package 后才有");
        }
        data.put("build", build);

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("application", environment.getProperty("spring.application.name"));
        runtime.put("profiles", activeProfile);
        runtime.put("port", environment.getProperty("server.port"));
        runtime.put("javaVersion", System.getProperty("java.version"));
        runtime.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        runtime.put("hostname", hostname());
        runtime.put("pid", ManagementFactory.getRuntimeMXBean().getPid());
        runtime.put("startedAt", startedAt.toString());
        runtime.put("uptime", humanUptime());
        data.put("runtime", runtime);

        return data;
    }

    /**
     * 部署路径说明：不同环境用不同的启动参数，接口把它显式列出来
     */
    @GetMapping("/pipeline")
    public Map<String, Object> pipeline() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("构建", "mvn -pl 13-deploy -am package -DskipTests");
        data.put("镜像", "docker build -f 13-deploy/Dockerfile -t backend-learning/13-deploy:1.0.0 .");
        data.put("运行", "docker run -p 8013:8013 -e SPRING_PROFILES_ACTIVE=prod backend-learning/13-deploy:1.0.0");
        data.put("编排", "docker compose -f 13-deploy/docker-compose.yml up -d");
        data.put("探活", "GET /actuator/health");
        return data;
    }

    @GetMapping("/hello")
    public String hello() {
        return "部署与 CI/CD 模块";
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.warn("获取主机名失败：{}", e.getMessage());
            return "unknown";
        }
    }

    private String humanUptime() {
        Duration d = Duration.between(startedAt, LocalDateTime.now());
        return "%d 分 %d 秒".formatted(d.toMinutes(), d.toSecondsPart());
    }
}
