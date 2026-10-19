# 短信验证码登录实战

> 本篇把 “手机号 + 短信验证码登录” 做成一个能直接运行、也能直接改成生产实现的完整案例：后端用 Redis 存验证码、用 JWT 签发登录态，前端用 Vue 3 + Element Plus 做登录页。后端代码在 `06-redis` 模块，前端代码在 `my-vue-app`，两边的每一步都给出可复制的命令与实测返回。

## 1. 案例说明

### 1.1 两个阶段

一次登录分成两个阶段，中间靠 Redis 传递验证码。

阶段一，发送验证码：

```
用户点击“发送验证码”
  └─ Login.vue            POST /sms/send?phone=...
       └─ SmsController.send
            └─ SmsServiceImpl.sendSms
                 ├─ 校验手机号格式
                 ├─ SET NX EX sms:send:interval:{phone}  60 秒内只放一次
                 ├─ INCR sms:send:count:{phone}          当日条数不超过 10 条
                 ├─ 生成 4 位验证码
                 ├─ 调用短信通道（mock 模式下只写日志）
                 └─ HMSET sms:captcha:{phone}  code / status=0, EXPIRE 300
       ←  {code:200, data:"短信发送成功"}
  └─ 显示验证码输入框，启动 60 秒倒计时
```

阶段二，验证码登录：

```
用户输入验证码，点击“登录”
  └─ Login.vue            POST /sms/login {phone, code}
       └─ SmsController.login
            └─ SmsServiceImpl.login
                 ├─ 校验手机号格式、验证码非空
                 ├─ HGETALL sms:captcha:{phone}
                 ├─ status 为 1 → 验证码已使用
                 ├─ code 不一致 → 错误次数 +1，够 5 次就作废
                 ├─ code 一致 → HSET status=1，保留 key 到过期
                 └─ 用 JWT 签发 token（sub 是手机号，exp 2 小时）
       ←  {code:200, data:{token, phone, message, expiresIn}}
  └─ token 写入 localStorage，跳转 /home
  └─ Home.vue             GET /user/me，带 Authorization: Bearer <token>
       ←  {code:200, data:{phone, loginTime}}
```

阶段之间的调用对照：

| 步骤 | 调用方 | 被调用方 | 输入 | 输出 |
| --- | --- | --- | --- | --- |
| 1 | `Login.vue` | `SmsController.send` | `phone` | `Result<String>` |
| 2 | `SmsController.send` | `SmsServiceImpl.sendSms` | `phone` | 无（失败抛异常） |
| 3 | `SmsServiceImpl.sendSms` | Redis | `sms:captcha:{phone}` | 写入结果 |
| 4 | `Login.vue` | `SmsController.login` | `LoginRequest` | `Result<LoginResponse>` |
| 5 | `SmsController.login` | `SmsServiceImpl.login` | `LoginRequest` | `LoginResponse` |
| 6 | `SmsServiceImpl.login` | Redis | `sms:captcha:{phone}` | 验证码与状态 |
| 7 | `Home.vue` | `SmsController`/`UserController.me` | `Authorization` 头 | `Result<UserInfo>` |
| 8 | `AuthInterceptor` | `JwtUtils.parse` | token | `Claims` |

### 1.2 几个设计选择

验证码放 Redis 而不是数据库。它天生是短时间数据，读写频繁、过期后不需要留痕，让 Redis 自己回收比在库里存 `expire_time` 再定时清理省事。key 按手机号组织，同一个手机号重复发送时后一次覆盖前一次，只保留最新的一条。

验证码存成 Hash 而不是裸值。Hash 里放两个字段，`code` 是验证码，`status` 是 `0` 未使用 / `1` 已使用。校验通过后把 `status` 改成 `1` 而不是删掉 key，这样“验证码错了”“验证码没用过”“验证码已经用过了”能给出三种不同的提示。

登录态用 JWT 而不是服务端会话。手机号、签发时间、过期时间都写进 payload 并由服务端密钥签名，服务端不存任何东西，多实例部署时不用共享会话。代价是签发出去的 token 无法撤回，需要撤回就得再维护一份黑名单。

短信通道加了一个 `mock-enabled` 开关。本地调试时把它设成 `true`，验证码只写日志和 Redis，不真发短信，既省费用也不受模板审核影响。上线前把它改回 `false`。

### 1.3 版本

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 父工程 `maven.compiler.source` |
| Spring Boot | 4.1.1 | 父工程 `spring-boot-starter-parent` |
| Spring Data Redis | 4.1.1 | 由 Boot 管理，底层客户端是 Lettuce |
| Redis | 8.x | 本案例用 10 号库 |
| jjwt | 0.13.0 | JWT 签发与校验 |
| 容联云 Java SDK | 1.0.4 | 短信模板接口 |
| Node.js | 22.18+ | `package.json` 的 engines 要求 |
| pnpm | 10.x | 也可以换成 npm |
| Vue | 3.5 | 组合式 API + `<script setup>` |
| Vite | 8.2 | 开发服务器与构建 |
| vue-router | 5.4 | 路由与守卫 |
| Element Plus | 2.14 | 表单、卡片、提示组件 |
| axios | 1.20 | HTTP 客户端 |
| TypeScript | 6.0 | 由 create-vue 选配 |
| vue-tsc | 3.3 | 类型检查 |

## 2. 环境准备

### 2.1 服务与账号

| 组件 | 检查命令 | 期望 |
| --- | --- | --- |
| JDK | `java -version` | 21 |
| Maven | `mvn -v` | 3.9.x |
| Redis | `redis-cli --no-auth-warning -a <你的密码> ping` | `PONG` |
| Node.js | `node -v` | v22.18.0 及以上 |
| pnpm | `pnpm -v` | 10.x |

Redis 的连接信息写在 `application.yml` 里，本案例用 10 号库：`host: localhost`、`port: 6379`、`password: abc123`、`database: 10`。用别的库排查问题时记得命令上带 `-n 10`，`redis-cli` 默认连的是 0 号库，看不到验证码。

短信部分需要容联云的控制台账号，取到 `accountSId`、`accountToken`、`appId` 三项，再申请一个短信模板拿到 `templateId`。模板内容里的验证码占位符是 `{1}`，发送时把验证码作为第一个参数传进去。本地调试不配这些也能跑，把 `mock-enabled` 保持默认的 `true` 即可。

### 2.2 端口约定

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| 后端 | 8006 | `application.yml` 的 `server.port` |
| 前端开发服务器 | 5173 | Vite 默认端口，被占用时会自动顺延 |

## 3. 后端工程

### 3.1 建模块

本案例的后端独立成一个模块，目录名 `06-redis`。在仓库根目录下建好目录，把自己的 `pom.xml` 放进去，然后在父工程的 `pom.xml` 里登记：

```xml
<modules>
    <!-- 其它模块略 -->
    <module>06-redis</module>
</modules>
```

子模块的父坐标指向本仓库，这样 Web、Lombok、测试三件套和依赖版本都由根 `pom.xml` 统一管理，子模块只声明自己额外需要的东西。

### 3.2 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>top.mqxu</groupId>
        <artifactId>backend-learning</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>06-redis</artifactId>

    <properties>
        <cloopen.version>1.0.4</cloopen.version>
        <jjwt.version>0.13.0</jjwt.version>
    </properties>


    <dependencies>
        <!-- Redis 客户端与 Spring Data Redis 抽象，底层默认用 Lettuce -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Lettuce 的 pool 配置由 commons-pool2 提供，缺了它池参数会静默失效 -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-pool2</artifactId>
        </dependency>

        <!-- 容联云短信 SDK -->
        <dependency>
            <groupId>com.cloopen</groupId>
            <artifactId>java-sms-sdk</artifactId>
            <version>${cloopen.version}</version>
        </dependency>

        <!-- JWT 签名与校验。api 是编译期接口，impl 与 jackson 只在运行期需要 -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

</project>
```

Spring Boot 4 里 Web 的 starter 叫 `spring-boot-starter-webmvc`，Boot 3 时代的 `spring-boot-starter-web` 仍然能下载，但已经改名。Lombok 与测试依赖来自父工程，所有模块共享。

jjwt 拆成了三个坐标：`jjwt-api` 是编译期用到的接口，`jjwt-impl` 与 `jjwt-jackson` 只在运行期需要，所以后两个标 `runtime`。少任何一个都会在启动时报 `Unable to discover any implementation` 之类的错。

### 3.3 application.yml

```yaml
server:
  port: 8006

spring:
  application:
    name: 06-redis 缓存模块
  data:
    redis:
      host: localhost
      port: 6379
      password: abc123
      database: 10
      timeout: 3s
      # Lettuce 默认多条命令复用同一个连接，池化后按连接池取用
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2
          max-wait: 1s


mqxu:
  # token 的签名密钥，生产环境从环境变量注入，不要写死在仓库里
  jwt:
    secret: sms-login-demo-secret-key-please-replace-in-production-2026
    expire-seconds: 7200
  sms:
    # true 时不调用短信 SDK，验证码只写日志与 Redis，方便本地调试
    mock-enabled: true
    # 验证码有效期（秒）
    code-expire-seconds: 300
    # 同一个手机号两次发送的最小间隔（秒），前端倒计时用同一个数字
    send-interval-seconds: 60
    # 同一个手机号一天最多发多少条
    send-daily-limit: 10
    # 同一个验证码最多允许输错几次
    max-fail-count: 5
    ccp:
      serverIp: app.cloopen.com
      port: 8883
      accountSId: 你的accountSId
      accountToken: 你的accountToken
      appId: 你的AppId
      templateId: 1
```

几个配置项的含义：

| 配置 | 作用 |
| --- | --- |
| `mqxu.jwt.secret` | token 的签名密钥。HS256 要求至少 32 字节，这里写的是 56 字节，jjwt 会据此选用 HS384 |
| `mqxu.jwt.expire-seconds` | token 有效期，2 小时 |
| `mqxu.sms.mock-enabled` | `true` 时不调用短信 SDK，验证码只写日志 |
| `mqxu.sms.code-expire-seconds` | 验证码有效期 300 秒，前端页面的“5 分钟内有效”对应这个值 |
| `mqxu.sms.send-interval-seconds` | 同一个手机号两次发送的最小间隔 60 秒，前端倒计时用同一个数字 |
| `mqxu.sms.send-daily-limit` | 同一个手机号一天最多 10 条 |
| `mqxu.sms.max-fail-count` | 同一个验证码最多输错 5 次 |

密钥和短信账号这类东西在真实项目里从环境变量注入，例如把 `mqxu.jwt.secret` 写成 `${SMS_JWT_SECRET}`，启动时用 `SMS_JWT_SECRET=xxx mvn spring-boot:run`。案例里为了少一个步骤直接写在配置文件中。

`database: 10` 指的是 Redis 的 10 号库。Redis 默认有 16 个库，用哪一号都行，但要和排查时 `redis-cli` 连的库一致。

## 4. 后端代码

模块结构：

```
06-redis
└── src/main
    ├── java/top/mqxu/redis
    │   ├── RedisApplication.java              启动类
    │   ├── common
    │   │   ├── BusinessException.java         业务异常
    │   │   ├── CommonUtils.java               手机号校验、随机验证码
    │   │   ├── ErrorCode.java                 错误码
    │   │   ├── GlobalExceptionHandler.java    全局异常处理
    │   │   ├── JwtUtils.java                  JWT 签发与校验
    │   │   ├── RedisCache.java                Redis 读写封装
    │   │   ├── RedisKeys.java                 key 命名
    │   │   └── Result.java                    统一响应体
    │   ├── config
    │   │   ├── AuthInterceptor.java           登录态拦截
    │   │   ├── CloopenConfig.java             容联云配置绑定
    │   │   ├── RedisConfig.java               RedisTemplate 序列化
    │   │   ├── SmsProperties.java             验证码策略参数
    │   │   └── WebConfig.java                 跨域与拦截器注册
    │   ├── controller
    │   │   ├── RedisController.java           模块自带的演示接口
    │   │   ├── SmsController.java             短信接口
    │   │   └── UserController.java            登录态接口
    │   ├── dto
    │   │   ├── LoginRequest.java              登录入参
    │   │   ├── LoginResponse.java             登录出参
    │   │   └── UserInfo.java                  用户信息出参
    │   └── service
    │       ├── SmsService.java                接口
    │       └── impl/SmsServiceImpl.java       实现
    └── resources
        └── application.yml
