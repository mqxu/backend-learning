# 04 · Web 开发进阶 —— 工程化的接口规范

> - 难度：进阶
> - 前置模块：02-config、03-logging
> - 预计时长：约 60 分钟
> - 对应代码：[04-web](https://github.com/mqxu/backend-learning/tree/main/04-web)

## 1. 本节导读

学完本节，你将能：

1. 设计统一响应体 `Result<T>`，所有接口返回结构一致；
2. 用 `@RestControllerAdvice` 做全局异常处理，不再到处 try/catch；
3. 用 `jakarta.validation` 做参数校验，非法输入挡在进业务前；
4. 理解并落地 RESTful 设计、拦截器、过滤器与跨域。

**前置知识**：会用 controller，了解依赖注入与 Bean。

---

## 2. 概念铺垫

### 2.1 为什么要「统一响应体」

如果一个接口返回 `User` 对象，另一个返回字符串，前端就得分情况解析，出错时也不知道错误码是什么。统一成：

```json
{ "code": 200, "message": "success", "data": { ... } }
```

前端只需解析一种结构，`code` 表达成功/失败，`data` 放业务数据。这是后端工程化的第一道「约定」。

### 2.2 为什么用全局异常处理

业务代码里到处 `try { ... } catch (Exception e) { ... }` 既啰嗦又容易漏。Spring 提供 `@RestControllerAdvice` + `@ExceptionHandler`：**在统一的地方集中处理异常**，业务代码只管抛异常、抛业务异常即可。

### 2.3 RESTful 是什么

把「操作」用 HTTP 方法表达、把「对象」用 URL 表达：

| 方法 | 语义 | 示例 | 说明 |
| --- | --- | --- | --- |
| GET | 查询 | `GET /users/1001` | 幂等，不改变数据 |
| POST | 新增 | `POST /users` | 数据在请求体 |
| PUT | 整体更新 | `PUT /users/1001` | 幂等 |
| DELETE | 删除 | `DELETE /users/1001` | 幂等 |

> 一个接口是否幂等（重复调用结果一致）是设计要点：GET/PUT/DELETE 应幂等，POST 不必。

---

## 3. 环境准备

比 `03` 多一个参数校验依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

---

## 4. 动手实战

> 本模块当前是骨架，下面带你逐步补全接口工程化能力。

### 步骤 1：统一响应体 `Result<T>`

目标：定义一套所有接口共用的返回结构。

```java
package top.mqxu.web.common;

import lombok.Data;

/**
 * 统一响应体
 *
 * @author mqxu
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
```

讲解：

- 泛型 `T` 让 `data` 可以是任意类型；
- 私有构造器 + 静态工厂方法，调用方只能 `Result.ok(...)` 或 `Result.fail(...)`，语义清晰。

### 步骤 2：全局异常处理

目标：集中处理三类异常——业务异常、参数校验异常、兜底异常。

先定义业务异常：

```java
package top.mqxu.web.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author mqxu
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
```

再写全局处理器：

```java
package top.mqxu.web.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 *
 * @author mqxu
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("参数校验失败");
        return Result.fail(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后再试");
    }
}
```

讲解：

- `@RestControllerAdvice` 让它监听所有 controller 抛出的异常；
- `@ExceptionHandler` 按异常类型分流；最后的 `Exception.class` 是「兜底」，避免直接把堆栈抛给前端；
- **规范点**：兜底异常要 `log.error("系统异常", e)` 记录完整堆栈，但**返回给前端的 message 不暴露内部细节**。

### 步骤 3：参数校验

目标：非法输入挡在进入业务之前。

定义请求 DTO：

```java
package top.mqxu.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建用户请求
 *
 * @author mqxu
 */
@Data
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @Min(value = 1, message = "年龄不能小于 1")
    @Max(value = 150, message = "年龄不能大于 150")
    private Integer age;
}
```

控制器用 `@Valid` 触发校验：

```java
package top.mqxu.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.web.common.Result;
import top.mqxu.web.dto.UserCreateRequest;

/**
 * 用户接口
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    @PostMapping
    public Result<Void> create(@Valid @RequestBody UserCreateRequest request) {
        // 校验通过后才会走到这里
        return Result.ok(null);
    }
}
```

讲解：

- `@Valid` 触发 `UserCreateRequest` 上的校验注解；校验失败会抛 `MethodArgumentNotValidException`，正好被步骤 2 的处理器接住；
- `@RequestBody` 把 JSON 请求体绑定到 DTO。

### 步骤 4：拦截器与过滤器

目标：理解两类「关卡」的区别并各写一个。

- **过滤器 Filter**：Servlet 层的关卡，在进入 Spring 之前；
- **拦截器 Interceptor**：Spring MVC 层的关卡，能拿到 handler。

拦截器示例（做简单鉴权/统计）：

```java
package top.mqxu.web.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 简单日志拦截器
 *
 * @author mqxu
 */
@Component
public class LogInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("startTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long start = (Long) request.getAttribute("startTime");
        if (start != null) {
            System.out.println("耗时：" + (System.currentTimeMillis() - start) + "ms");
        }
    }
}
```

> 上面用了 `System.out.println` 只为演示；正式项目应像 `03` 一样用 SLF4J。

### 步骤 5：跨域 CORS

目标：让前端能跨域访问接口。

```java
package top.mqxu.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置
 *
 * @author mqxu
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

---

## 5. 完整代码与目录结构

```
04-web
└── src/main
    ├── java/top/mqxu/web
    │   ├── WebApplication.java
    │   ├── controller/UserController.java
    │   ├── dto/UserCreateRequest.java
    │   ├── common
    │   │   ├── Result.java
    │   │   ├── BusinessException.java
    │   │   ├── GlobalExceptionHandler.java
    │   │   └── LogInterceptor.java
    │   └── config/WebConfig.java
    └── resources/application.yml
```

---

## 6. 运行与验证

```bash
mvn -pl 04-web -am spring-boot:run
```

| 场景 | 请求 | 期望返回 |
| --- | --- | --- |
| 正常新增 | `POST /users` body `{"username":"张三","age":20}` | `{"code":200,"message":"success","data":null}` |
| 参数为空 | body `{"age":20}` | `{"code":400,"message":"用户名不能为空",...}` |
| 参数越界 | body `{"username":"张三","age":999}` | `{"code":400,"message":"年龄不能大于 150",...}` |

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 校验不生效 | 忘了 `@Valid` | 控制器参数前加 `@Valid` |
| 异常返回的是堆栈而非 JSON | 没接住该异常类型 | 在 `GlobalExceptionHandler` 加对应 `@ExceptionHandler` |
| 兜底异常暴露内部信息 | 直接返回 `e.getMessage()` | 前端 message 用固定文案，内部细节写日志 |
| CORS 不生效 | 跨域配置没生效 / 用了 `allowedOrigins` 却带 `*` | 用 `allowedOriginPatterns` |
| `@RequestBody` 绑定 null | JSON 字段名与 DTO 不一致 | 保持命名一致，或用 `@JsonProperty` |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 给 `UserCreateRequest` 加 `@NotBlank` 的 `email` 字段 | 空邮箱返回对应 message |
| 2 | 手动 `throw new BusinessException(404, "用户不存在")` | 返回 `{"code":404,"message":"用户不存在"}` |
| 3 | 给 `/users` 加 `GET` 查询，返回 `Result.ok(list)` | 返回结构统一 |

### 8.2 课后练习（课后完成并提交）

- **基础**：完善 `Result`，增加 `ok()`（无 data 时）与带自定义 code 的 `fail`。
- **进阶**：新增 `DELETE /users/{id}` 与 `PUT /users/{id}`，并用统一响应体返回。
- **挑战**：写一个 Filter 记录请求耗时，并对比它与 Interceptor 的触发时机差异，写一段说明。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| `Result<T>` | 统一 code/message/data，前端只解析一种结构 |
| `@RestControllerAdvice` | 全局异常集中处理，业务只抛异常 |
| `@Valid` + JSR-380 | 参数校验挡在业务前 |
| RESTful | 方法表达操作，URL 表达资源，注意幂等 |
| Filter vs Interceptor | 分别位于 Servlet 层、Spring MVC 层 |

下一节 [05-mybatis](05-mybatis.md) 会引入数据库，把 `/users` 从内存对象换成真正的持久化。

> 本节遵循的规范（官方 + 阿里手册）：统一响应体；全局异常兜底；不把堆栈/内部细节暴露给前端；参数校验用 JSR-380；异常日志 `log.error("...", e)` 带堆栈。
