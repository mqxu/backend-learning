# 04-web · Web 开发进阶
> 📖 详细教程：[docs/04-web.md](../docs/04-web.md)
>
> 🧪 交互讲义：[docs/04-web-filter-interceptor-listener.html](../docs/04-web-filter-interceptor-listener.html)（过滤器 / 拦截器 / 监听器）

> 状态：✅ 已实现（统一响应体 / 错误码枚举 / 全局异常 / 参数校验 / 完整 CRUD + 分页 / 拦截器过滤器 / 跨域）

把接口做得规范、健壮、可维护。

## 学习目标

- 掌握 RESTful 接口设计规范
- 统一响应体 `Result<T>` + 错误码枚举 `ErrorCode`
- 参数校验、全局异常处理
- 完整 CRUD 与分页封装 `PageResult<T>`
- 拦截器与过滤器、跨域 CORS

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| RESTful 规范 | 资源路径 + HTTP 方法语义 |
| 统一响应 | `Result<T>` 统一 code / message / data |
| 错误码枚举 | `ErrorCode` 收敛 code / message |
| 参数校验 | `@Valid` + JSR-380（jakarta.validation） |
| 全局异常 | `@RestControllerAdvice` 统一兜底 |
| 分页封装 | `PageResult<T>` 返回 list + total + 分页参数 |
| 拦截器 / 过滤器 | `HandlerInterceptor` / `Filter` |
| 跨域 | CORS 配置 |

## 目录结构

```
04-web
└── src/main
    ├── java/top/mqxu/web
    │   ├── WebApplication.java
    │   ├── controller/UserController.java、DemoController.java
    │   ├── dto/UserCreateRequest.java、UserUpdateRequest.java
    │   ├── entity/User.java
    │   ├── service/UserService.java
    │   ├── common/
    │   │   ├── Result.java、PageResult.java、ErrorCode.java
    │   │   ├── BusinessException.java、GlobalExceptionHandler.java
    │   │   ├── LogInterceptor.java、RequestTimeFilter.java
    │   │   ├── filter/
    │   │   │   ├── TraceIdFilter.java、ParameterCleanFilter.java
    │   │   │   ├── ApiKeyFilter.java
    │   │   │   └── AnnotationDemoFilter.java
    │   │   ├── listener/
    │   │   │   ├── AppLifecycleListener.java
    │   │   │   └── OnlineCountListener.java
    │   │   └── interceptor/
    │   │       ├── CallbackTraceInterceptor.java
    │   │       └── TokenInterceptor.java
    │   └── config/WebConfig.java、FilterConfig.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 04-web spring-boot:run
```

端口：`8004`

接口示例：

- `GET    /users?pageNum=1&pageSize=10` 分页查询
- `GET    /users/{id}` 详情
- `POST   /users` 新增
- `PUT    /users/{id}` 更新
- `DELETE /users/{id}` 删除

## 依赖

- `spring-boot-starter-validation`（参数校验，已引入）
