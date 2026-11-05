# 03-logging · 日志管理

> 📖 详细教程：[docs/03-logging.md](../docs/03-logging.md)

SLF4J + Logback 的规范用法：级别、占位符、MDC 链路追踪、敏感信息脱敏、按环境分流。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/logging/hello` | 基础接口 |
| GET | `/logging/demo` | 级别演示 + 占位符对比 |
| GET | `/logging/levels` | 输出五级日志，返回 traceId |
| GET | `/logging/mask` | 四类敏感字段脱敏 |
| GET | `/logging/error` | 异常日志带堆栈，返回 500 |

每个响应都带 `X-Trace-Id` 头，值与该请求日志里的编号一致。

## 目录结构

```
03-logging
└── src/main
    ├── java/top/mqxu/logging
    │   ├── LoggingApplication.java
    │   ├── common/{LogMaskUtil, TraceInterceptor}.java
    │   ├── config/WebConfig.java
    │   └── controller/LoggingController.java
    └── resources
        ├── application.yml          # 包级别 DEBUG、日志格式
        ├── application-prod.yml
        ├── logback-spring.xml       # 按 profile 分流、按天滚动
        └── banner.txt
```

## 运行

```bash
mvn -pl 03-logging spring-boot:run
node tools/run-cases.mjs 03-logging
```

## 要点

- 用 `{}` 占位符，别用字符串拼接；级别关闭时前者零开销
- MDC 里的 traceId 必须在 `afterCompletion` 里清理，否则线程复用会串号
- 异常只打一次：`log.error(msg, e)`，不要再 `printStackTrace`
- 日志里永不出现原文，手机号、身份证、邮箱先脱敏
