# 03 · 日志管理 —— 规范地记录与排查问题

> - 难度：入门
> - 前置模块：02-config
> - 预计时长：约 45 分钟
> - 对应代码：[03-logging](https://github.com/mqxu/backend-learning/tree/main/03-logging)

## 1. 本节导读

学完本节，你将能：

1. 用 SLF4J 规范地打印日志，而不是 `System.out.println`；
2. 配置日志级别、输出格式，并按环境分文件；
3. 用 MDC 给同一次请求的日志打上 `traceId`，实现链路追踪；
4. 对敏感信息（手机号、身份证）做日志脱敏。

**前置知识**：会 `02-config`，了解依赖注入即可。

---

## 2. 概念铺垫

### 2.1 为什么要「规范」地打日志

排查线上问题，往往只能靠日志。如果日志里是一堆 `System.out.println`、没有时间、没有级别、没有上下文，出了问题就是大海捞针。规范的日志要能回答三个问题：**什么时间、哪个请求、发生了什么**。

### 2.2 SLF4J 门面 + Logback 实现

这是经典的**门面模式**：

- **SLF4J**：一套「打日志」的统一接口（门面），你的代码只依赖它；
- **Logback**：真正干活的实现（Spring Boot 默认），负责格式化、写文件等。

好处：代码面向 SLF4J 写，将来想换实现（如 Log4j2）只改依赖，不改代码。

```java
// 面向门面编程，不 import 任何 Logback 的类
private static final Logger log = LoggerFactory.getLogger(X.class);
```

> 用 Lombok 的 `@Slf4j` 可以少写这行，自动生成 `log` 字段。

### 2.3 日志级别

级别从低到高：`TRACE < DEBUG < INFO < WARN < ERROR`。

| 级别 | 用途 |
| --- | --- |
| DEBUG | 调试信息，开发期用 |
| INFO | 关键业务节点（下单、登录成功） |
| WARN | 有异常但能继续（重试、降级） |
| ERROR | 出错了，需要人工介入 |

设置了某级别后，**低于它的日志不会输出**。所以生产环境通常设 `INFO` 甚至 `WARN`，避免刷屏。

### 2.4 MDC 是什么

一个请求从进来到响应，会经过 controller、service、mapper 层层调用，日志是「平铺」的，很难看出哪些属于同一次请求。MDC（Mapped Diagnostic Context）是 SLF4J 提供的「当前线程上下文」，你可以往里面塞一个 `traceId`，它会在本次请求的所有日志里带上，从而把一次请求的日志串成一条线。

---

## 3. 环境准备

无需额外依赖：`spring-boot-starter` 已内置 Logback。只需在依赖里确认有 `lombok`（用于 `@Slf4j`）。

---

## 4. 动手实战

> 本模块当前是骨架，下面带你逐步补全日志能力。

### 步骤 1：用 `@Slf4j` 打基础日志

目标：替换 `System.out.println`。

```java
package top.mqxu.logging.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志演示接口
 *
 * @author mqxu
 */
@Slf4j
@RestController
@RequestMapping("/log")
public class LogController {

    @GetMapping("/demo")
    public String demo() {
        log.info("处理 /log/demo 请求");
        log.warn("这是一条警告日志");
        log.error("这是一条错误日志");
        return "ok";
    }
}
```

讲解：

- `@Slf4j` 生成 `log` 字段（SLF4J 的 `Logger`）；
- **规范点**：日志里如要拼接变量，用占位符 `{}`，**禁止** `+` 拼接（会提前计算、浪费性能）：

```java
String name = "张三";
log.info("用户 {} 登录成功", name);        // ✅ 推荐
log.info("用户 " + name + " 登录成功");     // ❌ 禁止
```

验证：访问 `/log/demo`，控制台能看到带时间、级别、类名的日志。

### 步骤 2：配置日志格式与按环境输出

目标：自定义输出格式、按天滚动写文件、dev/prod 用不同级别。

在 `resources` 下新建 `logback-spring.xml`（注意带 `-spring`，才能用 `<springProfile>`）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 控制台 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- 按天滚动的文件 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/app.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- 开发环境：INFO，输出到控制台 -->
    <springProfile name="dev">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <!-- 生产环境：WARN，写文件 -->
    <springProfile name="prod">
        <root level="WARN">
            <appender-ref ref="FILE"/>
        </root>
    </springProfile>
</configuration>
```

讲解：

- `%d` 时间、`%thread` 线程、`%-5level` 级别、`%logger` 类名、`%X{traceId}` MDC 值、`%msg` 消息、`%n` 换行；
- `<springProfile>` 让不同环境用不同配置（dev 打控制台、prod 写文件且级别更高）。

### 步骤 3：MDC 链路追踪

目标：每次请求自动打上 `traceId`。

先写拦截器，在请求进入时塞 `traceId`：

```java
package top.mqxu.logging.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 为每次请求生成 traceId 写入 MDC
 *
 * @author mqxu
 */
@Component
public class TraceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MDC.put("traceId", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束必须清理，避免线程复用导致串号
        MDC.clear();
    }
}
```

注册拦截器：

```java
package top.mqxu.logging.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.mqxu.logging.common.TraceInterceptor;