```

### 4.1 启动类

```java
package top.mqxu.redis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description RedisApplication
 **/
@SpringBootApplication
public class RedisApplication {
    public static void main(String[] args) {
        SpringApplication.run(RedisApplication.class, args);
    }
}
```

### 4.2 统一响应体与错误码

约定 HTTP 状态码恒为 200，成功与否看响应体里的 `code`。前端只需要认一个地方，不用同时处理 HTTP 状态码和业务码两套语义。

```java
package top.mqxu.redis.common;

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

```java
package top.mqxu.redis.common;

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
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试"),

    //业务异常
    PHONE_ERROR(40001, "手机号格式错误"),
    PHONE_NOT_EXIST(40002, "手机号不存在"),
    PHONE_CODE_EMPTY(40003, "验证码不能为空"),
    PHONE_CODE_ERROR(40004, "验证码错误"),
    PHONE_CODE_EXPIRED(40005, "验证码不存在或已过期"),
    PHONE_CODE_USED(40006, "验证码已使用"),
    PHONE_CODE_SEND_TOO_MANY(40007, "验证码发送过于频繁"),
    PHONE_CODE_SEND_FAILED(40008, "验证码发送失败"),
    PHONE_CODE_SEND_LIMIT(40009, "验证码发送次数已达上限"),
    PHONE_CODE_ERROR_TOO_MANY(40010, "验证码错误次数过多，请重新获取"),

    //登录态
    TOKEN_INVALID(40100, "登录状态已失效，请重新登录");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

```java
package top.mqxu.redis.common;

import lombok.Getter;

/**
 * 业务异常
 *
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

错误码按段划分：`4xxxx` 是手机号与验证码相关的业务错误，`40100` 是登录态失效。前端把 `40100` 单独处理，清掉本地 token 并回登录页。

### 4.3 全局异常处理

```java
package top.mqxu.redis.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数：{}", e.getParameterName());
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "缺少必填参数：" + e.getParameterName());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持：{}", e.getMethod());
        return Result.fail(405, "请求方法不支持：" + e.getMethod());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Result<Void> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("请求体类型不支持：{}", e.getContentType());
        return Result.fail(415, "请求体类型不支持，请使用 application/json");
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

前六个分支对应调用方传错参数或业务校验不通过，都带有明确的原因；最后一个是兜底，打完整堆栈并返回“系统繁忙”。区分开的好处是日志里留下的都是真正的服务端故障，可预期的输入错误不会淹没它。

### 4.4 配置类

`RedisConfig` 决定 Redis 里存下去的字节长什么样。

```java
package top.mqxu.redis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description RedisConfig
 **/
@Configuration
public class RedisConfig {

    /**
     * 缓存值统一用 JSON 序列化。
     */
    private GenericJacksonJsonRedisSerializer jsonSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("top.mqxu.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.math.")
                        .build())
                .enableSpringCacheNullValueSupport()
                .customize(builder -> builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS))
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJacksonJsonRedisSerializer valueSerializer = jsonSerializer();

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        return template;
    }
}
```

键用 `StringRedisSerializer` 写成普通字符串，值用 `GenericJacksonJsonRedisSerializer` 写成 JSON。JSON 序列化器要在 `builder()` 里显式打开类型信息，并给出允许还原的类型前缀白名单：不带类型信息的话，读回来只能得到 `LinkedHashMap`；白名单范围过大又会让反序列化变成注入入口。这里放开了 `top.mqxu.`、`java.lang.`、`java.util.`、`java.math.` 四个前缀，验证码用到的 `String`、`Integer` 都在其中。

```java
package top.mqxu.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description CloopenConfig
 **/
@Data
@Configuration
@ConfigurationProperties(prefix = "mqxu.sms.ccp")
public class CloopenConfig {
    private String serverIp;
    private String port;
    private String accountSID;
    private String accountToken;
    private String appId;
    private String templateId;
}
```

```java
package top.mqxu.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 验证码策略参数。这些数字前端也要用（倒计时、有效期提示），改动时两边一起改。
 *
 * @author mqxu
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mqxu.sms")
public class SmsProperties {

    /**
     * 是否使用模拟通道。true 时不调用短信 SDK，验证码只写日志和 Redis，方便本地调试
     */
    private boolean mockEnabled = true;

    /**
     * 验证码有效期，单位秒
     */
    private long codeExpireSeconds = 300;

    /**
     * 同一个手机号两次发送之间的最小间隔，单位秒
     */
    private long sendIntervalSeconds = 60;

    /**
     * 同一个手机号一天最多发送的条数
     */
    private long sendDailyLimit = 10;

    /**
     * 同一个验证码最多允许输错的次数，超过就作废
     */
    private int maxFailCount = 5;
}
```

`CloopenConfig` 绑 `mqxu.sms.ccp`，`SmsProperties` 绑 `mqxu.sms`，两个配置类各管一段，`SmsProperties` 里没有 `ccp` 字段，绑定器会忽略不认识的子键。

跨域与登录态拦截放在同一个 `WebConfig` 里，只写一套配置。

```java
package top.mqxu.redis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：跨域放行与登录态拦截。
 *
 * @author mqxu
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 开发时 Vite 的端口可能被占用后顺延，用模式匹配比写死端口方便
                .allowedOriginPatterns("http://localhost:[*]", "http://127.0.0.1:[*]")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                // 登录相关接口本身就要匿名访问，redis 是模块自带的演示接口，一并放行
                .excludePathPatterns("/sms/**", "/redis/**", "/error");
    }
}
```

跨域这里用 `allowedOriginPatterns` 而不是 `allowedOrigins("*")`：前者允许写端口通配 `[*]`，开发时 Vite 的端口被占用顺延也不用改配置；上线前把模式收敛成具体域名。

```java
package top.mqxu.redis.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.mqxu.redis.common.BusinessException;
import top.mqxu.redis.common.ErrorCode;
import top.mqxu.redis.common.JwtUtils;

/**
 * 登录态校验：读取 Authorization 头里的 Bearer token，验签通过后把载荷放进请求属性。
 *
 * @author mqxu
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * 请求属性名，controller 用 @RequestAttribute 取值
     */
    public static final String ATTR_CLAIMS = "claims";

    private static final String HEADER_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 跨域预检请求带的是 OPTIONS，浏览器不会给它加 Authorization，直接放行交给 CORS 处理
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HEADER_NAME);
        if (header == null || !header.startsWith(TOKEN_PREFIX)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        Claims claims = jwtUtils.parse(header.substring(TOKEN_PREFIX.length()));
        request.setAttribute(ATTR_CLAIMS, claims);
        return true;
    }
}
```

拦截器挂在 `/**` 上，白名单放行 `/sms/**`（登录接口本身就要匿名访问）、`/redis/**`（模块自带的演示接口）、`/error`。校验失败抛 `BusinessException`，由全局异常处理返回 `40100`，前端统一处理这一种情况。

`OPTIONS` 请求直接放行。跨域预检由浏览器发出，不会带上 `Authorization` 头，如果这里拦下来，预检就返回不了跨域响应头，浏览器会把真正的请求也一起拦掉。

### 4.5 Redis 操作封装

```java
package top.mqxu.redis.common;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description RedisKeys
 **/
public class RedisKeys {
    /**
     * 验证码 Key，存 Hash：code 是验证码，status 是 0 未使用 / 1 已使用
     */
    public static String getSmsKey(String phone) {
        return "sms:captcha:" + phone;
    }

    /**
     * 发送间隔 Key，存在即代表还在冷却中
     */
    public static String getSmsSendIntervalKey(String phone) {
        return "sms:send:interval:" + phone;
    }

    /**
     * 当日发送次数 Key
     */
    public static String getSmsSendCountKey(String phone) {
        return "sms:send:count:" + phone;
    }

    /**
     * 验证码连续输错次数 Key
     */
    public static String getSmsFailCountKey(String phone) {
        return "sms:fail:" + phone;
    }
}
```

```java
package top.mqxu.redis.common;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description RedisCache
 **/
@Component
public class RedisCache {
    /**
     * 默认过期时长为24小时，单位：秒
     */
    public final static long DEFAULT_EXPIRE = 60 * 60 * 24L;
    /**
     * 过期时长为1小时，单位：秒
     */
    public final static long HOUR_ONE_EXPIRE = (long) 60 * 60;
    /**
     * 过期时长为6小时，单位：秒
     */
    public final static long HOUR_SIX_EXPIRE = 60 * 60 * 6L;
    /**
     * 不设置过期时长
     */
    public final static long NOT_EXPIRE = -1L;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value, long expire) {
        if (expire == NOT_EXPIRE) {
            redisTemplate.opsForValue().set(key, value);
        } else {
            // 一条命令带上过期时间，避免 SET 成功后进程退出留下永不过期的 key
            redisTemplate.opsForValue().set(key, value, expire, TimeUnit.SECONDS);
        }
    }

    /**
     * 不存在才写入，返回是否写入成功。对应 Redis 的 SET key value NX EX expire
     */
    public Boolean setIfAbsent(String key, Object value, long expire) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, expire, TimeUnit.SECONDS);
    }

    public void set(String key, Object value) {
        set(key, value, DEFAULT_EXPIRE);
    }

    public Object get(String key, long expire) {
        Object value = redisTemplate.opsForValue().get(key);
        if (expire != NOT_EXPIRE) {
            expire(key, expire);
        }
        return value;
    }

    public Object get(String key) {
        return get(key, NOT_EXPIRE);
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 自增，并在第一次自增时设置过期时间，用来做固定窗口的计数
     *
     * @param key    计数的 key
     * @param expire 过期秒数
     * @return 自增后的值
     */
    public Long increment(String key, long expire) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L && expire != NOT_EXPIRE) {
            expire(key, expire);
        }
        return value;
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void delete(Collection<String> keys) {
        redisTemplate.delete(keys);
    }

    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    public Map<String, Object> hGetAll(String key) {
        HashOperations<String, String, Object> hashOperations = redisTemplate.opsForHash();
        return hashOperations.entries(key);
    }

    public void hMSet(String key, Map<String, Object> map) {
        hMSet(key, map, DEFAULT_EXPIRE);
    }

    public void hMSet(String key, Map<String, Object> map, long expire) {
        redisTemplate.opsForHash().putAll(key, map);

        if (expire != NOT_EXPIRE) {
            expire(key, expire);
        }
    }

    public void hSet(String key, String field, Object value) {
        hSet(key, field, value, DEFAULT_EXPIRE);
    }

    public void hSet(String key, String field, Object value, long expire) {
        redisTemplate.opsForHash().put(key, field, value);

        if (expire != NOT_EXPIRE) {
            expire(key, expire);
        }
    }

    public void expire(String key, long expire) {
        redisTemplate.expire(key, expire, TimeUnit.SECONDS);
    }

    public void hDel(String key, Object... fields) {
        redisTemplate.opsForHash().delete(key, fields);
    }

    public void leftPush(String key, Object value) {
        leftPush(key, value, DEFAULT_EXPIRE);
    }

    public void leftPush(String key, Object value, long expire) {
        redisTemplate.opsForList().leftPush(key, value);

        if (expire != NOT_EXPIRE) {
            expire(key, expire);
        }
    }

    public Object rightPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }
}
```

三个方法值得留意：

- `set(key, value, expire)` 用重载的 `set(key, value, expire, unit)` 一条命令下发值和过期时间。拆成 `set` 加 `expire` 两条命令的话，中间进程退出就会留下一个永不过期的 key。
- `setIfAbsent(key, value, expire)` 对应 `SET key value NX EX expire`，用来做“不存在才写入”的间隔锁，比先 `hasKey` 再 `set` 少一个竞态窗口。
- `increment(key, expire)` 在第一次自增时设置过期时间，用来做固定窗口的计数。

### 4.6 工具类与 JWT

```java
package top.mqxu.redis.common;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description CommonUtils
 **/
