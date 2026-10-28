# 04 · Web 开发进阶 —— 工程化的接口规范

> - 难度：进阶
> - 前置模块：02-config、03-logging
> - 预计时长：约 60 分钟
> - 对应代码：[04-web](https://github.com/mqxu/backend-learning/tree/main/04-web)

## 1. 本节导读

学完本节，你将能：

1. 设计统一响应体 `Result<T>` 与错误码枚举 `ErrorCode`，所有接口返回结构一致；
2. 用 `@RestControllerAdvice` 做全局异常处理，不再到处 try/catch；
3. 用 `jakarta.validation` 做参数校验，非法输入挡在进业务前；
4. 理解并落地 RESTful 设计、拦截器、过滤器与跨域；
5. 实现一套完整可跑的 CRUD 接口与分页查询。

**前置知识**：会用 controller，了解依赖注入与 Bean。

---

## 2. 概念铺垫

### 2.1 为什么要“统一响应体”

如果一个接口返回 `User` 对象，另一个返回字符串，前端就得分情况解析，出错时也不知道错误码是什么。统一成：

```json
{ 
  "code": 200, 
  "message": "success", 
  "data": { ... } 
 }
```

前端只需解析一种结构，`code` 表达成功/失败，`data` 放业务数据。这是后端工程化的第一道“约定”。

### 2.2 为什么用全局异常处理

业务代码里到处 `try { ... } catch (Exception e) { ... }` 既啰嗦又容易漏。Spring 提供 `@RestControllerAdvice` + `@ExceptionHandler`：**在统一的地方集中处理异常**，业务代码只管抛异常、抛业务异常即可。

### 2.3 RESTful 是什么

把“操作”用 HTTP 方法表达、把“对象”用 URL 表达：

| 方法 | 语义 | 示例 | 说明 |
| --- | --- | --- | --- |
| GET | 查询 | `GET /users/1001` | 幂等，不改变数据 |
| POST | 新增 | `POST /users` | 数据在请求体 |
| PUT | 整体更新 | `PUT /users/1001` | 幂等 |
| DELETE | 删除 | `DELETE /users/1001` | 幂等 |

> 一个接口是否幂等（重复调用结果一致）是设计要点：GET/PUT/DELETE 应幂等，POST 不必。

### 2.4 为什么要“错误码枚举”

如果业务代码里到处写 `Result.fail(404, "用户不存在")`，错误码散落各处，改一个文案要全局搜索。把 code/message 收敛到一个枚举：

```java
public enum ErrorCode {
    SUCCESS(200, "success"),
    NOT_FOUND(404, "资源不存在"),
    // ...
}
```

好处：**错误码只有一处定义**，`Result`、`BusinessException`、全局异常处理器都引用它，避免魔法数字与文案漂移。

### 2.5 为什么要“分页封装”

列表接口几乎都要分页。如果每次都返回裸 `List`，前端不知道总共有多少条、当前第几页，没法渲染分页条。统一封装：

```json
{ "list": [...], "total": 100, "pageNum": 1, "pageSize": 10 }
```

`total` 让前端能算总页数，`pageNum`/`pageSize` 回显当前分页参数。

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

### 步骤 1：错误码枚举 + 统一响应体 `Result<T>`

先定义错误码枚举：

```java
package top.mqxu.web.common;

import lombok.Getter;

/**
 * 错误码枚举
 *
 * @author mqxu
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

再定义统一响应体：

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

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
```

讲解：

- 泛型 `T` 让 `data` 可以是任意类型；
- 私有构造器 + 静态工厂方法，调用方只能 `Result.ok(...)` 或 `Result.fail(...)`，语义清晰；
- `ok()/ok(data)` 复用 `ErrorCode.SUCCESS`，成功码只在一处定义。

### 步骤 2：全局异常处理

目标：集中处理异常——业务异常、参数校验、参数类型、请求体解析、兜底。

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

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

再写全局处理器：

```java
package top.mqxu.web.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        return Result.fail(e.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配：{}", e.getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "参数类型错误");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}
```

讲解：

- `@RestControllerAdvice` 监听所有 controller 抛出的异常，`@ExceptionHandler` 按异常类型分流；
- 最后 `Exception.class` 是“兜底”，避免把堆栈直接抛给前端；
- **规范点**：兜底异常要 `log.error("系统异常", e)` 记录完整堆栈，但**返回给前端的 message 不暴露内部细节**；
- 新增三个实用 handler：`MethodArgumentTypeMismatchException`（如 `GET /users/abc`）、`HttpMessageNotReadableException`（请求体 JSON 格式错误）与 `NoResourceFoundException`（访问不存在的路径返回 404，而不是 500）。

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

    @NotBlank(message = "邮箱不能为空")
    private String email;
}
```

> 更新请求 `UserUpdateRequest` 字段相同。真实项目里 create/update 的字段约束常不同（如 update 允许部分字段），所以分开定义。

控制器用 `@Valid` 触发校验（见步骤 6）。

讲解：

- `@Valid` 触发 DTO 上的校验注解；校验失败抛 `MethodArgumentNotValidException`，正好被步骤 2 接住；
- `@RequestBody` 把 JSON 请求体绑定到 DTO。

### 步骤 4：拦截器与过滤器

目标：理解两类“关卡”的区别并各写一个。

- **过滤器 Filter**：Servlet 层的关卡，在进入 Spring 之前；
- **拦截器 Interceptor**：Spring MVC 层的关卡，能拿到 handler。

过滤器示例（统计请求耗时）：

```java
package top.mqxu.web.common;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 请求耗时过滤器（Servlet 层，比拦截器更早触发）
 *
 * @author mqxu
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTimeFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long start = System.currentTimeMillis();
        chain.doFilter(request, response);
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        log.info("过滤器统计：{} {} 耗时 {}ms", httpRequest.getMethod(), httpRequest.getRequestURI(),
                System.currentTimeMillis() - start);
    }
}
```

拦截器示例（同样统计耗时）：

```java
package top.mqxu.web.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 日志拦截器：统计接口耗时（Spring MVC 层）
 *
 * @author mqxu
 */
