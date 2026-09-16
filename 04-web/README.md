# 04-web · Web 开发进阶

> 状态：🚧 骨架已创建，核心案例待开发

把接口做得规范、健壮、可维护。

## 学习目标

- 掌握 RESTful 接口设计规范
- 参数校验、统一响应体 `Result<T>`
- 全局异常处理、拦截器与过滤器

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| RESTful 规范 | 资源路径 + HTTP 方法语义 |
| 参数校验 | `@Validated` + JSR-303 |
| 统一响应 | `Result<T>` 统一 code / message / data |
| 全局异常 | `@RestControllerAdvice` 统一兜底 |
| 拦截器 / 过滤器 | `HandlerInterceptor` / `Filter` |
| 跨域 | CORS 配置 |

## 目录结构

```
04-web
└── src/main
    ├── java/top/mqxu/web
    │   ├── WebApplication.java
    │   └── controller/WebController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 04-web -am spring-boot:run
```

端口：`8004`

## 待引入依赖

- `spring-boot-starter-validation`（参数校验）