public class CommonUtils {

    /**
     * 校验手机号码
     *
     * @param phone 电话
     * @return boolean
     */
    public static boolean checkPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return false;
        }
        // 中国大陆手机号码的正则表达式，可能需要根据实际情况进行调整
        String regex = "^1[3-9]\\d{9}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(phone);
        return matcher.matches();
    }

    /**
     * 生成4位随机验证码，范围 [1000, 9999]。
     * nextInt 的右边界是开区间，写 9999 会让 9999 永远取不到，这里要多加一个数。
     *
     * @return int
     */
    public static int generateCode() {
        return ThreadLocalRandom.current().nextInt(1000, 10000);
    }

}
```

`generateCode` 用的是 `nextInt(1000, 10000)`。`nextInt` 的右边界是开区间，写 `9999` 会让 9999 永远取不到，四位数的完整范围要多加一个数。

```java
package top.mqxu.redis.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 的签发与校验。
 * <p>
 * payload 里放手机号（sub）、签发时间（iat）、过期时间（exp），整个串用服务端密钥签名，
 * 服务端不保存任何会话状态。
 *
 * @author mqxu
 */
@Component
public class JwtUtils {

    private final SecretKey secretKey;
    private final long expireSeconds;

    public JwtUtils(@Value("${mqxu.jwt.secret}") String secret,
                    @Value("${mqxu.jwt.expire-seconds}") long expireSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    /**
     * 签发 token
     *
     * @param phone 手机号，写进 sub
     * @return 三段式的 JWT 字符串
     */
    public String create(String phone) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(phone)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 校验签名与有效期，返回载荷。签名不对、结构不对、已过期都抛 40100。
     *
     * @param token 不带 Bearer 前缀的 token
     * @return 载荷
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }
}
```

载荷里放三样东西：`sub` 是手机号，`iat` 是签发时间，`exp` 是过期时间。签名用 `Keys.hmacShaKeyFor(secret.getBytes())` 生成的对称密钥，校验时用同一个密钥验签，`parseSignedClaims` 会顺带检查 `exp`，过期、签名不对、结构不对都会抛 `JwtException`，统一转成 `40100`。

### 4.7 DTO

```java
package top.mqxu.redis.dto;

import lombok.Data;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description LoginRequest
 **/
@Data
public class LoginRequest {
    private String phone;
    private String code;
}
```

```java
package top.mqxu.redis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description LoginResponse
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String phone;
    private String message;
    /**
     * token 有效期，单位秒
     */
    private Long expiresIn;

    public LoginResponse(String token, String phone, long expiresIn) {
        this.token = token;
        this.phone = phone;
        this.message = "登录成功";
        this.expiresIn = expiresIn;
    }
}
```

```java
package top.mqxu.redis.dto;

/**
 * 当前登录用户信息
 *
 * @author mqxu
 */
public record UserInfo(String phone, String loginTime) {
}
```

### 4.8 Service

```java
package top.mqxu.redis.service;

import top.mqxu.redis.dto.LoginRequest;
import top.mqxu.redis.dto.LoginResponse;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description SmsService
 **/
public interface SmsService {
    /**
     * 发送验证码短信。发送失败、校验不通过都通过 BusinessException 往外抛
     *
     * @param phone 手机号
     */
    void sendSms(String phone);

    /**
     * 验证码登录
     *
     * @param loginRequest 登录请求参数
     * @return 登录响应
     */
    LoginResponse login(LoginRequest loginRequest);
}
```

```java
package top.mqxu.redis.service.impl;

import com.cloopen.rest.sdk.BodyType;
import com.cloopen.rest.sdk.CCPRestSmsSDK;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.mqxu.redis.common.*;
import top.mqxu.redis.config.CloopenConfig;
import top.mqxu.redis.config.SmsProperties;
import top.mqxu.redis.dto.LoginRequest;
import top.mqxu.redis.dto.LoginResponse;
import top.mqxu.redis.service.SmsService;

import java.util.HashMap;
import java.util.Map;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description SmsServiceImpl
 **/
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsServiceImpl implements SmsService {

    /**
     * 一天有多少秒，用于“当日发送条数”的计数窗口
     */
    private static final long SECONDS_PER_DAY = 24 * 60 * 60L;
    /**
     * 验证码 Hash 里存验证码的字段
     */
    private static final String FIELD_CODE = "code";
    /**
     * 验证码 Hash 里存状态的字段
     */
    private static final String FIELD_STATUS = "status";
    private static final String STATUS_UNUSED = "0";
    private static final String STATUS_USED = "1";

    private final CloopenConfig cloopenConfig;
    private final SmsProperties smsProperties;
    private final RedisCache redisCache;
    private final JwtUtils jwtUtils;

    @Override
    public void sendSms(String phone) {
        // 1、检查 phone 格式是否正确
        if (!CommonUtils.checkPhone(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ERROR);
        }

        // 2、发送间隔：SET NX EX 只让一次请求占住这个 key，占不到说明刚发过
        String intervalKey = RedisKeys.getSmsSendIntervalKey(phone);
        Boolean acquired = redisCache.setIfAbsent(intervalKey, 1, smsProperties.getSendIntervalSeconds());
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.PHONE_CODE_SEND_TOO_MANY);
        }

        // 3、当日条数：计数器的过期时间在第一次自增时设置
        Long sentToday = redisCache.increment(RedisKeys.getSmsSendCountKey(phone), SECONDS_PER_DAY);
        if (sentToday != null && sentToday > smsProperties.getSendDailyLimit()) {
            throw new BusinessException(ErrorCode.PHONE_CODE_SEND_LIMIT);
        }

        // 4、调用工具方法，生成随机验证码
        int code = CommonUtils.generateCode();

        // 5、先发短信，成功之后再写 Redis：用户没收到短信时不留验证码占位
        if (!send(phone, code)) {
            // 发送失败，把冷却的 key 撤掉，允许立刻重试
            redisCache.delete(intervalKey);
            throw new BusinessException(ErrorCode.PHONE_CODE_SEND_FAILED);
        }

        // 6、验证码与状态一起存成 Hash，方便区分“没用过”和“已经用过了”
        Map<String, Object> value = new HashMap<>();
        value.put(FIELD_CODE, String.valueOf(code));
        value.put(FIELD_STATUS, STATUS_UNUSED);
        redisCache.hMSet(RedisKeys.getSmsKey(phone), value, smsProperties.getCodeExpireSeconds());

        log.info("验证码已下发，手机号 {}", phone);
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        String phone = loginRequest.getPhone();
        String inputCode = loginRequest.getCode();

        // 1.校验手机号格式
        if (!CommonUtils.checkPhone(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ERROR);
        }

        // 2.校验验证码是否为空
        if (inputCode == null || inputCode.isBlank()) {
            throw new BusinessException(ErrorCode.PHONE_CODE_EMPTY);
        }

        // 3.从 Redis 中获取验证码，先判空再取值
        String redisKey = RedisKeys.getSmsKey(phone);
        Map<String, Object> cached = redisCache.hGetAll(redisKey);
        if (cached.isEmpty()) {
            throw new BusinessException(ErrorCode.PHONE_CODE_EXPIRED);
        }

        // 4.已经用过的验证码不能再登录一次
        if (STATUS_USED.equals(cached.get(FIELD_STATUS))) {
            throw new BusinessException(ErrorCode.PHONE_CODE_USED);
        }

        // 5.验证码不匹配，连续错到上限就作废，防止四位验证码被逐个试
        if (!inputCode.equals(cached.get(FIELD_CODE))) {
            Long failCount = redisCache.increment(
                    RedisKeys.getSmsFailCountKey(phone), smsProperties.getCodeExpireSeconds());
            if (failCount != null && failCount >= smsProperties.getMaxFailCount()) {
                redisCache.delete(redisKey);
                redisCache.delete(RedisKeys.getSmsFailCountKey(phone));
                log.warn("手机号 {} 验证码连续输错 {} 次，已作废", phone, failCount);
                throw new BusinessException(ErrorCode.PHONE_CODE_ERROR_TOO_MANY);
            }
            throw new BusinessException(ErrorCode.PHONE_CODE_ERROR);
        }

        // 6.验证通过：把状态改成已使用并保留到过期，重复提交能给出明确提示
        redisCache.hSet(redisKey, FIELD_STATUS, STATUS_USED, smsProperties.getCodeExpireSeconds());
        redisCache.delete(RedisKeys.getSmsFailCountKey(phone));

        // 7.签发 token 并返回登录信息
        String token = jwtUtils.create(phone);

        log.info("手机号 {} 登录成功", phone);
        return new LoginResponse(token, phone, jwtUtils.getExpireSeconds());
    }

    /**
     * 调用短信通道。mockEnabled 为 true 时走模拟通道，验证码只写日志，不真正发送
     *
     * @param phone 手机号
     * @param code  验证码
     * @return 是否发送成功
     */
    private boolean send(String phone, int code) {
        if (smsProperties.isMockEnabled()) {
            log.info("短信通道为模拟模式，手机号 {}，验证码 {}", phone, code);
            return true;
        }

        CCPRestSmsSDK sdk = new CCPRestSmsSDK();
        sdk.init(cloopenConfig.getServerIp(), cloopenConfig.getPort());
        sdk.setAccount(cloopenConfig.getAccountSID(), cloopenConfig.getAccountToken());
        sdk.setAppId(cloopenConfig.getAppId());
        sdk.setBodyType(BodyType.Type_JSON);

        String[] dataS = {String.valueOf(code)};
        HashMap<String, Object> result = sdk.sendTemplateSMS(phone, cloopenConfig.getTemplateId(), dataS);

        // 状态码为 000000 表示发送成功，其余情况把错误码与错误信息打出来
        if ("000000".equals(result.get("statusCode"))) {
            log.info("短信发送成功，手机号 {}", phone);
            return true;
        }
        log.warn("短信发送失败，手机号 {}，状态码 {}，状态信息 {}",
                phone, result.get("statusCode"), result.get("statusMsg"));
        return false;
    }
}
```

发送流程里有三处顺序值得留意：

先取间隔锁，再做其它事。锁占不到就说明 60 秒内已经发过，直接返回 40007，后面的生成与发送都不做。

先发短信，再写 Redis。反过来的话，短信没发出去而 Redis 里留着一个用户永远收不到的验证码，白占 300 秒。

发送失败时把间隔锁删掉。否则用户因为通道故障没收到短信，还要再等 60 秒才能重试。

登录流程里，校验通过后是把 `status` 置为 `1` 而不是删除 key，这样重复提交同一个验证码会返回 40006 而不是 40005，用户能分清“这个码用过了”和“这个码没发过”。

### 4.9 Controller

```java
package top.mqxu.redis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.mqxu.redis.common.Result;
import top.mqxu.redis.dto.LoginRequest;
import top.mqxu.redis.dto.LoginResponse;
import top.mqxu.redis.service.SmsService;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description SmsController
 **/