@Slf4j
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
            log.info("拦截器统计：{} {} 耗时 {}ms", request.getMethod(), request.getRequestURI(),
                    System.currentTimeMillis() - start);
        }
    }
}
```

拦截器在 `WebConfig` 里注册（见步骤 5）。

讲解：

- Filter 在 Servlet 层、比 Interceptor 更早触发；`@Component` 注册 Filter，`@Order` 控制执行顺序；
- Interceptor 能拿到 handler（哪个 controller 方法），做鉴权/统计更方便；Filter 能处理更底层的事（编码、CORS 兜底）；
- 两者都用 SLF4J 打日志，别用 `System.out.println`。

### 步骤 5：跨域 CORS + 注册拦截器

```java
package top.mqxu.web.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置
 *
 * @author mqxu
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LogInterceptor logInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(logInterceptor).addPathPatterns("/**");
    }

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

讲解：`addInterceptors` 注册拦截器；`addCorsMappings` 配跨域。注意用 `allowedOriginPatterns` 而不是 `allowedOrigins` 配 `*`。

### 步骤 6：完整 CRUD + 分页

目标：把前面所有能力串起来，做一个完整可跑的用户接口。

先定义实体与分页封装：

```java
// entity/User.java
package top.mqxu.web.entity;

import lombok.Data;

@Data
public class User {
    private Long id;
    private String username;
    private Integer age;
    private String email;
}
```

```java
// common/PageResult.java
package top.mqxu.web.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;

    public static <T> PageResult<T> of(List<T> list, long total, int pageNum, int pageSize) {
        return new PageResult<>(list, total, pageNum, pageSize);
    }
}
```

再写 service（用内存 Map 模拟存储，05-mybatis 再换数据库）：

```java
package top.mqxu.web.service;

import org.springframework.stereotype.Service;
import top.mqxu.web.common.BusinessException;
import top.mqxu.web.common.ErrorCode;
import top.mqxu.web.common.PageResult;
import top.mqxu.web.dto.UserCreateRequest;
import top.mqxu.web.dto.UserUpdateRequest;
import top.mqxu.web.entity.User;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public PageResult<User> page(int pageNum, int pageSize) {
        List<User> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparing(User::getId));
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, all.size());
        List<User> list = from >= all.size() ? List.of() : all.subList(from, to);
        return PageResult.of(list, all.size(), pageNum, pageSize);
    }

    public User getById(Long id) {
        User user = store.get(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return user;
    }

    public User create(UserCreateRequest request) {
        User user = new User();
        user.setId(idGenerator.incrementAndGet());
        user.setUsername(request.getUsername());
        user.setAge(request.getAge());
        user.setEmail(request.getEmail());
        store.put(user.getId(), user);
        return user;
    }

    public User update(Long id, UserUpdateRequest request) {
        User user = getById(id);
        user.setUsername(request.getUsername());
        user.setAge(request.getAge());
        user.setEmail(request.getEmail());
        return user;
    }

    public void delete(Long id) {
        if (store.remove(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
```

最后写 controller：

```java
package top.mqxu.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.mqxu.web.common.PageResult;
import top.mqxu.web.common.Result;
import top.mqxu.web.dto.UserCreateRequest;
import top.mqxu.web.dto.UserUpdateRequest;
import top.mqxu.web.entity.User;
import top.mqxu.web.service.UserService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Result<PageResult<User>> page(@RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(userService.page(pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<User> get(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @PostMapping
    public Result<User> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return Result.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
```

讲解：

