package top.mqxu.deploy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 部署信息与探针的测试。
 * <p>
 * 这些断言看起来琐碎，但它们守的是"上线后还能不能查清这个实例是谁"。
 * build-info 没生成、探针没开，接口照样返回 200，只是字段变成了 null 或者 404，
 * 出事的时候才发现，而那时候已经晚了。
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("部署信息与探针")
class DeployApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("hello 返回模块名")
    void hello() throws Exception {
        mockMvc.perform(get("/deploy/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("部署与 CI/CD 模块"));
    }

    @Test
    @DisplayName("build-info 生效，构建信息不是空壳")
    void infoHasBuildInfo() throws Exception {
        // spring-boot-maven-plugin 的 build-info 目标会生成 META-INF/build-info.properties，
        // 没执行它的话 BuildProperties 这个 Bean 压根不存在，这里会变成 build.note
        mockMvc.perform(get("/deploy/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.artifact").value("13-deploy"))
                .andExpect(jsonPath("$.build.version").value("0.0.1-SNAPSHOT"))
                .andExpect(jsonPath("$.build.time").exists());
    }

    @Test
    @DisplayName("runtime 段带出端口、JDK 版本、主机名与进程号")
    void infoHasRuntimeDetails() throws Exception {
        mockMvc.perform(get("/deploy/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtime.application").value("13-部署与 CI/CD"))
                // 端口不断言具体值：环境变量 SERVER__PORT 会覆盖 application.yml，
                // 用 server.port=0 时这里还会读到 0。断言格式就够了
                .andExpect(jsonPath("$.runtime.port", matchesPattern("\\d+")))
                .andExpect(jsonPath("$.runtime.javaVersion").exists())
                .andExpect(jsonPath("$.runtime.hostname").isNotEmpty())
                // 进程号是 JSON 数字，不是字符串 "unknown"
                .andExpect(jsonPath("$.runtime.pid").isNumber())
                .andExpect(jsonPath("$.runtime.startedAt").exists())
                .andExpect(jsonPath("$.runtime.uptime", matchesPattern("\\d+ 分 \\d+ 秒")));
    }

    @Test
    @DisplayName("pipeline 把五步命令都列出来了")
    void pipelineListsFiveSteps() throws Exception {
        mockMvc.perform(get("/deploy/pipeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.构建").exists())
                .andExpect(jsonPath("$.镜像").exists())
                .andExpect(jsonPath("$.运行").exists())
                .andExpect(jsonPath("$.编排").exists())
                .andExpect(jsonPath("$.探活").exists());
    }

    @Test
    @DisplayName("健康检查整体 UP")
    void healthUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("liveness 与 readiness 两个探针都开了")
    void probesEnabled() throws Exception {
        // 不开 management.endpoint.health.probes.enabled 的话，这两个路径都是 404，
        // 容器编排拿不到探针状态，只能一直当它健康
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("只暴露 health 与 info，metrics 访问不到")
    void onlyHealthAndInfoExposed() throws Exception {
        // include 里只写了 health,info。这种收敛是刻意的：
        // 监控端点开得越多，被打听内部结构的成本越低
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isNotFound());
    }
}