@RestController
@RequestMapping("/sms")
@RequiredArgsConstructor
public class SmsController {
    private final SmsService smsService;

    @PostMapping("/send")
    public Result<String> send(@RequestParam String phone) {
        smsService.sendSms(phone);
        return Result.ok("短信发送成功");
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        return Result.ok(smsService.login(loginRequest));
    }
}
```

```java
package top.mqxu.redis.controller;

import io.jsonwebtoken.Claims;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.redis.common.Result;
import top.mqxu.redis.config.AuthInterceptor;
import top.mqxu.redis.dto.UserInfo;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 需要登录态的接口。token 由 AuthInterceptor 校验，这里只管取载荷。
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private static final DateTimeFormatter LOGIN_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 返回当前 token 对应的用户信息，前端首页用它替代读 localStorage
     */
    @GetMapping("/me")
    public Result<UserInfo> me(@RequestAttribute(AuthInterceptor.ATTR_CLAIMS) Claims claims) {
        String loginTime = LOGIN_TIME_FORMATTER.format(
                claims.getIssuedAt().toInstant().atZone(ZoneId.systemDefault()));
        return Result.ok(new UserInfo(claims.getSubject(), loginTime));
    }
}
```

`UserController.me` 不需要自己解析请求头。`AuthInterceptor` 验签后把载荷放进了请求属性，用 `@RequestAttribute` 取即可。签发时间来自 token 的 `iat`，所以返回的 `loginTime` 就是这次登录的时刻，不必额外存储。

### 4.10 模块自带的演示接口

`RedisController` 是模块原有的基础类型操作接口，用来验证 Redis 连接与序列化配置是否正常。它在白名单里放行，不带 token 也能访问。

```java
package top.mqxu.redis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description RedisController
 **/
@RestController
@RequestMapping("/redis")
@RequiredArgsConstructor
public class RedisController {
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * String：最基础的类型，文本、数字、序列化后的 JSON 都放它里面
     */
    @PostMapping("/string")
    public Map<String, Object> setString(@RequestParam String key,
                                         @RequestParam String value,
                                         // 默认失效时间为 30 秒
                                         @RequestParam(defaultValue = "30") long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("value", value);
        result.put("ttlSeconds", stringRedisTemplate.getExpire(key));
        return result;
    }
}
```

## 5. 接口清单与实测

### 5.1 启动

```bash
mvn -pl test-redis1 spring-boot:run
```

启动日志里能看到端口和拦截器生效：

```
Tomcat started on port 8006 (http)
Started RedisApplication in 1.571 seconds
```

下面所有命令里的 `BASE` 都指 `http://127.0.0.1:8006`，端口改了换成新的即可。

```bash
BASE=http://127.0.0.1:8006
```

本机的 `curl` 如果配了代理，访问 127.0.0.1 会被代理截走，所有命令加上 `--noproxy '*'`。下面为了简洁省略了这个参数，实际执行时按自己的环境决定加不加。

### 5.2 发送验证码

```bash
curl -s -X POST "$BASE/sms/send?phone=13951905171"
# {"code":200,"data":"短信发送成功","message":"success"}
```

Redis 里随即出现三个 key：

```bash
redis-cli --no-auth-warning -a abc123 -n 10 hgetall sms:captcha:13951905171
# 1) "code"
# 2) "\"9275\""
# 3) "status"
# 4) "\"0\""

redis-cli --no-auth-warning -a abc123 -n 10 ttl sms:captcha:13951905171
# 300
redis-cli --no-auth-warning -a abc123 -n 10 ttl sms:send:interval:13951905171
# 60
redis-cli --no-auth-warning -a abc123 -n 10 get sms:send:count:13951905171
# 1
redis-cli --no-auth-warning -a abc123 -n 10 ttl sms:send:count:13951905171
# 86400
```

`hgetall` 的输出里值带着一层引号，例如 `"\"9275\""`，这是 JSON 序列化器写下去的结果。用 `hget` 取单个字段时同样要留意，脚本里通常用 `tr -d '"'` 去掉外层引号。

mock 模式下验证码会打在应用日志里，方便本地取用：

```
短信通道为模拟模式，手机号 13951905171，验证码 9275
验证码已下发，手机号 13951905171
```

### 5.3 验证码登录

```bash
curl -s -X POST "$BASE/sms/login" \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13951905171","code":"9275"}'
# {"code":200,"data":{"expiresIn":7200,"message":"登录成功","phone":"13951905171","token":"eyJhbGciOiJIUzM4NCJ9...."},"message":"success"}
```

`token` 由三段组成：头部声明算法，中间是载荷，第三段是签名。

```bash
# 头部（Base64URL 解码后）
# {"alg":"HS384"}
# 载荷
# {"sub":"13951905171","iat":1792131723,"exp":1792138923}
```

算法是 HS384 而不是 HS256，因为签名密钥是 56 字节，jjwt 会按密钥长度选用它支持的最强 HMAC 算法。

登录成功后，同一个验证码不能再登录一次：

```bash
curl -s -X POST "$BASE/sms/login" -H 'Content-Type: application/json' \
  -d '{"phone":"13951905171","code":"9275"}'
# {"code":40006,"data":null,"message":"验证码已使用"}
```

此时 Redis 里的 key 还在，只是状态变了：

```bash
redis-cli --no-auth-warning -a abc123 -n 10 hgetall sms:captcha:13951905171
# 1) "code"
# 2) "\"9275\""
# 3) "status"
# 4) "\"1\""
redis-cli --no-auth-warning -a abc123 -n 10 ttl sms:captcha:13951905171
# 还剩 200 多秒
```

### 5.4 登录态接口

```bash
TOKEN=<上一步返回的 token>
curl -s "$BASE/user/me" -H "Authorization: Bearer $TOKEN"
# {"code":200,"data":{"phone":"13951905171","loginTime":"2026-10-16 14:22:03"},"message":"success"}
```

不带 token、token 被改过、token 已过期，三种情况返回同一个业务码：

```bash
curl -s "$BASE/user/me"
# {"code":40100,"data":null,"message":"登录状态已失效，请重新登录"}

curl -s "$BASE/user/me" -H "Authorization: Bearer abc.def.ghi"
# {"code":40100,"data":null,"message":"登录状态已失效，请重新登录"}
```

想验证“签名合法但已过期”，可以按同样的密钥和算法自己造一个 `exp` 在过去的 token：

```python
import base64, hmac, hashlib, json, time

b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=")
secret = b"sms-login-demo-secret-key-please-replace-in-production-2026"

header = b64(json.dumps({"alg": "HS384"}, separators=(",", ":")).encode())
iat = int(time.time()) - 10000
payload = b64(json.dumps({"sub": "13951905171", "iat": iat, "exp": iat + 3600},
                         separators=(",", ":")).encode())
signature = b64(hmac.new(secret, header + b"." + payload, hashlib.sha384).digest())
print((header + b"." + payload + b"." + signature).decode())
```

用它去请求 `/user/me`，返回同样是 40100，说明校验的是过期时间而不只是签名。

### 5.5 异常与边界返回

下表 22 行全部来自实测，`BASE` 为 `http://127.0.0.1:8006`。

| # | 场景 | 请求 | 返回 |
| --- | --- | --- | --- |
| 1 | 正常发送 | `POST /sms/send?phone=13951905171` | `{"code":200,"data":"短信发送成功","message":"success"}` |
| 2 | 60 秒内重复发送 | 同上 | `{"code":40007,"data":null,"message":"验证码发送过于频繁"}` |
| 3 | 手机号格式错误 | `POST /sms/send?phone=abc` | `{"code":40001,"data":null,"message":"手机号格式错误"}` |
| 4 | 手机号为空串 | `POST /sms/send?phone=` | `{"code":40001,"data":null,"message":"手机号格式错误"}` |
| 5 | 不带 phone 参数 | `POST /sms/send` | `{"code":400,"data":null,"message":"缺少必填参数：phone"}` |
| 6 | 当天第 11 次发送 | 清掉间隔 key 后连发 | `{"code":40009,"data":null,"message":"验证码发送次数已达上限"}` |
| 7 | 从没发过验证码就登录 | `POST /sms/login` `{"phone":"13700000000","code":"1234"}` | `{"code":40005,"data":null,"message":"验证码不存在或已过期"}` |
| 8 | 验证码错误 | `code` 写 `0000` | `{"code":40004,"data":null,"message":"验证码错误"}` |
| 9 | 验证码为空 | `code` 写 `""` | `{"code":40003,"data":null,"message":"验证码不能为空"}` |
| 10 | 登录时手机号格式错误 | `phone` 写 `abc` | `{"code":40001,"data":null,"message":"手机号格式错误"}` |
| 11 | 连错到第 5 次 | 连续 5 次错误验证码 | 第 5 次返回 `{"code":40010,"data":null,"message":"验证码错误次数过多，请重新获取"}` |
| 12 | 验证码正确 | 填 Redis 里的值 | `{"code":200,"data":{...},"message":"success"}` |
| 13 | 同一个验证码再用一次 | 同上 | `{"code":40006,"data":null,"message":"验证码已使用"}` |
| 14 | 方法不匹配 | `GET /sms/login` | `{"code":405,"data":null,"message":"请求方法不支持：GET"}` |
| 15 | 请求体类型不对 | `Content-Type: text/plain` | `{"code":415,"data":null,"message":"请求体类型不支持，请使用 application/json"}` |
| 16 | 请求体不是合法 JSON | `-d '{phone:}'` | `{"code":400,"data":null,"message":"请求体格式错误"}` |
| 17 | 未登录访问受保护接口 | `GET /user/me` | `{"code":40100,"data":null,"message":"登录状态已失效，请重新登录"}` |
| 18 | 伪造 token | `Authorization: Bearer abc.def.ghi` | `{"code":40100,...}` |
| 19 | 篡改 token 的签名 | 改掉 token 最后一位 | `{"code":40100,...}` |
| 20 | 签名合法但已过期 | 自己造一个 `exp` 在过去的 token | `{"code":40100,...}` |
| 21 | 登录后访问 | 带正确 token | `{"code":200,"data":{"phone":"13951905171","loginTime":"2026-10-16 14:22:03"},"message":"success"}` |
| 22 | 带 token 访问不存在的路径 | `GET /nothing/here` | `{"code":404,"data":null,"message":"资源不存在"}` |

第 5 行与第 3、4 行的差别在于参数是“没传”还是“传了个空串”：没传参数由框架抛出 `MissingServletRequestParameterException`，走 400 分支；传空串是业务校验的范围，走 40001。第 22 行说明不带 token 访问不存在的路径会先被拦截器挡下返回 40100，鉴权发生在路由解析之前，带上 token 才会走到 404。

