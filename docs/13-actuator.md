# 13 · 应用监控 —— 用 Actuator 观测系统

> - 难度：进阶
> - 前置模块：04-web
> - 预计时长：约 45 分钟
> - 对应代码：[13-actuator](../13-actuator)

## 1. 本节导读

学完本节，你将能：

1. 用 Actuator 暴露健康、指标等端点；
2. 自定义健康检查与业务指标；
3. 对接 Prometheus 采集指标。

**前置知识**：会 `04-web`。

---

## 2. 概念铺垫

### 2.1 为什么要监控

服务上线后，怎么知道它还活着？QPS 是多少？内存占了多少？「可观测性」就是给系统装仪表盘。Actuator 是 Spring Boot 自带的观测入口。

### 2.2 Actuator 端点

| 端点 | 作用 |
| --- | --- |
| `/actuator/health` | 健康状态（UP/DOWN） |
| `/actuator/info` | 应用信息 |
| `/actuator/metrics` | 指标列表 |
| `/actuator/prometheus` | Prometheus 格式指标（需额外依赖） |

### 2.3 Micrometer 是什么

一套「指标门面」，屏蔽底层监控系统差异。你只需用 Micrometer 记录指标，底层是 Prometheus 还是别的，改依赖即可（和 SLF4J 之于日志是同一个思路）。

---

## 3. 环境准备

依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

开启端点（默认只暴露 health）：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

---

## 4. 动手实战

> 本模块当前是骨架，下面带你逐步补全监控能力。

### 步骤 1：暴露端点

配置见「环境准备」。启动后访问 `http://localhost:8013/actuator/health`，应返回：

```json
{"status":"UP"}
```

### 步骤 2：自定义健康检查

目标：让健康检查包含业务依赖（如数据库）状态。

```java
package top.mqxu.actuator.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 自定义健康检查
 *
 * @author mqxu
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // 实际可探测数据库连接
        boolean dbUp = checkDatabase();
        if (dbUp) {
            return Health.up().withDetail("database", "可用").build();
        }
        return Health.down().withDetail("database", "不可用").build();
    }

    private boolean checkDatabase() {
        return true; // 演示：真实项目在此做连通性检测
    }
}
```

### 步骤 3：自定义业务指标

目标：统计某接口被调用的次数。

```java
package top.mqxu.actuator.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 业务接口
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final MeterRegistry meterRegistry;

    @GetMapping("/hello")
    public String hello() {
        Counter.builder("api.hello.requests")
                .description("hello 接口调用次数")
                .register(meterRegistry)
                .increment();
        return "hello";
    }
}
```

讲解：

- `Counter` 只增不减的计数器，适合统计「调用次数」；
- 注册进 `MeterRegistry` 后，可通过 `/actuator/metrics/api.hello.requests` 查询。

### 步骤 4：对接 Prometheus

访问 `http://localhost:8013/actuator/prometheus`，能看到 Prometheus 格式的指标。Prometheus 配置抓取该地址即可持续采集，配合 Grafana 出图。

---

## 5. 完整代码与目录结构

```
13-actuator
└── src/main
    ├── java/top/mqxu/actuator
    │   ├── ActuatorApplication.java
    │   ├── controller/ApiController.java
    │   └── health/DatabaseHealthIndicator.java
    └── resources/application.yml
```

---

## 6. 运行与验证

```bash
mvn -pl 13-actuator -am spring-boot:run
```

| 请求 | 期望 |
| --- | --- |
| `GET /actuator/health` | `{"status":"UP","details":{"database":"可用"}}` |
| `GET /actuator/metrics` | 指标列表 |
| `GET /actuator/prometheus` | Prometheus 格式指标 |
| `GET /api/hello` 后查 `api.hello.requests` | 计数 +1 |

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 404 | 端点没暴露 | `management.endpoints.web.exposure.include` |
| 只看到 health | 默认最小暴露 | include 里加其他端点 |
| 指标为空 | 没触发对应逻辑 | 先调用接口再查指标 |
| 生产泄露敏感信息 | 全部暴露 | 生产只开必要端点，并加鉴权 |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 访问 `/actuator/health` | 返回 UP |
| 2 | 自定义一个「内存」健康指标 | health 里出现该 detail |
| 3 | 访问 3 次 `/api/hello` 再查计数 | 计数为 3 |

### 8.2 课后练习（课后完成并提交）

- **基础**：暴露 `info` 端点，并在 `application.yml` 里配 `info.app.name`。
- **进阶**：用 `Timer` 记录 `/api/hello` 的耗时分布，观察 `/actuator/metrics` 里的 timer 指标。
- **挑战**：本地起 Prometheus + Grafana，抓取 `/actuator/prometheus` 画一张 QPS 图，截图并写说明。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| Actuator | 健康/信息/指标端点 |
| HealthIndicator | 自定义健康检查 |
| Micrometer | 指标门面，Counter/Timer |
| Prometheus | 拉取指标 + Grafana 出图 |

下一节 [14-deploy](14-deploy.md) 会讲部署与 CI/CD。

> 本节遵循的规范（官方 + 阿里手册）：生产环境最小暴露端点并加鉴权；指标命名用小写点分、带业务前缀；健康检查覆盖关键依赖。