/**
 * Web 配置
 *
 * @author mqxu
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TraceInterceptor traceInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceInterceptor).addPathPatterns("/**");
    }
}
```

讲解：

- `preHandle` 在进 controller 前执行，写入 `traceId`；`afterCompletion` 在响应后执行，`MDC.clear()` 清理；
- 配合步骤 2 的 `%X{traceId}`，同一请求的所有日志都会带上同一个 traceId。

> **规范点**：`MDC.clear()` 必须放在 `afterCompletion`（或 `finally`）里，否则线程池复用线程会串号。

### 步骤 4：日志脱敏

目标：手机号、身份证等敏感信息不落明文。

```java
package top.mqxu.logging.common;

/**
 * 日志脱敏工具
 *
 * @author mqxu
 */
public final class LogMaskUtil {

    private LogMaskUtil() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
```

使用：

```java
log.info("用户手机号：{}", LogMaskUtil.maskPhone("13812345678"));
// 输出：用户手机号：138****5678
```

讲解：

- 脱敏放在「打日志前」用工具方法处理，是最简单直接的方式；
- 更彻底的做法是用 Logback 的 `MessageConverter` 自动识别并脱敏，属于进阶内容，可自行了解。

---

## 5. 完整代码与目录结构

```
03-logging
└── src/main
    ├── java/top/mqxu/logging
    │   ├── LoggingApplication.java
    │   ├── controller/LogController.java
    │   ├── config/WebConfig.java
    │   └── common
    │       ├── TraceInterceptor.java
    │       └── LogMaskUtil.java
    └── resources
        ├── application.yml
        └── logback-spring.xml
```

---

## 6. 运行与验证

```bash
mvn -pl 03-logging -am spring-boot:run
```

访问 `GET /log/demo`，观察控制台输出应包含：

```
2026-09-16 10:00:00.123 [http-nio-8003-exec-1] INFO  ...LogController [3f2a...] - 处理 /log/demo 请求
```

- 能看到时间、线程、级别、类名、`[traceId]`；
- 多次访问，同一次请求的多条日志 traceId 相同，不同请求不同。

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 日志没输出 | 级别设太高（如 WARN） | 调低 `root level`，或给指定包单独设 level |
| 文件没生成 | 没配 `FILE` appender，或当前 profile 没引用它 | 检查 `<springProfile>` 里的 `appender-ref` |
| `%X{traceId}` 打印为空 | 拦截器没注册 / MDC 未 put | 确认 `addPathPatterns("/**")` 且 `preHandle` 执行 |
| 日志串号 | 忘记 `MDC.clear()` | 在 `afterCompletion` 里清理 |
| `springProfile` 不生效 | 文件命名成了 `logback.xml` | 必须叫 `logback-spring.xml` |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 在 `demo()` 里用占位符打印 `name` 变量 | 控制台正确输出变量值 |
| 2 | 把 dev 环境级别临时改为 `DEBUG` 并加一条 `log.debug` | DEBUG 日志能显示 |
| 3 | 加一条带手机号的日志并用 `maskPhone` 脱敏 | 输出 `138****5678` 而非明文 |

### 8.2 课后练习（课后完成并提交）

- **基础**：给 `LogController` 增加一个带 `@RequestParam name` 的接口，用占位符打印 `name`。
- **进阶**：新增 `application-prod.yml` 与 `<springProfile name="prod">`，实现 prod 写文件、级别 WARN。
- **挑战**：自定义一个 Logback `MessageConverter`，自动把日志中的手机号脱敏，无需手动调用工具方法。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| SLF4J + Logback | 门面 + 实现，代码只依赖 SLF4J |
| `@Slf4j` | 自动生成 `log` 字段 |
| 占位符 `{}` | 禁止字符串拼接 |
| logback-spring.xml | 格式、按天滚动、`<springProfile>` 分环境 |
| MDC | 用 `traceId` 串起一次请求的全部日志 |
| 脱敏 | 敏感信息打码后再落日志 |

下一节 [04-web](04-web.md) 会讲接口工程化：统一响应体、全局异常处理与参数校验。

> 本节遵循的规范（官方 + 阿里手册）：日志统一 SLF4J 门面；占位符替代拼接；级别选取（阿里手册：线上谨慎使用 DEBUG，重要日志用 INFO/WARN/ERROR）；敏感信息脱敏；`MDC.clear()` 防串号。