### 5.6 跨域

前端从 5173 访问 8006 属于跨域，浏览器在真正发请求之前会发一次 `OPTIONS` 预检：

```bash
curl -i -X OPTIONS "$BASE/sms/login" \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```

```
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
Access-Control-Allow-Headers: content-type
Access-Control-Max-Age: 3600
Content-Length: 0
```

`Access-Control-Max-Age: 3600` 表示这次预检的结果会被浏览器缓存一小时。调试跨域问题时，如果改了后端配置却发现浏览器行为没变，先看这里，或者在开发者工具里勾掉缓存。预检请求本身不带 `Authorization`，这也是拦截器要放行 `OPTIONS` 的原因。

## 6. 前端工程

### 6.1 生成骨架

用 create-vue 生成 Vue 3 + Vite + TypeScript 骨架。命令后面跟特性开关，一次给全就不会进交互问答：

```bash
pnpm create vue@latest my-vue-app --ts --router
```

等价地不加参数逐项回答，选 `TypeScript: Yes`、`Vue Router: Yes`，其余选 `No`。可选的开关还有 `--pinia`、`--vitest`、`--cypress`、`--playwright`、`--eslint`、`--prettier`、`--jsx`，本案例用不到。

生成之后进目录装依赖：

```bash
cd my-vue-app
pnpm install
```

### 6.2 装本案例用到的三个包

```bash
pnpm add axios element-plus @element-plus/icons-vue
```

`element-plus` 是组件库，`@element-plus/icons-vue` 是图标集，`axios` 是 HTTP 客户端。

create-vue 生成的 `package.json` 里已经带好类型检查相关的依赖（`typescript`、`vue-tsc`、`@vue/tsconfig`、`@tsconfig/node24`、`@types/node`），装完的样子：

create-vue 生成的 `package.json` 里已经带好类型检查相关的依赖（`typescript`、`vue-tsc`、`@vue/tsconfig`、`@tsconfig/node24`、`@types/node`）。出厂的 `build` 脚本用 `run-p` 并行跑类型检查和打包，需要额外的 `npm-run-all2`，这里简化成先后执行，并把类型检查单独留一个脚本：

```json
{
  "name": "my-vue-app",
  "version": "0.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview",
    "type-check": "vue-tsc -b"
  },
  "dependencies": {
    "@element-plus/icons-vue": "^2.3.2",
    "axios": "^1.20.0",
    "element-plus": "^2.14.7",
    "vue": "^3.5.42",
    "vue-router": "^5.3.1"
  },
  "devDependencies": {
    "@tsconfig/node24": "^24.0.5",
    "@types/node": "^24.13.4",
    "@vitejs/plugin-vue": "^6.0.8",
    "@vue/tsconfig": "^0.9.1",
    "typescript": "~6.0.0",
    "vite": "^8.2.2",
    "vite-plugin-vue-devtools": "^8.2.1",
    "vue-tsc": "^3.3.11"
  },
  "engines": {
    "node": "^22.18.0 || >=24.12.0"
  }
}
```

> `vue-tsc -b && vite build` 可以先做类型检查再打包，类型错误会让构建直接失败，比运行时才发现问题要早。

### 6.3 清理模板自带的示例代码

生成出来的骨架带了一些演示内容，本案例不用，删掉它们：

```bash
rm -rf src/components src/assets src/views/HomeView.vue src/views/AboutView.vue
```

`src/main.ts` 里 `import './assets/main.css'` 这一行也去掉，全局样式改成在 `App.vue` 里写。剩下的目录结构就是本案例的形态，`views`、`api`、`utils`、`types`、`config` 五个目录下的文件在下一节逐个给出。

```bash
pnpm dev     # 开发服务器，默认 5173
pnpm build   # 类型检查 + 打包到 dist
pnpm type-check   # 只做类型检查
```

### 6.4 环境变量

后端地址放在 `.env` 里，改地址不用动代码。Vite 只会把 `VITE_` 前缀的变量暴露给前端代码。

```bash
# 后端接口地址，Vite 启动时读取，改地址不用动代码
VITE_API_BASE_URL=http://localhost:8006
```

```ts
/// <reference types="vite/client" />

interface ImportMetaEnv {
    /** 后端接口地址，在 .env 里配置 */
    readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
```

`env.d.ts` 里的 `ImportMetaEnv` 是给 `import.meta.env.VITE_API_BASE_URL` 补类型的，缺了它这个表达式在类型检查时会被视为 `any`。

### 6.5 构建配置

```ts
import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import vueDevTools from "vite-plugin-vue-devtools";

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});
```

`@` 指向 `src`，`tsconfig.app.json` 里的 `paths` 与这里的 `alias` 两处要保持一致：一个是给编辑器与类型检查看的，一个是给打包器看的。

```jsonc
{
  "extends": "@vue/tsconfig/tsconfig.dom.json",
  "include": ["env.d.ts", "src/**/*", "src/**/*.vue"],
  "exclude": ["src/**/__tests__/*"],
  "compilerOptions": {
    // Extra safety for array and object lookups, but may have false positives.
    "noUncheckedIndexedAccess": true,

    // Path mapping for cleaner imports.
    "paths": {
      "@/*": ["./src/*"]
    },

    // `vue-tsc --build` produces a .tsbuildinfo file for incremental type-checking.
    // Specified here to keep it out of the root directory.
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo"
  }
}
```

### 6.6 目录结构

```
my-vue-app
├── .env                        后端地址
├── env.d.ts                    环境变量类型
├── index.html                  页面入口
├── package.json
├── tsconfig.json               工程引用根
├── tsconfig.app.json           应用侧编译选项
├── tsconfig.node.json          构建脚本侧编译选项
├── vite.config.ts
└── src
    ├── App.vue                 根组件
    ├── main.ts                 应用入口
    ├── api
    │   └── auth.ts             接口定义
    ├── config
    │   └── index.ts            前后端要对齐的常量
    ├── router
    │   └── index.ts            路由与守卫
    ├── types
    │   └── api.ts              接口相关的类型
    ├── utils
    │   ├── auth.ts             登录态读写
    │   └── request.ts          axios 封装
    └── views
        ├── Home.vue            首页
        └── Login.vue           登录页
```

## 7. 前端代码

### 7.1 入口

```html
<!DOCTYPE html>
<html lang="">
  <head>
    <meta charset="UTF-8">
    <link rel="icon" href="/favicon.ico">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vite App</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

```ts
import { createApp } from 'vue';
import ElementPlus from 'element-plus';
// @ts-ignore
import 'element-plus/dist/index.css';
import App from './App.vue';

// 引入路由
import router from './router'

// 图标在各自页面里按需引入，这里不再全量注册
const app = createApp(App);

app.use(ElementPlus);

// 挂载路由
app.use(router);
app.mount("#app");
```

```vue
<template>
  <router-view/>
</template>

<script setup lang="ts">
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  width: 100%;
  height: 100%;
}
</style>
```

模板里用到的图标在各自页面里按需引入，`main.ts` 不再全量注册图标，避免几百个组件被打进包里。Element Plus 本身仍是整包引入，打包后 vendor 文件约 1 MB，gzip 之后 320 KB 左右；需要进一步压缩体积时，换成按需引入的组件插件即可。

### 7.2 常量

```ts
/**
 * 前后端需要对齐的几个数字放在这里，改一处就能同时影响倒计时与提示文案。
 */

/** 后端接口地址，来自 .env 的 VITE_API_BASE_URL */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8006";

/** 重发验证码的间隔（秒），与后端 mqxu.sms.send-interval-seconds 一致 */
export const SEND_INTERVAL_SECONDS = 60;

/** 验证码有效期（分钟），与后端 mqxu.sms.code-expire-seconds 一致 */
export const CODE_TTL_MINUTES = 5;

/** 登录态失效的业务码，与后端 ErrorCode.TOKEN_INVALID 一致 */
export const CODE_TOKEN_INVALID = 40100;
```

`SEND_INTERVAL_SECONDS` 与 `CODE_TTL_MINUTES` 要和后端 `mqxu.sms` 下的两个数字保持一致，倒计时和提示文案都从这两个常量取值，改的时候只改一处。

### 7.3 类型

```ts
/**
 * 与后端 top.mqxu.redis.common.Result 一一对应的响应体。
 * 字段名是 message，后端不叫 msg，写错拿到的永远是 undefined。
 */
export interface ApiResponse<T = unknown> {
    code: number;
    message: string;
    data: T;
}

/** POST /sms/login 的入参 */
export interface LoginRequest {
    phone: string;
    code: string;
}

/** POST /sms/login 的 data 部分 */
export interface LoginResult {
    token: string;
    phone: string;
    message: string;
    /** token 有效期，单位秒 */
    expiresIn: number;
}

/** GET /user/me 的 data 部分 */
export interface UserInfo {
    phone: string;
    loginTime: string;
}
```

`ApiResponse` 的字段名与后端 `Result` 完全对应：`code`、`message`、`data`。后端叫 `message`，前端就不能写 `msg`，写错的话取到的永远是 `undefined`，而且不会有编译错误。

### 7.4 登录态读写

```ts
/**
 * 登录态的读写集中在这里，localStorage 的 key 只在这个文件里出现，
 * 拦截器、路由守卫、页面都从这里取。
 */

const TOKEN_KEY = "token";

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}

export function saveToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
    localStorage.removeItem(TOKEN_KEY);
}
```

localStorage 的 key 只在这个文件里出现，拦截器、路由守卫、页面都从这里取，换存储介质时只改这一处。

### 7.5 axios 封装

```ts
/**
 * axios 封装：统一 baseURL、统一带 token、统一按业务码判断成败。
 * 后端约定 HTTP 状态码恒为 200，成功与否看响应体里的 code。
 */
import axios, {
    type AxiosError,
    type AxiosInstance,
    type AxiosResponse,
    type InternalAxiosRequestConfig,
} from "axios";
import { ElMessage } from "element-plus";
import { API_BASE_URL, CODE_TOKEN_INVALID } from "@/config";
import { clearToken, getToken } from "@/utils/auth";
import type { ApiResponse } from "@/types/api";

const request: AxiosInstance = axios.create({
    baseURL: API_BASE_URL,
    timeout: 10000,
    headers: {
        "Content-Type": "application/json;charset=UTF-8",
    },
});

/**
 * 请求拦截器：有 token 就带上，后端 AuthInterceptor 靠这个头认人
 */
request.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        const token = getToken();
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error),
);

/**
 * 响应拦截器：
 * 业务码 200 原样返回；40100 清掉登录态并回登录页；其余把后端的 message 包成 Error 抛出。
 * 调用方在 catch 里拿到的就是后端文案，不需要自己判断 code。
 */
request.interceptors.response.use(
    (response: AxiosResponse<ApiResponse>) => {
        const body = response.data;
        if (body.code === 200) {
            return response;
        }

        const message = body.message || "请求失败";
        if (body.code === CODE_TOKEN_INVALID) {
            clearToken();
            ElMessage.warning(message);
            // 用 replace 换掉当前页，避免用户点后退又回到失效的页面
            if (window.location.pathname !== "/login") {
                window.location.replace("/login");
            }
        }
        return Promise.reject(new Error(message));
    },
    (error: AxiosError) => {
        // 走到这里说明请求没能拿到正常响应：服务没起、断网、超时、HTTP 5xx
        const message = error.response
            ? `请求失败：HTTP ${error.response.status}`
            : "网络连接失败，请确认后端服务已启动";
        ElMessage.error(message);
        return Promise.reject(new Error(message));
    },
);