- 5 个接口分别对应 RESTful 的查询/详情/新增/更新/删除，全部返回 `Result<T>`；
- `@RequestParam(defaultValue=...)` 给分页参数默认值；`@PathVariable` 取路径里的 id；
- 数据用 `ConcurrentHashMap` + `AtomicLong` 模拟，重启会清空——这正是下一步 05-mybatis 要解决的持久化问题。

---

## 5. 完整代码与目录结构

```
04-web
└── src/main
    ├── java/top/mqxu/web
    │   ├── WebApplication.java
    │   ├── controller/UserController.java
    │   ├── dto
    │   │   ├── UserCreateRequest.java
    │   │   └── UserUpdateRequest.java
    │   ├── entity/User.java
    │   ├── service/UserService.java
    │   ├── common
    │   │   ├── Result.java
    │   │   ├── PageResult.java
    │   │   ├── ErrorCode.java
    │   │   ├── BusinessException.java
    │   │   ├── GlobalExceptionHandler.java
    │   │   ├── LogInterceptor.java
    │   │   └── RequestTimeFilter.java
    │   └── config/WebConfig.java
    └── resources/application.yml
```

---

## 6. 运行与验证

```bash
mvn -pl 04-web spring-boot:run
```

| 场景 | 请求 | 期望返回 |
| --- | --- | --- |
| 新增 | `POST /users` body `{"username":"张三","age":20,"email":"a@b.com"}` | `{"code":200,"message":"success","data":{"id":1,...}}` |
| 分页 | `GET /users?pageNum=1&pageSize=10` | `{"code":200,"data":{"list":[...],"total":1,"pageNum":1,"pageSize":10}}` |
| 详情 | `GET /users/1` | `{"code":200,"data":{"id":1,"username":"张三",...}}` |
| 参数为空 | `POST /users` body `{"age":20}` | `{"code":400,"message":"用户名不能为空"}` |
| 参数越界 | `POST /users` body `{"username":"张三","age":999,"email":"a@b.com"}` | `{"code":400,"message":"年龄不能大于 150"}` |
| 类型错误 | `GET /users/abc` | `{"code":400,"message":"参数类型错误"}` |
| 不存在 | `GET /users/999` | `{"code":404,"message":"资源不存在"}` |

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 校验不生效 | 忘了 `@Valid` | 控制器参数前加 `@Valid` |
| 异常返回堆栈而非 JSON | 没接住该异常类型 | 在 `GlobalExceptionHandler` 加对应 `@ExceptionHandler` |
| 兜底异常暴露内部信息 | 直接返回 `e.getMessage()` | 前端 message 用固定文案，内部细节写日志 |
| CORS 不生效 | 用了 `allowedOrigins` 却带 `*` | 用 `allowedOriginPatterns` |
| `@RequestBody` 绑定 null | JSON 字段名与 DTO 不一致 | 保持命名一致，或用 `@JsonProperty` |
| `GET /users/abc` 报 500 | 没处理类型不匹配异常 | 加 `MethodArgumentTypeMismatchException` 的 handler |
| 数据重启后丢失 | 用的是内存 Map | 05-mybatis 接数据库后解决 |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 给 `UserCreateRequest` 的 `email` 加 `@Email` 校验 | 非法邮箱返回对应 message |
| 2 | 手动 `throw new BusinessException(ErrorCode.NOT_FOUND)` | 返回 `{"code":404,"message":"资源不存在"}` |
| 3 | 分页查询 `pageNum=2&pageSize=1` | 返回第 2 页数据，`total` 正确 |

### 8.2 课后练习（课后完成并提交）

- **基础**：给 `Result` 增加 `fail(ErrorCode, String message)` 重载，支持覆盖默认文案。
- **进阶**：给 `User` 增加 `status` 字段，并加一个 `PATCH /users/{id}/status` 接口（RESTful 部分更新）。
- **挑战**：写一个自定义校验注解，校验用户名不能包含非法字符（参考 JSR-380 自定义约束）。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| `Result<T>` + `ErrorCode` | 统一 code/message/data，错误码收敛到枚举 |
| `@RestControllerAdvice` | 全局异常集中处理，业务只抛异常 |
| `@Valid` + JSR-380 | 参数校验挡在业务前 |
| RESTful | 方法表达操作，URL 表达资源，注意幂等 |
| Filter vs Interceptor | 分别位于 Servlet 层、Spring MVC 层 |
| `PageResult<T>` | 分页返回 list + total + 分页参数 |

下一节 [05-mybatis](05-mybatis.md) 会引入数据库，把内存 Map 换成真正的持久化。

> 本节遵循的规范（官方 + 阿里手册）：统一响应体；全局异常兜底；不把堆栈/内部细节暴露给前端；参数校验用 JSR-380；异常日志 `log.error("...", e)` 带堆栈；列表接口统一分页返回。
