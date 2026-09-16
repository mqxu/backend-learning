# 03-logging · 日志管理

> 状态：🚧 骨架已创建，核心案例待开发

规范日志输出，方便问题定位与链路追踪。

## 学习目标

- 理解 SLF4J 门面与 Logback 实现的关系
- 掌握日志级别、日志格式与按环境配置
- 学会用 MDC 做链路追踪、日志脱敏

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| SLF4J / Logback | 日志门面与 Spring Boot 默认实现 |
| 日志级别 | DEBUG / INFO / WARN / ERROR 的选取 |
| 日志格式 | 时间、线程、级别、类名、消息 |
| MDC | 给每条日志注入 traceId 做链路追踪 |
| 脱敏 | 手机号、身份证等敏感信息打码 |

## 目录结构

```
03-logging
└── src/main
    ├── java/top/mqxu/logging
    │   ├── LoggingApplication.java
    │   └── controller/LoggingController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 03-logging -am spring-boot:run
```

端口：`8003`

## 待引入依赖

无 —— `spring-boot-starter` 已内置 Logback。