export default request;
```

响应拦截器承担三件事：业务码为 200 时原样返回；业务码为 `40100` 时清掉 token 并跳登录页；其余情况把后端的 `message` 包成 `Error` 抛出。页面里的 `catch` 拿到的就是后端文案，不需要自己判断 `code`。

区分两类失败很重要。业务失败（验证码错误、发送太频繁）走的是 HTTP 200 加非 200 的业务码，此时把后端的文案原样交给页面，页面上显示的就是“验证码错误”。传输失败（服务没起、断网、超时）由第二个回调处理，文案是“网络连接失败，请确认后端服务已启动”。两者分开之后，用户看到“验证码错误”就不会以为是网络问题。

### 7.6 接口定义

```ts
import request from "@/utils/request";
import type { ApiResponse, LoginRequest, LoginResult, UserInfo } from "@/types/api";

export const authApi = {
    /** 发送短信验证码 */
    async sendSms(phone: string): Promise<ApiResponse<string>> {
        const res = await request.post<ApiResponse<string>>("/sms/send", null, {
            params: { phone },
        });
        return res.data;
    },

    /** 短信验证码登录 */
    async login(data: LoginRequest): Promise<ApiResponse<LoginResult>> {
        const res = await request.post<ApiResponse<LoginResult>>("/sms/login", data);
        return res.data;
    },

    /** 查询当前登录用户，token 由请求拦截器带上 */
    async me(): Promise<ApiResponse<UserInfo>> {
        const res = await request.get<ApiResponse<UserInfo>>("/user/me");
        return res.data;
    },
};
```

查询参数用 `params` 传，axios 会负责拼接与转义，比手写模板字符串安全。

### 7.7 路由与守卫

```ts
import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";
import { getToken } from "@/utils/auth";

const routes: Array<RouteRecordRaw> = [
    {
        path: "/",
        redirect: "/home",
    },
    {
        path: "/login",
        name: "Login",
        component: () => import("@/views/Login.vue"),
    },
    {
        path: "/home",
        name: "Home",
        component: () => import("@/views/Home.vue"),
        meta: {requiresAuth: true},
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

// 路由守卫只做“本地有没有 token”这一层拦截，token 是否真的有效由 /user/me 判断
router.beforeEach((to) => {
    if (to.meta.requiresAuth && !getToken()) {
        return "/login";
    }
    return true;
});

export default router;
```

守卫只做 “本地有没有 token” 这一层拦截，用来避免未登录时直接进首页。token 是否真的有效由 `/user/me` 判断，服务端返回 40100 时拦截器会清掉 token 并回登录页。两层分工明确，守卫里不需要解析 token。

### 7.8 登录页

```vue
<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <el-icon>
            <Iphone/>
          </el-icon>
          <span class="title">短信登录</span>
        </div>
      </template>

      <el-form
          @submit.prevent="login"
          :model="formData"
          ref="loginFormRef"
          :rules="rules"
      >
        <el-form-item prop="phone">
          <el-input
              v-model="phone"
              placeholder="请输入手机号"
              size="large"
              :prefix-icon="Iphone"
              maxlength="11"
              show-word-limit
          />
        </el-form-item>

        <el-form-item>
          <el-button
              type="primary"
              @click="sendSms"
              :disabled="isCountingDown"
              :loading="isSendingSms"
              size="large"
              style="width: 100%"
          >
            <el-icon>
              <Message/>
            </el-icon>
            {{ isCountingDown ? `${countdown}秒后重发` : '发送验证码' }}
          </el-button>
        </el-form-item>

        <div v-show="isCodeSent">
          <el-form-item prop="code">
            <el-input
                v-model="code"
                placeholder="请输入验证码"
                size="large"
                :prefix-icon="Lock"
                maxlength="4"
                show-word-limit
            />
          </el-form-item>

          <el-form-item>
            <el-button
                type="success"
                @click="login"
                :loading="isLoggingIn"
                size="large"
                style="width: 100%"
            >
              <el-icon>
                <Check/>
              </el-icon>
              登录
            </el-button>
          </el-form-item>
        </div>

        <el-alert
            v-if="errorMessage"
            :title="errorMessage"
            type="error"
            :closable="false"
            show-icon
        />

        <el-alert
            v-if="successMessage"
            :title="successMessage"
            type="success"
            :closable="false"
            show-icon
        />
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import {onUnmounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {ElMessage, type FormInstance, type FormRules} from 'element-plus';
import {Check, Iphone, Lock, Message} from '@element-plus/icons-vue';
import {CODE_TTL_MINUTES, SEND_INTERVAL_SECONDS} from '@/config';
import {saveToken} from '@/utils/auth';
import type {LoginRequest} from '@/types/api';
import {authApi} from '@/api/auth';

const router = useRouter();

const phone = ref("");
const code = ref("");
const errorMessage = ref("");
const successMessage = ref("");
const isCountingDown = ref(false);
const countdown = ref(SEND_INTERVAL_SECONDS);
const isSendingSms = ref(false);
const isLoggingIn = ref(false);
const isCodeSent = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;

const loginFormRef = ref<FormInstance>();

const formData = reactive({
  phone,
  code,
});

const rules: FormRules = {
  phone: [
    {required: true, message: "请输入手机号", trigger: "blur"},
    {
      pattern: /^1[3-9]\d{9}$/,
      message: "请输入正确的手机号",
      trigger: "blur",
    },
  ],
  code: [
    {required: true, message: "请输入验证码", trigger: "blur"},
    {len: 4, message: "验证码长度为4位", trigger: "blur"},
  ],
};

const sendSms = async () => {
  if (!loginFormRef.value || isCountingDown.value) return;

  const valid = await loginFormRef.value
      .validateField("phone")
      .catch(() => false);
  if (!valid) return;

  errorMessage.value = "";
  successMessage.value = "";
  isSendingSms.value = true;

  try {
    await authApi.sendSms(phone.value);
    successMessage.value = `验证码已发送，${CODE_TTL_MINUTES} 分钟内有效`;
    ElMessage.success("验证码已发送，请查收！");
    isCodeSent.value = true;
    startCountdown();
  } catch (error) {
    // 响应拦截器把后端的 message 包成了 Error，这里直接用
    errorMessage.value = (error as Error).message || "发送失败，请重试。";
  } finally {
    isSendingSms.value = false;
  }
};

const login = async () => {
  if (!loginFormRef.value) return;

  const valid = await loginFormRef.value.validate().catch(() => false);
  if (!valid) return;

  errorMessage.value = "";
  successMessage.value = "";
  isLoggingIn.value = true;

  try {
    const loginData: LoginRequest = {
      phone: phone.value,
      code: code.value,
    };
    const {data} = await authApi.login(loginData);

    saveToken(data.token);
    successMessage.value = data.message;
    ElMessage.success(data.message);
    // 跳转到首页
    router.push("/home");
  } catch (error) {
    errorMessage.value = (error as Error).message || "登录失败，请重试。";
  } finally {
    isLoggingIn.value = false;
  }
};

const startCountdown = () => {
  clearInterval(timer);
  isCountingDown.value = true;
  countdown.value = SEND_INTERVAL_SECONDS;
  timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      clearInterval(timer);
      timer = undefined;
      isCountingDown.value = false;
    }
  }, 1000);
};

// 用户中途离开页面时把定时器清掉，避免它继续跑
onUnmounted(() => {
  clearInterval(timer);
});
</script>

<style scoped>
.login-container {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #cbe9ed 0%, #60cddb 100%);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  border-radius: 15px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: #409eff;
}

.card-header .el-icon {
  font-size: 24px;
}

.title {
  background: linear-gradient(45deg, #409eff, #67c23a);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.3s ease;
}

:deep(.el-button:hover) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
}

:deep(.el-alert) {
  border-radius: 8px;
  margin-top: 10px;
}

:deep(.el-card__header) {
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  border-bottom: 1px solid #e4e7ed;
  padding: 20px;
}

:deep(.el-card__body) {
  padding: 30px;
}
</style>
```

页面逻辑里有四个细节：

手机号初始值是空串，交付前不留测试号码。

发验证码的按钮在倒计时期间既置灰又提前 `return`，靠页面状态和函数内的判断两层保证不会重复提交。

倒计时在新一轮开始时先 `clearInterval`，在计数归零、组件卸载时也各清一次，避免用户中途离开页面后定时器还在跑。

登录成功与失败都读后端的 `message`：成功提示“登录成功”，失败提示“验证码错误”“验证码已使用”这些具体原因。

### 7.9 首页

```vue
<template>
  <div class="home-container">
    <el-container>
      <el-header class="header">
        <div class="header-content">
          <h2 class="title">欢迎来到首页</h2>
          <div class="user-info">
            <el-button type="primary" @click="logout">
              <el-icon>
                <SwitchButton/>
              </el-icon>
              退出登录
            </el-button>
          </div>
        </div>
      </el-header>

      <el-main class="main-content">
        <el-card class="welcome-card">
          <template #header>
            <div class="card-header">
              <el-icon>
                <User/>
              </el-icon>
              <span>用户信息</span>
            </div>
          </template>
          <div class="user-profile">
            <el-descriptions :column="1" size="large">
              <el-descriptions-item label="手机号">
                {{ userPhone }}
              </el-descriptions-item>
              <el-descriptions-item label="登录时间">
                {{ loginTime }}
              </el-descriptions-item>
              <el-descriptions-item label="Token状态">
                <el-tag :type="verified ? 'success' : 'info'">
                  {{ verified ? "已认证" : "校验中" }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>

        <el-card class="feature-card">
          <template #header>
            <div class="card-header">
              <el-icon>
                <Setting/>
              </el-icon>
              <span>功能区域</span>
            </div>
          </template>
          <el-empty description="功能开发中..."/>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from "vue";
import {useRouter} from "vue-router";
import {ElMessage} from "element-plus";
import {Setting, SwitchButton, User} from "@element-plus/icons-vue";
import {authApi} from "@/api/auth";
import {clearToken} from "@/utils/auth";

const router = useRouter();
const userPhone = ref("");
const loginTime = ref("");
const verified = ref(false);

onMounted(async () => {
  try {
    // 手机号与登录时间都取自 token 对应的服务端数据，不再读 localStorage
    const {data} = await authApi.me();
    userPhone.value = data.phone;
    loginTime.value = data.loginTime;
    verified.value = true;
  } catch {
    // token 缺失、被改过或者已经过期，清掉本地存储回登录页
    clearToken();
    router.replace("/login");
  }
});

const logout = () => {
  clearToken();
  ElMessage.success("退出登录成功");
  // 跳转到登录页
  router.push("/login");
};
</script>

<style scoped>
.home-container {
  width: 100%;
  height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}

.header {
  background: linear-gradient(90deg, #60cddb 0%, #cbe9ed 100%);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.title {
  color: white;
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.user-info {
  display: flex;
  align-items: center;
}

.main-content {
  width: 100%;
  padding: 20px;
}

.welcome-card,
.feature-card {
  margin-bottom: 20px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  width: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #409eff;
}

.card-header .el-icon {
  font-size: 20px;
}

.user-profile {
  padding: 20px 0;
}

:deep(.el-descriptions__label) {
  font-weight: 600;
  color: #606266;
}

:deep(.el-descriptions__content) {
  color: #303133;
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.3s ease;
}

:deep(.el-button:hover) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
}

:deep(.el-card__header) {
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  border-bottom: 1px solid #e4e7ed;
}
</style>
```

手机号与登录时间来自 `/user/me`，不再读 localStorage。token 缺失、被改过或过期时，请求返回 40100，页面清掉本地存储并回到登录页，`已认证` 这个标签只有服务端确认之后才会显示。

## 8. 端到端联调

### 8.1 启动顺序

三个东西要都在跑：Redis、后端、前端。

```bash
# 1. Redis
redis-cli --no-auth-warning -a <你的密码> ping

# 2. 后端，端口 8006
mvn -pl test-redis1 spring-boot:run

# 3. 前端，端口 5173
cd my-vue-app && pnpm dev
```

后端起来后先自测一个接口，确认服务可用：

```bash
curl -s -X POST "$BASE/sms/send?phone=13951905171"
```

### 8.2 操作与观察点

浏览器打开 `http://localhost:5173`，会重定向到 `/login`。

第一步，在手机上不填验证码，先点“发送验证码”。此时可以看三个地方：

- 按钮变成“60秒后重发”并置灰，验证码输入框出现
- Network 里有一条 `OPTIONS /sms/send`（预检）和一条 `POST /sms/send?phone=...`，后者状态 200，响应 `{"code":200,"data":"短信发送成功","message":"success"}`
- 后端日志里出现 `短信通道为模拟模式，手机号 13951905171，验证码 9275`
- Redis 里出现 `sms:captcha:13951905171`

第二步，先用一个错的验证码点“登录”。页面上显示的是“验证码错误”，与后端返回的文案一致。Redis 里多出一个 `sms:fail:13951905171` 计数，值为 1。

第三步，填入日志里的验证码点“登录”。提示“登录成功”，随后跳转到 `/home`，页面显示手机号和本次登录时间，Token状态是“已认证”。此时再查 Redis：

```bash
redis-cli --no-auth-warning -a abc123 -n 10 hget sms:captcha:13951905171 status
# "1"
```

key 还在，状态变成了已使用，同一个验证码再提交会返回 40006。

第四步，在 `/home` 点“退出登录”，localStorage 里的 token 被清掉，跳回 `/login`。手动访问 `http://localhost:5173/home`，路由守卫发现本地没有 token，把你送回 `/login`。

第五步，验证 token 真的有用。重新登录一次，然后在开发者工具的 Application 面板把 localStorage 里的 `token` 改成任意字符串，刷新 `/home`。请求 `/user/me` 返回 40100，页面回到登录页。这一步说明“已认证”不是页面上的一个固定标签，而是服务端验签的结果。

### 8.3 前后端字段对应

| 环节 | 后端 | 前端 | 位置 |
| --- | --- | --- | --- |
| 发送路径 | `@PostMapping("/send")` | `/sms/send` | `SmsController` / `auth.ts` |
| 登录路径 | `@PostMapping("/login")` | `/sms/login` | `SmsController` / `auth.ts` |
| 发送入参 | `@RequestParam String phone` | `params: { phone }` | `SmsController` / `auth.ts` |
| 登录入参 | `LoginRequest {phone, code}` | `{"phone","code"}` | `LoginRequest` / `Login.vue` |
| 业务码 | `Result.code` | `body.code === 200` | `Result` / `request.ts` |
| 文案字段 | `Result.message` | `body.message` | `Result` / `request.ts` |
| 登录出参 | `data = {token, phone, message, expiresIn}` | `LoginResult` | `LoginResponse` / `api.ts` |
| 用户信息 | `UserInfo {phone, loginTime}` | `UserInfo` | `UserInfo` / `api.ts` |
| token 传递 | `Authorization` 头 | 请求拦截器自动加 `Bearer` | `AuthInterceptor` / `request.ts` |
| token 存放 | 无状态，不落库 | `localStorage.token` | `AuthInterceptor` / `auth.ts` |

## 9. 实现要点与易错点

下面 15 条是同一个功能容易写错的地方，每条给出写错的样子、造成的现象和本实现的做法。现象一栏都是在本地实例上实测过的。

### 9.1 验证码不存在时对 null 调 toString

容易写成：

```java
String redisCode = redisCache.get(redisKey).toString();
if (redisCode == null) {
    throw new BusinessException(ErrorCode.PHONE_CODE_EXPIRED);
}
```

`get` 返回 `null` 时，`.toString()` 先抛出 `NullPointerException`，后面的判空根本没机会执行。全局异常处理把它接住，返回 `{"code":500,"message":"系统繁忙，请稍后再试"}`，`PHONE_CODE_EXPIRED`（40005）成了一个永远不会出现的分支。用户输错手机号或者验证码已过期，看到的是“系统繁忙”，分不清是自己填错了还是服务出问题了。

本实现先判空再取值：

```java
Map<String, Object> cached = redisCache.hGetAll(redisKey);
if (cached.isEmpty()) {
    throw new BusinessException(ErrorCode.PHONE_CODE_EXPIRED);
}
```

实测这一分支返回 `{"code":40005,"data":null,"message":"验证码不存在或已过期"}`。

### 9.2 发送接口不校验手机号

`sendSms` 如果直接把传入的字符串拼进 key，`?phone=abc`、`?phone=` 都会返回成功，Redis 里会出现 `sms:captcha:abc` 这样的 key，真正的发送动作也会带着这个值去调用通道。在真实短信通道下，这是一个不需要任何条件就能触发的接口。

本实现在方法开头做同一次校验，登录与发送共用 `CommonUtils.checkPhone`：

```java
if (!CommonUtils.checkPhone(phone)) {
    throw new BusinessException(ErrorCode.PHONE_ERROR);
}
```

实测 `?phone=abc` 与 `?phone=` 都返回 `{"code":40001,"data":null,"message":"手机号格式错误"}`，Redis 里不再产生非法 key。

### 9.3 发送接口没有频率限制

短信是按条计费的。没有限制的话，同一个手机号可以连发几十次，验证码被反复覆盖，费用按次结算。`ErrorCode` 里通常早就预留了“发送过于频繁”“次数已达上限”两个码，只是没有实现。

本实现用两层限制，都复用了同一个 Redis：

```java
String intervalKey = RedisKeys.getSmsSendIntervalKey(phone);
Boolean acquired = redisCache.setIfAbsent(intervalKey, 1, smsProperties.getSendIntervalSeconds());
if (!Boolean.TRUE.equals(acquired)) {
    throw new BusinessException(ErrorCode.PHONE_CODE_SEND_TOO_MANY);
}

Long sentToday = redisCache.increment(RedisKeys.getSmsSendCountKey(phone), SECONDS_PER_DAY);
if (sentToday != null && sentToday > smsProperties.getSendDailyLimit()) {
    throw new BusinessException(ErrorCode.PHONE_CODE_SEND_LIMIT);
}
```

第一层用 `SET NX EX` 占一个 60 秒的坑，占不到就说明刚发过。第二层用计数器做当日窗口，`increment(key, expire)` 只在第一次自增时设置过期时间。

实测 60 秒内重复发送返回 `{"code":40007,...}`；清掉间隔 key 后连发 11 次，前 10 次成功，第 11 次返回 `{"code":40009,"message":"验证码发送次数已达上限"}`。

### 9.4 可预期的输入错误落进 500 兜底

只保留一个 `Exception` 兜底分支时，缺少必填参数、请求方法不对、请求体类型不对这些调用方的问题都会返回 500，日志里也看不出区别。真正的服务端故障混在大量“参数写错”的日志里，排查时很难找。

本实现为三类框架异常各补了一个分支：

```java
@ExceptionHandler(MissingServletRequestParameterException.class)
public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
    return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "缺少必填参数：" + e.getParameterName());
}

@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
    return Result.fail(405, "请求方法不支持：" + e.getMethod());
}

@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
public Result<Void> handleMediaType(HttpMediaTypeNotSupportedException e) {
    return Result.fail(415, "请求体类型不支持，请使用 application/json");
}
```

实测：`POST /sms/send` 不带参数返回 `{"code":400,"message":"缺少必填参数：phone"}`，`GET /sms/login` 返回 `{"code":405,"message":"请求方法不支持：GET"}`，用 `text/plain` 提交返回 `{"code":415,...}`。

### 9.5 验证码取值范围少一个数

```java
ThreadLocalRandom.current().nextInt(1000, 9999)
```

`nextInt(origin, bound)` 的右边界是开区间，实际范围是 1000 到 9998，9999 取不到。四位数的完整范围是 1000 到 9999 共 9000 个值，这样写少了 9999 这一个。

本实现改成：

```java
ThreadLocalRandom.current().nextInt(1000, 10000)
```

这类边界问题靠在线抽样不容易发现，连续发 200 次最大值只到 9988 附近，9999 出现的概率是九千分之一。直接调用工具类统计更快：

```java
public class RangeCheck {
    public static void main(String[] args) {
        boolean[] seen = new boolean[10000];
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = 0; i < 2_000_000; i++) {
            int code = CommonUtils.generateCode();
            seen[code] = true;
            min = Math.min(min, code);
            max = Math.max(max, code);
        }
        int distinct = 0;
        for (boolean b : seen) if (b) distinct++;
        System.out.println("200 万次取值：最小 " + min + "，最大 " + max + "，覆盖不同值 " + distinct + " 个");
    }
}
```

```
200 万次取值：最小 1000，最大 9999，覆盖不同值 9000 个
```

换成 `nextInt(1000, 9999)` 再跑一次，覆盖的不同值只有 8999 个，最大停在 9998。

### 9.6 先写 Redis 再发短信，通道又没有开关

顺序反了会有两个后果。一是短信通道返回失败时，Redis 里留着一个用户永远收不到的验证码，白占 300 秒，用户重发的验证码也无法生效，因为后一次会覆盖前一次。二是本地调试时每次都真发短信，按条计费，模板内容还要先过审。

本实现把顺序调过来，并给通道加了开关：

```java
if (!send(phone, code)) {
    redisCache.delete(intervalKey);
    throw new BusinessException(ErrorCode.PHONE_CODE_SEND_FAILED);
}

Map<String, Object> value = new HashMap<>();
value.put(FIELD_CODE, String.valueOf(code));
value.put(FIELD_STATUS, STATUS_UNUSED);
redisCache.hMSet(RedisKeys.getSmsKey(phone), value, smsProperties.getCodeExpireSeconds());
```

发送失败时顺手把间隔锁删掉，用户可以立刻重试，不用等 60 秒。`send` 方法在 `mockEnabled` 为 `true` 时只打一行日志返回成功：

```java
if (smsProperties.isMockEnabled()) {
    log.info("短信通道为模拟模式，手机号 {}，验证码 {}", phone, code);
    return true;
}
```

上线前把这个开关改成 `false`，真实分支里判断 SDK 返回的 `statusCode` 是否为 `000000`，失败时把状态码和状态信息打进日志。

### 9.7 token 只拼随机串，没有签名和有效期

```java
return UUID.randomUUID().toString().replace("-", "") + phone.hashCode();
```

这样拼出来的 token 没有任何可校验的结构，服务端也不存储它，谁都能造一个。如果只有签发没有校验，所有接口都是直接放行的，登录态就只存在于浏览器的 localStorage 里。

本实现换成 JWT，并在服务端加一个拦截器真的去验：

```java
public String create(String phone) {
    Instant now = Instant.now();
    return Jwts.builder()
            .subject(phone)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expireSeconds)))
            .signWith(secretKey)
            .compact();
}
```

```java
Claims claims = jwtUtils.parse(header.substring(TOKEN_PREFIX.length()));
request.setAttribute(ATTR_CLAIMS, claims);
```

实测不带 token、伪造 token、篡改签名、签名合法但已过期四种情况都返回 40100，只有签名与有效期都通过的 token 才能拿到 `/user/me` 的数据。签名密钥长度决定算法，56 字节的密钥落在 HS384；HS256 要求至少 32 字节，密钥短了 jjwt 会直接抛 `WeakKeyException`。

### 9.8 验证码用过即删，三种状态分不清

校验通过后 `delete` 掉 key，“这个码用过了”和“这个码没发过”都表现为查不到，`PHONE_CODE_USED`（40006）同样无用。

本实现把验证码存成 Hash，用 `status` 记录状态，校验通过后改状态而不是删 key：

```java
redisCache.hSet(redisKey, FIELD_STATUS, STATUS_USED, smsProperties.getCodeExpireSeconds());
```

实测同一个验证码第二次提交返回 `{"code":40006,"data":null,"message":"验证码已使用"}`，key 保留到 TTL 到期。代价是重复提交会一直返回 40006 直到过期，判断“已过期”需要额外看 TTL；换来的是三种状态各有一个明确的提示。

### 9.9 不限制验证码错误次数

四位验证码只有 9000 种可能，有效期 300 秒，不限制尝试次数的话可以逐个试。登录接口本身不需要认证，等于把爆破口开在了公网上。

本实现按手机号累计错误次数，达到上限就作废当前验证码：

```java
if (!inputCode.equals(cached.get(FIELD_CODE))) {
    Long failCount = redisCache.increment(
            RedisKeys.getSmsFailCountKey(phone), smsProperties.getCodeExpireSeconds());
    if (failCount != null && failCount >= smsProperties.getMaxFailCount()) {
        redisCache.delete(redisKey);
        redisCache.delete(RedisKeys.getSmsFailCountKey(phone));
        throw new BusinessException(ErrorCode.PHONE_CODE_ERROR_TOO_MANY);
    }
    throw new BusinessException(ErrorCode.PHONE_CODE_ERROR);
}
```

实测连错 5 次，前 4 次返回 40004，第 5 次返回 `{"code":40010,"message":"验证码错误次数过多，请重新获取"}`，同时验证码与计数的 key 都被删掉，需要重新获取验证码。

### 9.10 后端字段名与前端不一致

后端 `Result` 的字段是 `message`，前端如果按 `msg` 去取：

```ts
errorMessage.value = response.msg || "发送失败，请重试。";
```

`response.msg` 恒为 `undefined`，“||”右边那句兜底文案总会生效。后端定义的“验证码发送过于频繁”“验证码发送次数已达上限”这些具体提示，前端一条都显示不出来。

本实现统一按后端的字段名写类型，并在类型里把可选性写清楚：

```ts
export interface ApiResponse<T = unknown> {
    code: number;
    message: string;
    data: T;
}
```

字段名写错时，TypeScript 会在编译期报错，而不是留到运行期变成一句笼统的兜底文案。

### 9.11 前端在 data 为 null 时读 token

```ts
const loginResult = response.data;
if (loginResult.token) {
```

登录失败时后端返回的 `data` 是 `null`，`loginResult.token` 抛 `TypeError: Cannot read properties of null`，被外层 `catch` 接住，页面上显示的是“请求失败，请稍后再试。”。用户输错验证码，看到的是“请求失败”，会以为网络出了问题。

本实现把“成功与失败”的判断收进响应拦截器，页面只在成功分支里读 `data`：

```ts
const {data} = await authApi.login(loginData);
saveToken(data.token);
```

失败走 `catch`，页面上显示拦截器抛出来的后端文案：

```ts
errorMessage.value = (error as Error).message || "登录失败，请重试。";
```

实测输错验证码时页面显示“验证码错误”，与后端返回一致。

### 9.12 倒计时与有效期各写各的

后端给验证码设的过期时间、前端按钮的倒计时、发送接口的间隔限制，是三个不同的数字，混在一起写很容易对不上。

本实现把它们分开命名，前后端各有一处集中定义：

| 含义 | 后端 | 前端 | 取值 |
| --- | --- | --- | --- |
| 验证码有效期 | `mqxu.sms.code-expire-seconds` | `CODE_TTL_MINUTES` | 300 秒 / 5 分钟 |
| 重发间隔 | `mqxu.sms.send-interval-seconds` | `SEND_INTERVAL_SECONDS` | 60 秒 |

倒计时对应的是重发间隔，页面上提示的“5 分钟内有效”对应的是有效期，两个数字各司其职。后端也真的按 60 秒拦，前端倒计时结束就能重发，两边一致。改了后端的数字，前端常量要一起改，这一条写在了 `src/config/index.ts` 的注释里。

### 9.13 定时器没有在组件卸载时清理

`setInterval` 只在计数到 0 时被清掉，用户在倒计时期间点浏览器后退或者被跳转到别的页面，定时器会继续跑到归零。影响不大，但会让已经卸载的组件持有引用。

本实现补上卸载钩子，并在新一轮倒计时开始前先清一次：

```ts
onUnmounted(() => {
  clearInterval(timer);
});
```

```ts
const startCountdown = () => {
  clearInterval(timer);
  isCountingDown.value = true;
  ...
};
```

### 9.14 首页只读 localStorage，没有真实鉴权

如果首页只读 localStorage，手机号和登录时间都来自浏览器，`已认证` 是写死的标签。只要在浏览器控制台执行 `localStorage.setItem('token','x')`，路由守卫就会放行 `/home`，页面上还显示“已认证”。

本实现给后端加了 `GET /user/me`，首页挂载时带上 token 调用它：

```ts
const {data} = await authApi.me();
userPhone.value = data.phone;
loginTime.value = data.loginTime;
verified.value = true;
```

服务端验签失败返回 40100，拦截器清掉 token 并回登录页。`已认证` 这个标签改成跟着请求结果走，只有服务端确认之后才显示。手机号与登录时间也不再存在浏览器里，减少一处可以被改动的数据。

### 9.15 收尾项

`RedisCache.set` 拆成 `set` 加 `expire` 两条命令下发，中间进程退出会留下永不过期的 key。改成一个带过期时间的重载。

跨域配置写了两套（`addCorsMappings` 和 `corsConfigurationSource`）效果重叠，留了一套，放进 `WebConfig` 与拦截器注册放在一起。

`index.html` 引用的入口要写成 `/src/main.ts`，与真实文件名一致。

`main.ts` 里批量注册全部 Element Plus 图标会让几百个组件被打进包，模板里用到的图标在各自页面按需引入即可。

登录页的手机号初始值不留测试号码。

应用名沿用了其它模块的名字，`spring.application.name` 改成这个工程自己的名字，日志和监控里的标识才对得上。

## 10. 练习

### 随堂练习

**练习 1：改发送间隔，验证前后端一致。**

把 `mqxu.sms.send-interval-seconds` 改成 30，同时把 `src/config/index.ts` 里的 `SEND_INTERVAL_SECONDS` 也改成 30。重启后端，在前端连点两次“发送验证码”，确认第二次被 40007 拦下，且按钮的倒计时是 30 秒。再只改后端不改前端，观察会出现什么现象。

**练习 2：用 MONITOR 观察一次完整登录。**

```bash
redis-cli --no-auth-warning -a abc123 -n 10 monitor
```

另一个终端走一遍发送加登录，把与 `sms:` 相关的命令按顺序抄下来，对照 4.8 节说明每条命令来自 `RedisCache` 的哪个方法。

**练习 3：缩短 token 有效期。**

把 `mqxu.jwt.expire-seconds` 改成 30，重新登录，等 30 秒后再访问 `/user/me`，确认返回 40100。顺便看一下前端拦截器的行为：是页面自己跳转，还是拦截器跳转。

**练习 4：观察 mock 开关的作用。**

把 `mock-enabled` 改成 `false` 并重启，调用发送接口。观察日志里的状态码与状态信息，以及接口返回。把返回的验证码字段、错误码和你预期的不一致的地方记下来。

**练习 5：手工改状态。**

发送验证码后，用 `redis-cli` 把 Hash 里的 `status` 改成 `1`，再用正确的验证码登录，确认返回 40006。再把 `status` 改回 `0`，同一个验证码就能登录成功。

### 课后作业

**作业 1：把验证码改成 6 位。**

改 `CommonUtils.generateCode` 的取值范围，同时改前端验证码输入框的长度限制与表单校验规则，并调整 9.9 节里错误次数上限的说明（6 位验证码的取值空间更大，限制策略可以放宽）。改完给出接口返回的对照表。

**作业 2：按 IP 再加一层发送限制。**

当前限制是按手机号做的，攻击者可以换手机号绕过。用一个能拿到客户端 IP 的地方（`HttpServletRequest.getRemoteAddr()` 或经过代理时的 `X-Forwarded-For`）加一层限制，比如同一 IP 一小时最多 20 条，触发时返回一个新的错误码。

**作业 3：把登录态换成 Redis 会话，比较两种方案的取舍。**

token 改成随机串存 Redis，value 放手机号，TTL 2 小时；拦截器改成查 Redis。想清楚这两种方案在“主动下线”“多实例部署”“每次请求的开销”“能否离线校验”四个方面各有什么差别，写成一段说明。

**作业 4：给接口分权限。**

新增一个需要管理员才能访问的接口，比如 `GET /admin/stats`。在 token 的载荷里加一个 `role`，拦截器校验角色，不匹配返回 403。用两个不同角色的 token 分别访问，给出返回对照。

**作业 5：写一组接口测试。**

用 `MockMvc` 覆盖 `/sms/send` 与 `/sms/login`，至少包含这些用例：手机号格式错误、验证码为空、验证码不存在、验证码错误、连续错误达到上限、验证码正确、验证码用第二次、缺少参数。验证码部分用 `@MockitoBean` 替换 `RedisCache`，不要依赖真实 Redis。另外为 `/user/me` 补三个用例：不带 token、token 无效、token 有效。

## 11. 小结

这个案例把验证码登录拆成两块：服务端负责生成、存储、校验验证码并签发登录态；客户端负责发起请求、展示倒计时、保存 token 和控制路由。两块之间的契约只有三条——`/sms/send` 收手机号，`/sms/login` 收手机号与验证码，返回体统一带 `code` 与 `message`

跑通链路之后，代码里的关键细节集中在三处。Redis 用的是 10 号库，排查时容易漏掉 `-n 10`。JSON 序列化器要打开类型信息并放行 `java.lang.`，否则读回来的值类型不对。跨域要放行开发服务器的端口，预检结果会被浏览器缓存一小时。

从“能跑通”到“能上线”，中间隔着三类工作：可用性是发送接口的手机号校验与频率限制，短信按条计费的特性决定了这两项必须有；安全性是 token 的签名与有效期、服务端对 token 的校验、错误次数的限制，以及密钥与短信账号不进仓库；可维护性是把验证码策略、重发间隔这些数字集中到配置里，前后端各保留一处定义。

验证码登录只是登录方式的一种。把这里的 `sms:captcha:{phone}` 换成 `captcha:{uuid}`，就是图形验证码；把校验换成密码比对，就是账号密码登录；把 token 换成 Redis 会话，就是有状态登录。Redis 的 key 结构、错误码分段、拦截器这三样是不变的骨架。
