# 15 · 工具库实战：用 Hutool 把十二个常见需求做成接口

这一篇用真实项目里反复出现的十二个需求，把 Hutool 常用的工具类串起来。每个案例都是从建文件开始，一个文件一个步骤，代码完整给出。所有代码都在 `15-toolkit` 模块里，端口 8015。

## 1. 本节导读

每个案例都是一个独立的小需求：验证码、扫码登录、商品海报、短链、Excel 导入导出、分片上传、上传安全、开放接口签名、敏感数据、系统面板、接口巡检、文本清洗。这些需求在真实项目里出现的频率很高，写起来又都不复杂，正适合用来练工具库。

### 1.1 十二个案例一览

| 序号 | 案例         | 主要 API                                 | 对应的真实场景        |
| -- | ---------- | -------------------------------------- | -------------- |
| 1  | 图形验证码      | CaptchaUtil、MathGenerator、TimedCache   | 登录、注册、短信发送前的防刷 |
| 2  | 扫码登录       | QrCodeUtil、QrConfig、JWTUtil            | PC 端扫码登录       |
| 3  | 商品海报       | Img、ImgUtil、QrCodeUtil                 | 分享海报、活动图       |
| 4  | 短链服务       | RandomUtil、TimedCache、Validator        | 短信里的短链接、渠道统计   |
| 5  | Excel 导入导出 | ExcelWriter、ExcelReader、@Alias         | 后台报表、批量导入      |
| 6  | 分片上传       | FileUtil、DigestUtil                    | 大文件、断点续传       |
| 7  | 上传安全       | FileTypeUtil、ImgUtil、ZipUtil           | 上传校验、图片压缩、打包下载 |
| 8  | 开放接口签名     | SecureUtil.hmac、MessageDigest          | 给第三方调用的 API    |
| 9  | 敏感数据       | AES、BCrypt、DesensitizedUtil、IdcardUtil | 手机号身份证加密、按角色脱敏 |
| 10 | 系统面板       | OshiUtil、ManagementFactory             | 运维监控页          |
| 11 | 接口巡检       | HttpRequest、ThreadUtil                 | 定时探测地址可用性      |
| 12 | 文本清洗       | ReUtil、SensitiveUtil、DFA               | 内容审核、日志脱敏      |

### 1.2 这一节的组织方式

第 2 章把工程的骨架一次搭好，包含依赖、配置、启动类、统一响应、错误码、全局异常、配置类。这些每个案例都要用到，所以集中放在前面，后面不再重复。

第 3 到 14 章一个案例一章。每章都按同样的五段写：需求、涉及文件、实现步骤、接口实测、容易踩的坑。实现步骤里按编译能通过的顺序逐个文件给出完整代码，从 DTO 开始，到 Service，最后到 Controller，中间不留省略号，照抄就能编译。

第 15 章讲生产边界，也就是这些代码哪些能直接上生产、哪些必须换掉。第 16 到 18 章是常见问题、练习和小结。

### 1.3 环境要求

- JDK 21，父工程 `maven.compiler.source` 就是 21
- Spring Boot 4.1.1，父工程已经用 `spring-boot-starter-parent` 管好版本
- Maven 3.9 以上
- 不需要数据库、不需要 Redis，所有案例的数据都放进程内存，重启即清空

所有接口都挂在 `/toolkit` 下面，端口 8015。启动方式在后面第 2 章末尾给出。

## 2. 先把骨架搭好

这一章建出可以启动的空工程，并准备好后面十二个案例共用的基础类。文件顺序就是下面的步骤顺序，先父工程挂号，再建模块，再写启动类和公共类，最后启动验证。

### 2.1 建模块与配置

#### 步骤 1 · 在父工程的模块列表里加上 15-toolkit

父工程 `pom.xml` 的 `<modules>` 里加一行。不加这一行，`mvn -pl 15-toolkit` 会报找不到模块。

```xml
<modules>
    <module>01-quickstart</module>
    
    <module>14-deploy</module>
    <module>15-toolkit</module>
</modules>
```

#### 步骤 2 · 写模块的 pom.xml

父工程已经管好了 Spring Boot 的版本，也默认带了 webmvc、lombok、devtools，所以模块里只写额外需要的东西。这里要特别注意 Hutool 有四个功能依赖的是可选包，必须自己引：二维码要 zxing，Excel 要 POI，系统信息要 oshi。不引的后果不是编译报错，而是运行到那行代码才抛 `NoClassDefFoundError`。

路径：`15-toolkit/pom.xml`

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

    <artifactId>15-toolkit</artifactId>

    <properties>
        <hutool.version>5.8.48</hutool.version>
        <zxing.version>3.5.3</zxing.version>
        <poi.version>5.5.1</poi.version>
        <oshi.version>6.6.2</oshi.version>
    </properties>

    <dependencies>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>${hutool.version}</version>
        </dependency>

        
        <dependency>
            <groupId>com.google.zxing</groupId>
            <artifactId>core</artifactId>
            <version>${zxing.version}</version>
        </dependency>
        <dependency>
            <groupId>com.google.zxing</groupId>
            <artifactId>javase</artifactId>
            <version>${zxing.version}</version>
        </dependency>

        
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>${poi.version}</version>
        </dependency>

        
        <dependency>
            <groupId>com.github.oshi</groupId>
            <artifactId>oshi-core</artifactId>
            <version>${oshi.version}</version>
        </dependency>
    </dependencies>

</project>
```

#### 步骤 3 · 写配置文件

配置项全部放在 `toolkit` 前缀下，由第 8 步的配置类读取。密钥、有效期、目录都不写死在代码里，改参数不用重新编译。`work-dir` 用系统临时目录，退出后文件还在，但重启不影响功能。

路径：`15-toolkit/src/main/resources/application.yml`

```yaml
server:
  port: 8015

spring:
  application:
    name: 15-toolkit 工具库实战
  servlet:
    multipart:
      max-file-size: 20MB     # 上传的单个文件大小
      max-request-size: 50MB  # 单次请求最大容量

toolkit:
  # 各案例的临时文件目录，启动时自动创建
  work-dir: ${java.io.tmpdir}/toolkit-demo
  captcha:
    expire-seconds: 60
    # 练习环境打开，响应里会带答案，方便 curl 直接验证；线上必须设成 false
    debug-code: true
  qr-login:
    expire-seconds: 120
  short-link:
    expire-seconds: 604800
    base-url: http://127.0.0.1:8015
  sign:
    app-id: toolkit-demo
    app-secret: 3f2a9c1e5b7d4086a1c3e5f7092b4d68
    tolerance-seconds: 300
  crypto:
    aes-key: 4f8b2d6a1c9e3570
  chunk:
    chunk-size: 1048576
    expire-seconds: 3600
  inspect:
    timeout-millis: 3000
    max-concurrency: 8

logging:
  level:
    top.mqxu.toolkit: info
```

`captcha.debug-code` 是这个练习工程特有的开关。打开后生成验证码的响应里会带上答案，方便用 curl 一条命令走完整个校验流程。真实项目里必须关掉，答案只能留在服务端。

### 2.2 启动类

#### 步骤 4 · 写启动类

启动类里只多做一件事：把 `java.awt.headless` 设成 true。二维码、海报、验证码都要画图，服务器上没有显示设备，不设这个参数在某些环境下会抛 `HeadlessException`。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/ToolkitApplication.java`

```java
package top.mqxu.toolkit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 工具库实战模块启动类
 *
 * @author mqxu
 * @date 2026-10-21
 * @description Hutool 典型功能的工程化案例
 */
@SpringBootApplication
public class ToolkitApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(ToolkitApplication.class, args);
    }
}
```


### 2.3 公共类

#### 步骤 5 · 统一响应体

所有接口都返回 `code`、`message`、`data` 三个字段。二进制接口（海报、Excel 下载、压缩包）例外，它们直接返回字节流，不走这个包装。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/common/Result.java`

```java
package top.mqxu.toolkit.common;

import lombok.Data;

/**
 * 统一响应体
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 所有接口统一返回 code / message / data
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

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
```

#### 步骤 6 · 错误码

错误码按案例分段，41001 到 41003 是验证码，41011 起是扫码登录，依此类推。分段的好处是看到码就知道是哪个功能出的问题，排查时不用翻代码。公共的 4xxxx 和 50000 放在最前面。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/common/ErrorCode.java`

```java
package top.mqxu.toolkit.common;

import lombok.Getter;

/**
 * 错误码
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 每个案例的错误码独立编号，41001 起按案例分段
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),

    BAD_REQUEST(40000, "请求参数不合法"),
    NOT_FOUND(40400, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE(41500, "不支持的内容类型"),
    PAYLOAD_TOO_LARGE(41300, "上传文件过大"),
    INTERNAL_ERROR(50000, "服务器内部错误"),

    // 案例 1：验证码
    CAPTCHA_NOT_FOUND(41001, "验证码不存在或已过期"),
    CAPTCHA_WRONG(41002, "验证码错误"),
    CAPTCHA_USED(41003, "验证码已使用"),

    // 案例 2：扫码登录
    QR_NOT_FOUND(41011, "二维码不存在或已过期"),
    QR_STATE_CONFLICT(41012, "二维码状态不允许该操作"),

    // 案例 4：短链
    SHORT_LINK_NOT_FOUND(41021, "短链不存在或已过期"),

    // 案例 5：Excel
    EXCEL_READ_FAILED(41031, "Excel 解析失败"),
    EXCEL_EMPTY(41032, "Excel 没有数据行"),

    // 案例 6/7：文件
    CHUNK_SESSION_NOT_FOUND(41041, "分片会话不存在或已过期"),
    CHUNK_NOT_COMPLETE(41042, "分片不完整"),
    CHUNK_MERGE_FAILED(41043, "分片合并失败"),
    FILE_TYPE_BLOCKED(41044, "文件类型不允许"),
    FILE_EMPTY(41045, "文件为空"),

    // 案例 8：开放接口签名
    SIGN_MISSING(41051, "缺少签名参数"),
    SIGN_INVALID(41052, "签名不正确"),
    SIGN_EXPIRED(41053, "请求已过期"),
    SIGN_REPLAY(41054, "重复请求"),

    // 案例 9：敏感数据
    CUSTOMER_NOT_FOUND(41061, "客户不存在"),
    PASSWORD_WRONG(41062, "密码错误");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

#### 步骤 7 · 业务异常

业务失败不返回 null，也不返回 false，而是抛这个异常，由下一步的全局处理器统一转成响应。这样 Service 层的方法签名可以保持干净，不用把错误码一层层往上带。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/common/BusinessException.java`

```java
package top.mqxu.toolkit.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 携带错误码，由全局异常处理器转成 Result
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
```

#### 步骤 8 · 全局异常处理

这个类做两件事：把异常转成统一响应，同时把 HTTP 状态码也设对。很多项目所有响应都返 200，靠 body 里的 code 区分成败，结果监控、网关、巡检这些看状态码的组件全被瞒过去。这里的做法是业务码前三位的语义映射到状态码，`4xxxx` 的细分码统一归到 400，`40400` 给 404，`40500` 给 405，`41300` 给 413，`41500` 给 415，`50000` 给 500。

除了业务异常，还处理了参数校验失败、缺参数、参数类型不对、请求体不是 JSON、请求方法不支持、Content-Type 不对、上传超限、静态资源 404 这几种常见情况。最后留一个兜底，把没预料到的异常记日志并返回 500，不把堆栈暴露给调用方。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/common/GlobalExceptionHandler.java`

```java
package top.mqxu.toolkit.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 统一返回 Result，同时把 HTTP 状态码也设对，
 * 巡检、网关、监控这些看状态码的组件才不会被统一 200 骗过去
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常：{} {}", e.getErrorCode().getCode(), e.getMessage());
        return build(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        return build(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return build(ErrorCode.BAD_REQUEST, "缺少参数：" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(ErrorCode.BAD_REQUEST, "参数类型错误：" + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return build(ErrorCode.BAD_REQUEST, "请求体不是合法的 JSON");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return build(ErrorCode.METHOD_NOT_ALLOWED,
                "该接口只支持 " + String.join(", ", e.getSupportedMethods()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "请使用 application/json 提交");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleTooLarge(MaxUploadSizeExceededException e) {
        return build(ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND, "路径不存在：/" + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<Result<Void>> build(ErrorCode errorCode, String message) {
        return ResponseEntity.status(httpStatus(errorCode.getCode()))
                .body(Result.fail(errorCode, message));
    }

    /**
     * 业务码前三位就是对应的 HTTP 状态码，4xxxx 里的细分类都归到 400
     */
    private HttpStatus httpStatus(int code) {
        return switch (code) {
            case 40400 -> HttpStatus.NOT_FOUND;
            case 40500 -> HttpStatus.METHOD_NOT_ALLOWED;
            case 41300 -> HttpStatus.PAYLOAD_TOO_LARGE;
            case 41500 -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case 50000 -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
```

#### 步骤 9 · 配置类

一个类对应 yml 里的 `toolkit` 前缀，内部用静态类分段，和 yml 的层级一一对应。写法上每个字段给默认值，这样 yml 缺某一项也不会启动失败。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/config/ToolkitProperties.java`

```java
package top.mqxu.toolkit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 案例配置
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 密钥、过期时间、目录等全部外置，代码里不写死
 */
@Data
@Component
@ConfigurationProperties(prefix = "toolkit")
public class ToolkitProperties {

    /**
     * 临时文件根目录
     */
    private String workDir;

    private Captcha captcha = new Captcha();
    private QrLogin qrLogin = new QrLogin();
    private ShortLink shortLink = new ShortLink();
    private Sign sign = new Sign();
    private Crypto crypto = new Crypto();
    private Chunk chunk = new Chunk();
    private Inspect inspect = new Inspect();

    @Data
    public static class Captcha {
        /**
         * 验证码有效期（秒）
         */
        private long expireSeconds = 60;

        /**
         * 调试开关，打开后响应里带上答案，方便 curl 验证。
         * 只用于本地练习，线上必须关掉
         */
        private boolean debugCode = false;
    }

    @Data
    public static class QrLogin {
        /**
         * 二维码有效期（秒）
         */
        private long expireSeconds = 120;
    }

    @Data
    public static class ShortLink {
        /**
         * 短链有效期（秒），默认 7 天
         */
        private long expireSeconds = 604800;

        /**
         * 拼短链时用的域名前缀
         */
        private String baseUrl = "http://127.0.0.1:8015";
    }

    @Data
    public static class Sign {
        private String appId = "toolkit-demo";
        private String appSecret;
        /**
         * 允许的时间偏差（秒）
         */
        private long toleranceSeconds = 300;
    }

    @Data
    public static class Crypto {
        /**
         * AES 密钥，16 位对应 AES-128
         */
        private String aesKey;
    }

    @Data
    public static class Chunk {
        /**
         * 分片大小（字节），默认 1MB
         */
        private long chunkSize = 1048576;
        /**
         * 会话保留时间（秒）
         */
        private long expireSeconds = 3600;
    }

    @Data
    public static class Inspect {
        private int timeoutMillis = 3000;
        private int maxConcurrency = 8;
    }
}
```

### 2.4 启动与验证

在仓库根目录执行下面这条命令，只启动这一个模块。注意不要加 `-am`，父工程没有主类，加上会去编译并尝试运行父工程。

```bash
mvn -pl 15-toolkit spring-boot:run
```

看到 `Tomcat started on port 8015` 就算起来了。这时还没有任何业务接口，访问一个不存在的路径，应该拿到 404 加统一响应，说明全局异常处理器已经在工作：

```bash
curl --noproxy '*' -i http://127.0.0.1:8015/toolkit/nothing
```

```http
HTTP/1.1 404
Content-Type: application/json

{"code":40400,"data":null,"message":"路径不存在：/toolkit/nothing"}
```

如果这台机器上环境变量里有 `SERVER_PORT` 或 `SERVER__PORT`，Spring 会拿它覆盖 yml 里的 8015，服务跑到别的端口上。启动前用 `env -u SERVER__PORT mvn -pl 15-toolkit spring-boot:run` 清掉。

## 3. 案例 1：图形验证码

### 3.1 需求

登录、注册、发短信之前都要挡一下机器人，最省事的办法是图形验证码。需求拆开是五条：能出图、能校验、有有效期、校验一次就作废、连续输错要作废。再进一步，样式要能换，除了常见的字母数字，还要有算术题。

为了让 curl 也能走完流程，工程里加了一个调试开关，打开后生成的响应会带答案。这个开关只用于本地练习。

### 3.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件                                                                            | 作用          |
| -- | ----------------------------------------------------------------------------- | ----------- |
| 1  | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/CaptchaResponse.java`          | 返回验证码图片和标识  |
| 2  | `15-toolkit/src/main/java/top/mqxu/toolkit/service/CaptchaService.java`       | 生成图片、存答案、校验 |
| 3  | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/CaptchaController.java` | 暴露生成与校验两个接口 |
| 4  | `application.yml（toolkit.captcha 片段）`                                         | 有效期与调试开关    |


### 3.3 实现步骤

#### 步骤 1 · 定义响应结构

用 record 而不是普通类，是因为这几个字段建好之后不会再改。`image` 是 data URI，前端拿到直接塞进 `<img>` 的 src 就能显示，不用再发一次请求取图。`code` 只在调试开关打开时有值，正常情况返回 null。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/CaptchaResponse.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 验证码响应
 *
 * @param captchaId     验证码标识，校验时回传
 * @param image         data URI，可直接放进 img 的 src
 * @param expireSeconds 有效期（秒）
 * @param type          样式
 * @param code          仅当 toolkit.captcha.debug-code 打开时有值，练习时方便从响应里取答案
 * @author mqxu
 * @date 2026-10-21
 */
public record CaptchaResponse(String captchaId, String image, long expireSeconds, String type, String code) {
}
```

#### 步骤 2 · 写验证码服务

答案存在 `TimedCache` 里，键是随机 UUID，值是 Hutool 的验证码对象。`TimedCache` 是带过期时间的 Map，过期项由 `schedulePrune` 定时清理，不清理的话缓存只增不减。

读缓存统一用 `cache.get(id, false)`，第二个参数是“是否续期”。传 true 的话，前端每刷新一次图片就续一次期，验证码永远不会过期。

算术验证码用 `new LineCaptcha(120, 40, new MathGenerator(1), 2)`，第二个参数是干扰线数量。它的 `getCode()` 返回的是算式本身，比如 `4*5=`，所以调试回答案时要先用 `Calculator.conversion` 算出 20。

输错次数单独用一个 `ConcurrentHashMap` 记，因为 `TimedCache` 里存的是验证码对象，不适合再塞计数器。错满三次直接从缓存删掉，同一个 captchaId 就再也校验不了了。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/service/CaptchaService.java`

```java
package top.mqxu.toolkit.service;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.captcha.AbstractCaptcha;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.core.math.Calculator;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.CaptchaResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码服务
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 生成四种样式与算术验证码，答案存在本地缓存里，校验一次即作废
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final int MAX_FAILURES = 3;

    private final ToolkitProperties properties;

    private TimedCache<String, AbstractCaptcha> cache;
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        long ttl = properties.getCaptcha().getExpireSeconds() * 1000;
        cache = CacheUtil.newTimedCache(ttl);
        // 每 30 秒清理一次过期项，避免只增不减
        cache.schedulePrune(30_000);
    }

    @PreDestroy
    void destroy() {
        cache.cancelPruneSchedule();
        cache.clear();
    }

    /**
     * 生成验证码
     *
     * @param type line / circle / shear / gif / math
     */
    public CaptchaResponse create(String type) {
        String style = StrUtil.blankToDefault(type, "line");
        AbstractCaptcha captcha = switch (style) {
            case "circle" -> CaptchaUtil.createCircleCaptcha(120, 40, 4, 8);
            case "shear" -> CaptchaUtil.createShearCaptcha(120, 40, 4, 2);
            case "gif" -> CaptchaUtil.createGifCaptcha(120, 40, 4);
            case "math" -> new LineCaptcha(120, 40, new MathGenerator(1), 2);
            default -> CaptchaUtil.createLineCaptcha(120, 40, 4, 2);
        };

        String captchaId = IdUtil.fastSimpleUUID();
        cache.put(captchaId, captcha);

        // GIF 是多帧图，Base64 前缀是 gif，其余都是 png
        String image = "data:image/" + ("gif".equals(style) ? "gif" : "png")
                + ";base64," + captcha.getImageBase64();

        return new CaptchaResponse(captchaId, image,
                properties.getCaptcha().getExpireSeconds(), style,
                debugAnswer(style, captcha));
    }

    /**
     * 调试开关打开时返回可直接提交的答案。
     * 算术验证码的 code 是算式本身（例如 3*7=），要算出结果才和提交值一致
     */
    private String debugAnswer(String style, AbstractCaptcha captcha) {
        if (!properties.getCaptcha().isDebugCode()) {
            return null;
        }
        String raw = captcha.getCode();
        if (!"math".equals(style)) {
            return raw;
        }
        return String.valueOf((int) Calculator.conversion(StrUtil.removeSuffix(raw, "=")));
    }

    /**
     * 校验验证码，成功后立即删除，防止同一个码重复使用
     */
    public void verify(String captchaId, String code) {
        if (StrUtil.isBlank(captchaId)) {
            throw new BusinessException(ErrorCode.CAPTCHA_NOT_FOUND);
        }
        AbstractCaptcha captcha = cache.get(captchaId, false);
        if (captcha == null) {
            throw new BusinessException(ErrorCode.CAPTCHA_NOT_FOUND);
        }
        if (StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.CAPTCHA_WRONG, "请输入验证码");
        }

        if (captcha.verify(code)) {
            cache.remove(captchaId);
            failures.remove(captchaId);
            return;
        }

        int count = failures.merge(captchaId, 1, Integer::sum);
        if (count >= MAX_FAILURES) {
            cache.remove(captchaId);
            failures.remove(captchaId);
            throw new BusinessException(ErrorCode.CAPTCHA_USED, "错误次数过多，验证码已失效");
        }
        throw new BusinessException(ErrorCode.CAPTCHA_WRONG,
                "验证码错误，还可尝试 " + (MAX_FAILURES - count) + " 次");
    }
}
```

#### 步骤 3 · 写控制器

生成接口用 GET，带一个 `type` 参数，默认 line。校验接口用 POST，请求体用内嵌的 record 接，字段上加 `@NotBlank`，配合方法参数上的 `@Valid` 触发校验。校验失败会抛 `MethodArgumentNotValidException`，被全局处理器接住，返回 40000 加具体字段的提示。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/CaptchaController.java`

```java
package top.mqxu.toolkit.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.CaptchaResponse;
import top.mqxu.toolkit.service.CaptchaService;

/**
 * 案例 1：图形验证码
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /**
     * 生成验证码
     *
     * @param type line（默认）/ circle / shear / gif / math
     */
    @GetMapping
    public Result<CaptchaResponse> create(@RequestParam(defaultValue = "line") String type) {
        return Result.ok(captchaService.create(type));
    }

    /**
     * 校验验证码，成功后该 captchaId 立即失效
     */
    @PostMapping("/verify")
    public Result<Boolean> verify(@Valid @RequestBody VerifyRequest request) {
        captchaService.verify(request.captchaId(), request.code());
        return Result.ok("验证通过", true);
    }

    public record VerifyRequest(
            @NotBlank(message = "captchaId 不能为空") String captchaId,
            @NotBlank(message = "验证码不能为空") String code) {
    }
}
```

#### 步骤 4 · 补上配置项

完整文件在第 2 章步骤 3 已经给过，这里只标出这个案例用到的两行。改完配置要重启，`@ConfigurationProperties` 不支持热更新。

```yaml
toolkit:
  captcha:
    expire-seconds: 60
    # 只用于本地练习，响应里会带答案；线上必须设成 false
    debug-code: true
```

### 3.4 接口实测

生成一张字母验证码。因为调试开关开着，响应里的 `code` 就是答案：

```bash
curl --noproxy '*' "http://127.0.0.1:8015/toolkit/captcha?type=line"
```

```json
{
  "code": 200,
  "data": {
    "captchaId": "e969cde3ddf24e72a51da044f1960de1",
    "image": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAHgAAAAoCAYAAAA16j4l...",
    "expireSeconds": 60,
    "type": "line",
    "code": "rofm"
  },
  "message": "操作成功"
}
```

先输错一次，错误次数会递减提示：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/captcha/verify \
  -H 'Content-Type: application/json' \
  -d '{"captchaId":"e969cde3ddf24e72a51da044f1960de1","code":"zzzz"}'
```

```json
{"code":41002,"data":null,"message":"验证码错误，还可尝试 2 次"}
```

再用正确的答案提交，通过：

```json
{"code":200,"data":true,"message":"验证通过"}
```

同一个 captchaId 再提交一次，已经查不到了：

```json
{"code":41001,"data":null,"message":"验证码不存在或已过期"}
```

连错三次，第三次直接作废，不再给剩余次数：

```json
{"code":41002,"data":null,"message":"验证码错误，还可尝试 2 次"}
{"code":41002,"data":null,"message":"验证码错误，还可尝试 1 次"}
{"code":41003,"data":null,"message":"错误次数过多，验证码已失效"}
```

请求体缺字段时，参数校验先拦住：

```json
{"code":40000,"data":null,"message":"验证码不能为空"}
```

算术验证码返回的答案是算好的结果，图片上画的是算式：

```bash
curl --noproxy '*' "http://127.0.0.1:8015/toolkit/captcha?type=math"
```

```json
{"code":200,"data":{"captchaId":"...","type":"math","code":"20","image":"data:image/png;base64,..."}}
```

### 3.5 容易踩的坑

1. 算术验证码提交算式。`getCode()` 是 `4*5=`，`MathGenerator.verify` 会先把用户输入转成整数，再和算式的计算结果比较。提交 `4*5=` 一定失败，要提交 20。前端如果靠 OCR 拿到算式，得先自己算。
2. GIF 的 Base64 前缀拼成 png。`getImageBase64()` 只给数据部分，前缀要自己拼。GIF 是多帧图，拼成 `data:image/png` 浏览器不认，图是空的。代码里按 style 判断了一次，就是为了这个。
3. 读缓存时传了续期。`cache.get(id, true)` 会让验证码在每次被读到时重新计时，配上前端定时刷新，一个验证码能用到天荒地老。校验和查询一律用 false。
4. 把答案放在响应里让前端比对。这样等于没有验证码，抓包就能过。调试字段上线前必须关掉，答案只能留在服务端。
5. `TimedCache` 不调 `schedulePrune`。过期项不会被清理，内存里堆着几万个已经失效的验证码。构造函数只设了 TTL，清理要靠定时任务，代码里给的是 30 秒一次。
6. 服务器上没有显示设备。画图依赖 AWT，Linux 容器里要 `-Djava.awt.headless=true`，启动类里已经设了，自己再 `new JFrame` 之类的代码才会出问题。

## 4. 案例 2：扫码登录

### 4.1 需求

PC 网页上显示一个二维码，用户用手机扫一下并确认，网页自动登录。整个过程网页不刷新，靠轮询拿状态。

拆成接口是五个：创建二维码、查询状态、模拟扫码、模拟确认、模拟取消。另外加一个 ascii 接口，方便在终端里直接看二维码，不用开浏览器。

状态只有四个，流转方向是固定的：

| 当前状态      | 能做什么  | 做完之后                 |
| --------- | ----- | -------------------- |
| WAITING   | 扫码、取消 | SCANNED / CANCELED   |
| SCANNED   | 确认、取消 | CONFIRMED / CANCELED |
| CONFIRMED | 无     | 已经是终态                |
| CANCELED  | 无     | 已经是终态                |

两个地方容易写错。一是跳步，没扫码就直接确认，这种请求必须拒绝；二是确认之后再取消，此时 token 已经签发，取消没有意义，也要拒绝。

二维码用 zxing 画，依赖已经在第 2 章的 pom 里引过，这里不用再动依赖。

### 4.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件                                                                            | 作用                  |
| -- | ----------------------------------------------------------------------------- | ------------------- |
| 1  | `15-toolkit/src/main/java/top/mqxu/toolkit/support/QrSession.java`            | 保存 ticket 与状态       |
| 2  | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/QrTicketResponse.java`         | 返回 ticket、内容、图片     |
| 3  | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/QrStatusResponse.java`         | 轮询时返回的对象            |
| 4  | `15-toolkit/src/main/java/top/mqxu/toolkit/service/QrLoginService.java`       | 生成二维码、状态流转、签发 token |
| 5  | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/QrLoginController.java` | 五个动作加一个 ascii       |


### 4.3 实现步骤

#### 步骤 1 · 定义会话对象

状态用枚举，不用字符串，写错状态名编译期就能发现，不用等到运行。三个可变字段都加了 `volatile`，因为轮询线程和扫码线程不是同一个，要保证可见性。

真正的并发安全靠 Service 里的 `synchronized (session)`，这里只解决可见性，两个手段不冲突。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/support/QrSession.java`

```java
package top.mqxu.toolkit.support;

import lombok.Getter;
import lombok.Setter;

/**
 * 扫码登录会话
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 状态流转：WAITING -> SCANNED -> CONFIRMED，任意阶段可 CANCELED
 */
@Getter
public class QrSession {

    public enum State {
        WAITING, SCANNED, CONFIRMED, CANCELED
    }

    private final String ticket;
    private final String content;
    private final long createdAt = System.currentTimeMillis();

    @Setter
    private volatile State state = State.WAITING;

    @Setter
    private volatile String phone;

    @Setter
    private volatile String token;

    public QrSession(String ticket, String content) {
        this.ticket = ticket;
        this.content = content;
    }
}
```

#### 步骤 2 · 定义创建结果

`content` 是二维码里真正写进去的字符串，调试时有用，扫码软件识别出来就是它。`image` 同样是 data URI。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/QrTicketResponse.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 二维码票据
 *
 * @param ticket        票据，轮询状态时回传
 * @param content       二维码里的内容
 * @param image         data URI
 * @param expireSeconds 有效期（秒）
 * @author mqxu
 * @date 2026-10-21
 */
public record QrTicketResponse(String ticket, String content, String image, long expireSeconds) {
}
```

#### 步骤 3 · 定义状态结果

`phone` 和 `token` 在确认之前都是 null。前端判断登录成功，只看 `state` 是不是 CONFIRMED。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/QrStatusResponse.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 扫码状态
 *
 * @param ticket 票据
 * @param state  WAITING / SCANNED / CONFIRMED / CANCELED
 * @param phone  确认登录的手机号，未确认时为 null
 * @param token  确认登录后签发的 token
 * @author mqxu
 * @date 2026-10-21
 */
public record QrStatusResponse(String ticket, String state, String phone, String token) {
}
```

#### 步骤 4 · 写扫码登录服务

二维码用 `QrConfig` 配置：260 见方、边距 1、深蓝前景色、纠错级别 H。纠错级别调高是为了中间能盖 logo，H 允许遮挡约 30% 的面积还能扫出来。logo 不用外部图片，`drawLogo()` 用 Java2D 现画一个蓝色圆角方块加一个字母，省得带资源文件。

二维码内容是 `toolkit://qr-login?ticket=xxx`，手机端扫出来从这个串里取 ticket 再调接口。真实的扫码登录会用自定义 scheme 唤起 App，这里用一样的思路。

状态流转的三处修改都套在 `synchronized (session)` 里。轮询、扫码、确认可能是三个线程同时进来，不加锁会出现两个请求都通过了状态判断，最后状态被覆盖。

确认时用 `JWTUtil.createToken` 签发 token，密钥直接取签名案例的 `app-secret`，省得再配一份。`ascii()` 走 `generateAsAsciiArt`，输出的是带 ANSI 颜色的方块字符，终端里能直接看。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/service/QrLoginService.java`

```java
package top.mqxu.toolkit.service;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import cn.hutool.jwt.JWTUtil;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.QrStatusResponse;
import top.mqxu.toolkit.dto.QrTicketResponse;
import top.mqxu.toolkit.support.QrSession;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 扫码登录服务
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 生成带 logo 的二维码，用 ticket 串起扫码、确认、签发 token 的完整流程
 */
@Service
@RequiredArgsConstructor
public class QrLoginService {

    private final ToolkitProperties properties;

    private TimedCache<String, QrSession> cache;
    private Image logo;

    @PostConstruct
    void init() {
        long ttl = properties.getQrLogin().getExpireSeconds() * 1000;
        cache = CacheUtil.newTimedCache(ttl);
        cache.schedulePrune(30_000);
        logo = drawLogo();
    }

    @PreDestroy
    void destroy() {
        cache.cancelPruneSchedule();
        cache.clear();
    }

    /**
     * 创建二维码，二维码内容里带 ticket
     */
    public QrTicketResponse create() {
        String ticket = IdUtil.fastSimpleUUID();
        String content = "toolkit://qr-login?ticket=" + ticket;

        cache.put(ticket, new QrSession(ticket, content));

        QrConfig config = QrConfig.create()
                .setWidth(260)
                .setHeight(260)
                .setMargin(1)
                .setForeColor(new Color(0x1F2D3D))
                .setBackColor(Color.WHITE)
                .setErrorCorrection(ErrorCorrectionLevel.H)
                .setImg(logo);

        String image = toDataUri(QrCodeUtil.generateAsBase64(content, config, "png"));
        return new QrTicketResponse(ticket, content, image,
                properties.getQrLogin().getExpireSeconds());
    }

    /**
     * 手机端扫码，WAITING -> SCANNED
     */
    public QrStatusResponse scan(String ticket) {
        QrSession session = get(ticket);
        synchronized (session) {
            requireState(session, QrSession.State.WAITING, "该二维码已被扫描或已失效");
            session.setState(QrSession.State.SCANNED);
        }
        return toResponse(session);
    }

    /**
     * 手机端确认登录，SCANNED -> CONFIRMED，此时签发 token
     */
    public QrStatusResponse confirm(String ticket, String phone) {
        QrSession session = get(ticket);
        synchronized (session) {
            requireState(session, QrSession.State.SCANNED, "请先扫码再确认");
            session.setPhone(phone);
            session.setToken(issueToken(ticket, phone));
            session.setState(QrSession.State.CONFIRMED);
        }
        return toResponse(session);
    }

    /**
     * 手机端取消，任意未完成状态都可以取消
     */
    public QrStatusResponse cancel(String ticket) {
        QrSession session = get(ticket);
        synchronized (session) {
            if (session.getState() == QrSession.State.CONFIRMED) {
                throw new BusinessException(ErrorCode.QR_STATE_CONFLICT, "已确认的登录不能取消");
            }
            session.setState(QrSession.State.CANCELED);
        }
        return toResponse(session);
    }

    /**
     * 前端轮询状态
     */
    public QrStatusResponse status(String ticket) {
        return toResponse(get(ticket));
    }

    /**
     * 在终端里查看二维码，调试用
     */
    public String ascii(String ticket) {
        return QrCodeUtil.generateAsAsciiArt(get(ticket).getContent());
    }

    private String issueToken(String ticket, String phone) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("phone", phone);
        payload.put("ticket", ticket);
        payload.put("iat", System.currentTimeMillis() / 1000);
        return JWTUtil.createToken(payload,
                properties.getSign().getAppSecret().getBytes(StandardCharsets.UTF_8));
    }

    private QrSession get(String ticket) {
        if (StrUtil.isBlank(ticket)) {
            throw new BusinessException(ErrorCode.QR_NOT_FOUND);
        }
        QrSession session = cache.get(ticket, false);
        if (session == null) {
            throw new BusinessException(ErrorCode.QR_NOT_FOUND);
        }
        return session;
    }

    private void requireState(QrSession session, QrSession.State expected, String message) {
        if (session.getState() != expected) {
            throw new BusinessException(ErrorCode.QR_STATE_CONFLICT, message);
        }
    }

    private QrStatusResponse toResponse(QrSession session) {
        return new QrStatusResponse(session.getTicket(), session.getState().name(),
                session.getPhone(), session.getToken());
    }

    private String toDataUri(String base64) {
        return base64.startsWith("data:") ? base64 : "data:image/png;base64," + base64;
    }

    /**
     * 程序画一个方形 logo，避免依赖外部图片文件
     */
    private Image drawLogo() {
        int size = 56;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x2F6FED));
        g.fillRoundRect(0, 0, size, size, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        String text = "T";
        int textWidth = g.getFontMetrics().stringWidth(text);
        int textHeight = g.getFontMetrics().getAscent();
        g.drawString(text, (size - textWidth) / 2, (size + textHeight) / 2 - 4);
        g.dispose();
        return image;
    }
}
```

#### 步骤 5 · 写控制器

ticket 和 phone 都用 `@RequestParam` 接，模拟手机端的调用。`confirm` 里额外校验了手机号格式，因为这是登录身份的来源，不能随便传。

ascii 接口的 `produces` 要写成 `text/plain;charset=UTF-8`，否则中文和制表字符会乱码。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/QrLoginController.java`

```java
package top.mqxu.toolkit.controller;

import cn.hutool.core.lang.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.QrStatusResponse;
import top.mqxu.toolkit.dto.QrTicketResponse;
import top.mqxu.toolkit.service.QrLoginService;

/**
 * 案例 2：扫码登录
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/qr-login")
@RequiredArgsConstructor
public class QrLoginController {

    private final QrLoginService qrLoginService;

    /**
     * 生成二维码
     */
    @PostMapping("/create")
    public Result<QrTicketResponse> create() {
        return Result.ok(qrLoginService.create());
    }

    /**
     * 前端轮询，建议 1 到 2 秒一次
     */
    @GetMapping("/status")
    public Result<QrStatusResponse> status(@RequestParam String ticket) {
        return Result.ok(qrLoginService.status(ticket));
    }

    /**
     * 模拟手机端扫码
     */
    @PostMapping("/scan")
    public Result<QrStatusResponse> scan(@RequestParam String ticket) {
        return Result.ok("扫码成功", qrLoginService.scan(ticket));
    }

    /**
     * 模拟手机端确认登录
     */
    @PostMapping("/confirm")
    public Result<QrStatusResponse> confirm(@RequestParam String ticket,
                                            @RequestParam String phone) {
        if (!Validator.isMobile(phone)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
        return Result.ok("登录成功", qrLoginService.confirm(ticket, phone));
    }

    /**
     * 模拟手机端取消
     */
    @PostMapping("/cancel")
    public Result<QrStatusResponse> cancel(@RequestParam String ticket) {
        return Result.ok("已取消", qrLoginService.cancel(ticket));
    }

    /**
     * 终端里查看二维码，纯文本
     */
    @GetMapping(value = "/ascii", produces = "text/plain;charset=UTF-8")
    public String ascii(@RequestParam String ticket) {
        return qrLoginService.ascii(ticket);
    }
}
```

### 4.4 接口实测

创建二维码，拿到 ticket：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/qr-login/create
```

```json
{
  "ticket": "4ef1d2479ac3409a977aecf00f98b8de",
  "content": "toolkit://qr-login?ticket=4ef1d2479ac3409a977aecf00f98b8de",
  "image": "data:image/png;base64,iVBORw0KGgoAAAANSU...",
  "expireSeconds": 120
}
```

此时轮询，状态是 WAITING：

```json
{"code":200,"data":{"ticket":"4ef1d2479ac3409a977aecf00f98b8de","state":"WAITING","phone":null,"token":null},"message":"操作成功"}
```

没扫码就确认，被状态机挡住：

```json
{"code":41012,"data":null,"message":"请先扫码再确认"}
```

模拟手机扫码，进入 SCANNED：

```bash
curl --noproxy '*' -X POST "http://127.0.0.1:8015/toolkit/qr-login/scan?ticket=4ef1d2479ac3409a977aecf00f98b8de"
```

```json
{"code":200,"data":{"ticket":"4ef1d2479ac3409a977aecf00f98b8de","state":"SCANNED","phone":null,"token":null},"message":"扫码成功"}
```

确认登录，状态变 CONFIRMED，同时带回 token：

```bash
curl --noproxy '*' -X POST "http://127.0.0.1:8015/toolkit/qr-login/confirm?ticket=4ef1d2479ac3409a977aecf00f98b8de&phone=13800001111"
```

```json
{"code":200,"data":{"ticket":"4ef1d2479ac3409a977aecf00f98b8de","state":"CONFIRMED","phone":"13800001111","token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."},"message":"登录成功"}
```

确认之后再取消，不允许：

```json
{"code":41012,"data":null,"message":"已确认的登录不能取消"}
```

ticket 不存在或已过期：

```json
{"code":41011,"data":null,"message":"二维码不存在或已过期"}
```

在终端里看二维码，前几行是这样的方块字符（带 ANSI 颜色，这里去掉了转义码）：

```text
█▀▀▀▀▀▀▀█▀▀█▀█▀▀▀▀█▀▀█▀▀▀▀█▀▀▀▀▀▀▀█
█ █▀▀▀█ █▀▄▄██▄█▀▄▄ ▀ █▀███ █▀▀▀█ █
█ █   █ █ ▀██▄▄▀█▄ ▄█▄██▀▄█ █   █ █
```

### 4.5 容易踩的坑

1. 状态判断和状态修改不在同一把锁里。先 `if (state == WAITING)` 再 `setState(SCANNED)`，两个请求同时进来会都通过判断。要么给整个方法加锁，要么像这里一样锁住 session 对象，把判断和修改放在同一个同步块内。
2. 确认后还允许取消。取消一般会清掉登录凭据，如果 token 已经发给网页了，再清服务端没有意义，用户照样能用 token 访问。所以确认后置为终态，拒绝后续所有操作。
3. 二维码扫不出来，多半是 logo 太大或者纠错级别太低。中间盖图要配 `ErrorCorrectionLevel.H`，logo 面积控制在 20% 以内；L 级别只能挡住 7% 左右。
4. 轮询频率太高。一秒一次已经够，500 毫秒一次在几百人同时登录时就是几千 QPS 的无用请求。真实的扫码登录还会在成功时推送一次，减少轮询。
5. ticket 直接放在二维码内容里，没有做签名。攻击者自己造一个 ticket 去轮询，只会拿到 41011，因为服务端查不到；但如果 ticket 能被猜到，别人就能抢登。UUID 够长，实践中够用，要求更高时应在内容里加时间戳和签名。
6. ascii 接口不带 charset，中文和全角字符会乱码。`produces` 必须写成带 `charset=UTF-8` 的 text/plain。

## 5. 案例 3：商品海报

### 5.1 需求

运营要一张能直接发朋友圈的商品图：上面是商品照片，下面是半透明黑条，条上写标题和价格，右下角放一个二维码，扫码进商品页。

用户只传一张商品图，标题、价格、二维码内容都用参数传，服务端合成后直接返回 PNG。不上传图时要能用渐变底图顶上，保证接口随时可测。

海报里的二维码同样走 zxing，依赖已经引过。图片处理用 Hutool 自带的 Img 和 ImgUtil，不需要额外的图像库。

### 5.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件                                                                           | 作用              |
| -- | ---------------------------------------------------------------------------- | --------------- |
| 1  | `15-toolkit/src/main/java/top/mqxu/toolkit/service/PosterService.java`       | 缩放、圆角、底栏、文字、二维码 |
| 2  | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/PosterController.java` | 返回 PNG 二进制      |


### 5.3 实现步骤

#### 步骤 1 · 写海报合成服务

合成顺序不能乱：先把原图按宽 600 等比缩放并补足高度，再切圆角，再画半透明底栏，最后写文字和二维码。顺序反了，底栏会被裁掉，二维码会被圆角切掉一个角。

`Img.round(0.03)` 的参数是圆角半径占短边的比例，不是像素值。传 24 会抛异常，这也是很多人第一次用 Img 时卡住的地方。

底栏用 `AlphaComposite` 设 0.62 的透明度，比纯黑好看，也不会挡死底图。文字用 `StrUtil.maxLength` 截断到 16 个字，不然长标题会压到二维码上。

二维码用 `QrCodeUtil.generate(content, 140, 140)` 直接出 BufferedImage，再用 `drawImage` 贴到右下角。

没有上传图时走 `loadSource` 的 else 分支，画一张从蓝到紫的渐变图，中间写“商品图”三个字。这样 curl 不带文件也能出图，联调时省事。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/service/PosterService.java`

```java
package top.mqxu.toolkit.service;

import cn.hutool.core.img.Img;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 商品海报生成
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 缩放、圆角、半透明底栏、文字、二维码，一次合成
 */
@Service
@RequiredArgsConstructor
public class PosterService {

    private static final int WIDTH = 600;
    private static final int BAR_HEIGHT = 180;

    public byte[] render(MultipartFile file, String title, String price, String qrContent) {
        BufferedImage source = loadSource(file);
        BufferedImage canvas = fit(source);
        // round 的参数是圆角半径占短边的比例，不是像素值
        canvas = ImgUtil.toBufferedImage(Img.from(canvas).round(0.03).getImg());

        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 底部半透明黑条，保证文字在任何底图上都能看清
        int barTop = canvas.getHeight() - BAR_HEIGHT;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.62f));
        g.setColor(Color.BLACK);
        g.fillRect(0, barTop, canvas.getWidth(), BAR_HEIGHT);
        g.setComposite(AlphaComposite.SrcOver);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        g.drawString(StrUtil.maxLength(StrUtil.blankToDefault(title, "商品名称"), 16),
                32, barTop + 62);

        g.setColor(new Color(0xFFB020));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        g.drawString("¥ " + StrUtil.blankToDefault(price, "0.00"), 32, barTop + 130);

        String content = StrUtil.blankToDefault(qrContent, "https://example.com");
        BufferedImage qr = QrCodeUtil.generate(content, 140, 140);
        g.drawImage(qr, canvas.getWidth() - 172, canvas.getHeight() - 160, null);

        g.dispose();
        return ImgUtil.toBytes(canvas, "png");
    }

    /**
     * 没有上传图片时生成一张渐变底图，保证接口随时可测
     */
    private BufferedImage loadSource(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            BufferedImage image = new BufferedImage(WIDTH, 800, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setPaint(new GradientPaint(0, 0, new Color(0x2F6FED),
                    0, 800, new Color(0x8A4BF0)));
            g.fillRect(0, 0, WIDTH, 800);
            g.setColor(new Color(255, 255, 255, 90));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 120));
            g.drawString("商品图", 150, 420);
            g.dispose();
            return image;
        }
        try {
            return ImgUtil.toBufferedImage(Img.from(file.getInputStream()).getImg());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片读取失败：" + e.getMessage());
        }
    }

    /**
     * 按宽度 600 等比缩放，高度至少 800，不足的补白
     */
    private BufferedImage fit(BufferedImage source) {
        double ratio = (double) WIDTH / source.getWidth();
        int height = Math.max(800, (int) Math.round(source.getHeight() * ratio));

        BufferedImage target = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, height);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, WIDTH, height, null);
        g.dispose();
        return target;
    }
}
```

#### 步骤 2 · 写控制器

`produces = MediaType.IMAGE_PNG_VALUE`，返回 `ResponseEntity<byte[]>`。不套 Result，因为这是图片不是 JSON。`Content-Disposition` 用 inline，浏览器直接显示，加 `attachment` 就变成下载。

`file` 用 `@RequestParam(required = false) MultipartFile` 接，不传时为 null，Service 里会走默认底图。注意 multipart 参数不能和 `@RequestBody` 混用，这里全用表单参数，所以没问题。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/PosterController.java`

```java
package top.mqxu.toolkit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.service.PosterService;

/**
 * 案例 3：商品海报
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/poster")
@RequiredArgsConstructor
public class PosterController {

    private final PosterService posterService;

    /**
     * 生成海报，直接返回 PNG 二进制，浏览器打开就能看到
     *
     * @param file      商品图，可不传，不传时用渐变底图
     * @param title     标题
     * @param price     价格
     * @param qrContent 二维码内容
     */
    @PostMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> render(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(defaultValue = "春季新品") String title,
            @RequestParam(defaultValue = "199.00") String price,
            @RequestParam(required = false) String qrContent) {
        byte[] png = posterService.render(file, title, price, qrContent);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Content-Disposition", "inline; filename=poster.png")
                .body(png);
    }
}
```

### 5.4 接口实测

不带图片，用默认底图，中文参数用 form 方式传：

```bash
curl --noproxy '*' -X POST \
  -F "title=秋季新品" -F "price=2999.00" -F "qrContent=https://example.com" \
  http://127.0.0.1:8015/toolkit/poster -o /tmp/poster.png -w '%{http_code} %{size_download}\n'
file /tmp/poster.png
```

```text
200 20527
/tmp/poster.png: PNG image data, 600 x 800, 8-bit/color RGBA, non-interlaced
```

换成自己上传的图，尺寸会按宽 600 等比缩放，高度不足 800 补白：

```bash
curl --noproxy '*' -X POST \
  -F "file=@/tmp/goods.jpg" -F "title=真皮双肩包" -F "price=499.00" \
  http://127.0.0.1:8015/toolkit/poster -o /tmp/poster2.png
file /tmp/poster2.png
```

把中文直接写在 URL 上不编码，请求会被 Tomcat 挡在进入 Spring 之前，返回的是 Tomcat 自带的 HTML 错误页，不是统一响应：

```bash
curl --noproxy '*' -o /tmp/p400.html -w '%{http_code}\n' -X POST "http://127.0.0.1:8015/toolkit/poster?title=秋季新品"
```

```text
400
<!doctype html><html lang="en"><head><title>HTTP Status 400 – Bad Request</title>...
```

### 5.5 容易踩的坑

1. 中文参数不编码。`?title=秋季新品` 里的中文没有 percent-encoding，Tomcat 的 URL 解码按 ISO-8859-1 处理，遇到非法字节直接返回 400，而且这个 400 在进入 Spring 之前就发生了，全局异常处理器接不到，前端拿到的是 Tomcat 的 HTML 错误页。用 `-F` 表单方式，或者 `--data-urlencode`，浏览器和 Postman 会自动处理。
2. `Img.round()` 的参数理解错。它要的是比例（0 到 0.5 之间），不是像素。想要 24 像素的圆角，得先算出 24 除以短边。
3. 合成顺序颠倒。先写字再切圆角，字会被裁；先贴二维码再画底栏，二维码被盖住一半。顺序是缩放、圆角、底栏、文字、二维码。
4. 服务器上没有中文字体。`Font.SANS_SERIF` 在最小化的 Linux 镜像里可能落到没有中文字形的字体上，文字全变方框。要么装 `fontconfig` 和一套中文字体，要么把字体文件打进镜像并用 `Font.createFont` 加载。
5. 把商品原图直接当输出。原图 4000 像素宽，输出也是 4000，一张海报几 MB，微信上传都费劲。必须限制输出宽度，这里固定 600。
6. 每次请求都重新画 logo 或加载字体。这些是常量，放到 `@PostConstruct` 里初始化一次，或者用静态字段，别放在方法里。

## 6. 案例 4：短链服务

### 6.1 需求

短信里的链接太长会被拆行，也会被运营商拦。需要一个接口把长链换成短链，访问短链时 302 跳回原地址，同时记录被点了多少次。

三个接口：创建短链、访问短链、查统计。短码 6 位，默认 7 天过期，创建时可以指定更短的有效期。

### 6.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | `15-toolkit/src/main/java/top/mqxu/toolkit/support/ShortLink.java` | 保存原链、过期时间、点击数 |
| 2 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/ShortLinkResponse.java` | 返回短码和完整短链 |
| 3 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/ShortLinkStats.java` | 短码、原链、点击数、时间 |
| 4 | `15-toolkit/src/main/java/top/mqxu/toolkit/service/ShortLinkService.java` | 生成短码、解析跳转、统计 |
| 5 | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/ShortLinkController.java` | 创建、跳转、统计 |


### 6.3 实现步骤

#### 步骤 1 · 定义短链记录

点击数用 `AtomicLong`，因为跳转接口会被并发调用，普通 long 自增会丢数。过期时间在创建时算好存下来，判断过期交给缓存，这里只用于展示。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/support/ShortLink.java`

```java
package top.mqxu.toolkit.support;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 短链记录
 *
 * @author mqxu
 * @date 2026-10-21
 */
@Getter
public class ShortLink {

    private final String code;
    private final String url;
    private final long createdAt = System.currentTimeMillis();
    private final long expireAt;
    private final AtomicLong clicks = new AtomicLong();

    public ShortLink(String code, String url, long expireAt) {
        this.code = code;
        this.url = url;
        this.expireAt = expireAt;
    }
}
```

#### 步骤 2 · 定义创建结果

`shortUrl` 是拼好的完整地址，调用方拿到就能直接用，不用自己拼域名。`expireAt` 给的是毫秒时间戳，前端要显示成日期自己格式化。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/ShortLinkResponse.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 短链创建结果
 *
 * @param code     短码
 * @param shortUrl 完整短链
 * @param url      原始长链
 * @param expireAt 过期时间戳（毫秒）
 * @author mqxu
 * @date 2026-10-21
 */
public record ShortLinkResponse(String code, String shortUrl, String url, long expireAt) {
}
```

#### 步骤 3 · 定义统计结果

和创建结果分开，是因为统计接口不需要再返回一次 shortUrl，字段含义也不同。两个 record 都小，没必要合并。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/ShortLinkStats.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 短链统计
 *
 * @param code     短码
 * @param url      原始长链
 * @param clicks   点击次数
 * @param createdAt 创建时间戳（毫秒）
 * @param expireAt 过期时间戳（毫秒）
 * @author mqxu
 * @date 2026-10-21
 */
public record ShortLinkStats(String code, String url, long clicks, long createdAt, long expireAt) {
}
```

#### 步骤 4 · 写短链服务

字符表去掉了 0、O、1、l、I 这几个容易看混的字符。短链经常要人工念、手工输，少几个歧义字符能省很多客服工单。

短码用 `RandomUtil.randomString` 生成，6 位字符表大小 54，空间约 240 亿，够用。生成后检查是否已被占用，冲突就重试，最多 5 次，还不行就抛错让调用方重试，而不是死循环。

解析时用 `cache.get(code, false)`，不续期。用 true 的话，每次点击都会把过期时间往后推，短链永远不过期。

跳转用 302 而不是 301。301 会被浏览器永久缓存，同一个短码以后再改指向，用户那边还是跳老地址。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/service/ShortLinkService.java`

```java
package top.mqxu.toolkit.service;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.ShortLinkResponse;
import top.mqxu.toolkit.dto.ShortLinkStats;
import top.mqxu.toolkit.support.ShortLink;

/**
 * 短链服务
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 随机短码 + 本地缓存 + 点击计数，重点是冲突处理与过期
 */
@Service
@RequiredArgsConstructor
public class ShortLinkService {

    /**
     * 去掉容易看混的 0O1lI
     */
    private static final String ALPHABET =
            "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_RETRY = 5;

    private final ToolkitProperties properties;

    private TimedCache<String, ShortLink> cache;

    @PostConstruct
    void init() {
        cache = CacheUtil.newTimedCache(properties.getShortLink().getExpireSeconds() * 1000);
        cache.schedulePrune(60_000);
    }

    @PreDestroy
    void destroy() {
        cache.cancelPruneSchedule();
        cache.clear();
    }

    public ShortLinkResponse create(String url, Long ttlSeconds) {
        if (!Validator.isUrl(url)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "url 不是合法的地址");
        }
        long ttl = ttlSeconds == null || ttlSeconds <= 0
                ? properties.getShortLink().getExpireSeconds()
                : ttlSeconds;

        String code = allocateCode();
        long expireAt = System.currentTimeMillis() + ttl * 1000;
        cache.put(code, new ShortLink(code, url, expireAt), ttl * 1000);

        String baseUrl = StrUtil.removeSuffix(properties.getShortLink().getBaseUrl(), "/");
        return new ShortLinkResponse(code, baseUrl + "/toolkit/s/" + code, url, expireAt);
    }

    /**
     * 解析短码，点一次算一次
     */
    public String resolve(String code) {
        ShortLink link = cache.get(code, false);
        if (link == null) {
            throw new BusinessException(ErrorCode.SHORT_LINK_NOT_FOUND);
        }
        link.getClicks().incrementAndGet();
        return link.getUrl();
    }

    public ShortLinkStats stats(String code) {
        ShortLink link = cache.get(code, false);
        if (link == null) {
            throw new BusinessException(ErrorCode.SHORT_LINK_NOT_FOUND);
        }
        return new ShortLinkStats(link.getCode(), link.getUrl(), link.getClicks().get(),
                link.getCreatedAt(), link.getExpireAt());
    }

    /**
     * 生成一个没被占用的短码
     */
    private String allocateCode() {
        for (int i = 0; i < MAX_RETRY; i++) {
            String code = RandomUtil.randomString(ALPHABET, CODE_LENGTH);
            if (!cache.containsKey(code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "短码分配失败，请重试");
    }
}
```

#### 步骤 5 · 写控制器

跳转接口返回 `ResponseEntity<Void>`，状态码 `HttpStatus.FOUND` 就是 302，`Location` 头放原地址，body 为空。

创建接口的 `url` 加了 `@NotBlank`，格式校验放在 Service 里用 `Validator.isUrl`。两层校验不重复：一个是必填，一个是格式。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/ShortLinkController.java`

```java
package top.mqxu.toolkit.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.ShortLinkResponse;
import top.mqxu.toolkit.dto.ShortLinkStats;
import top.mqxu.toolkit.service.ShortLinkService;

import java.net.URI;

/**
 * 案例 4：短链服务
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit")
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    @PostMapping("/short-link")
    public Result<ShortLinkResponse> create(@Valid @RequestBody CreateRequest request) {
        return Result.ok(shortLinkService.create(request.url(), request.ttlSeconds()));
    }

    /**
     * 访问短链，302 跳到原始地址
     */
    @GetMapping("/s/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String url = shortLinkService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    @GetMapping("/short-link/{code}")
    public Result<ShortLinkStats> stats(@PathVariable String code) {
        return Result.ok(shortLinkService.stats(code));
    }

    public record CreateRequest(
            @NotBlank(message = "url 不能为空") String url,
            Long ttlSeconds) {
    }
}
```

### 6.4 接口实测

创建短链：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/short-link \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/a/very/long/path?x=1&y=2","ttlSeconds":604800}'
```

```json
{"code":200,"data":{"code":"fQq27s","shortUrl":"http://127.0.0.1:8015/toolkit/s/fQq27s","url":"https://example.com/a/very/long/path?x=1&y=2","expireAt":1793156205360},"message":"操作成功"}
```

访问短链，返回 302 和 Location（用 -i 看响应头，不要用 -L，否则会跟到外网）：

```bash
curl --noproxy '*' -i "http://127.0.0.1:8015/toolkit/s/fQq27s"
```

```http
HTTP/1.1 302
Location: https://example.com/a/very/long/path?x=1&y=2
Content-Length: 0
```

再查统计，点击数变成 1：

```json
{"code":200,"data":{"code":"fQq27s","url":"https://example.com/a/very/long/path?x=1&y=2","clicks":1,"createdAt":1792551405360,"expireAt":1793156205360},"message":"操作成功"}
```

传一个不合法的地址：

```json
{"code":40000,"data":null,"message":"url 不是合法的地址"}
```

访问不存在的短码：

```json
{"code":41021,"data":null,"message":"短链不存在或已过期"}
```

### 6.5 容易踩的坑

1. 跳转用 301。浏览器会永久缓存，短码对应的地址改了以后，老用户还是一直跳旧地址，而且清缓存之前没法恢复。跳转一律用 302 或 307。
2. 解析时给缓存续期。`cache.get(code, true)` 会把 TTL 重置，短链变成永不过期，统计也会一直涨。
3. 短码用顺序 ID 转 62 进制。别人可以从短码推算出你一共有多少条短链，还能遍历。要么加随机前缀，要么直接用随机串。
4. 冲突处理写成死循环。`while (containsKey(code)) code = random()` 在空间快满时会卡死线程。给一个重试上限，超了抛异常。
5. 统计放内存。重启清零，多实例各算各的。真实项目点击计数写入 Redis 的 Hash 或直接落库，短链映射本身也放 Redis，并设置和业务一致的 TTL。
6. 短链域名写死成请求域名。网关转发、内网调用时 `getRequestURL` 拿到的是内部地址，拼出来的短链外网打不开。域名放配置里。

## 7. 案例 5：Excel 导入导出

### 7.1 需求

后台最常见的两个功能：把订单导成 Excel 给财务，把别人填好的 Excel 导进来做批量处理。前者的难点在样式，后者的难点在校验和错误提示。

导出要求：表头深蓝底白字、冻结首行、金额千分位保留两位、状态列按值着色、状态列带下拉框。导入要求：逐行校验，所有错误一次性收集起来，生成一份标红的错误明细让用户下载。

表头固定六列：订单号、客户、手机号、金额、状态、下单时间。

这个案例要用 POI，依赖已经在第 2 章的 pom 里引过（poi-ooxml 5.5.1），这里不用再动依赖。

### 7.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/OrderRow.java` | 接住 Excel 的每一行 |
| 2 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/ImportResult.java` | 汇总加错误明细 |
| 3 | `15-toolkit/src/main/java/top/mqxu/toolkit/service/ExcelService.java` | 导出、模板、导入、错误明细 |
| 4 | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/ExcelController.java` | 四个下载与上传接口 |


### 7.3 实现步骤

#### 步骤 1 · 定义订单行

字段全部用 String，不用 BigDecimal 和 Date。原因是导入文件由用户自己填，金额可能写成 `abc`，时间可能写成 `2026/10/21`。如果字段类型是 BigDecimal，`readAll` 在转换时就抛异常，整批数据全废，而且报错信息只有一句类型转换失败，用户不知道自己哪一行错了。

用 String 接住原始值，再在业务层逐行校验，才能做到“哪一行、哪一列、错在哪”。

`@Alias` 把中文表头和字段名对上，省得要求用户把表头改成英文。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/OrderRow.java`

```java
package top.mqxu.toolkit.dto;

import cn.hutool.core.annotation.Alias;
import lombok.Data;

/**
 * 订单行
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 导入时所有字段先按字符串接收，日期和金额在业务层解析，避免格式差异直接抛异常
 */
@Data
public class OrderRow {

    @Alias("订单号")
    private String orderNo;

    @Alias("客户")
    private String customer;

    @Alias("手机号")
    private String phone;

    @Alias("金额")
    private String amount;

    @Alias("状态")
    private String status;

    @Alias("下单时间")
    private String createdAt;
}
```

#### 步骤 2 · 定义导入结果

`errors` 是明细列表，内嵌一个 `RowError` record，三个字段分别对应行号、订单号、原因。行号是 Excel 里的真实行号，不是列表下标，用户拿着行号能直接定位。

`errorFileUrl` 在有错误时才给地址，没错误就是 null，前端据此决定要不要显示下载按钮。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/ImportResult.java`

```java
package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 导入结果
 *
 * @param total        总行数
 * @param success      成功行数
 * @param failed       失败行数
 * @param errors       错误明细
 * @param errorFileUrl 错误明细文件下载地址，没有错误时为 null
 * @author mqxu
 * @date 2026-10-21
 */
public record ImportResult(int total, int success, int failed,
                           List<RowError> errors, String errorFileUrl) {

    public record RowError(int row, String orderNo, String message) {
    }
}
```

#### 步骤 3 · 写 Excel 服务

导出这条线：`ExcelUtil.getWriter(true)` 建一个 xlsx 写入器，`renameSheet` 改表名，`writeHeadRow` 手写表头，`write(rows, false)` 写数据（false 表示不再写一次表头）。之后加金额格式、状态列颜色、冻结首行、列宽、状态下拉，最后 `flush` 到内存流。

样式上有一点要注意：`writer.getHeadCellStyle()` 拿到的是表头样式，改完对已经写出的表头也生效。状态列的颜色是用 `getSheet().getRow(i).getCell(4)` 逐格设置，因为要按每行的值选不同样式。

导入这条线：`ExcelUtil.getReader(InputStream)` 读，`readAll(OrderRow.class)` 按 `@Alias` 映射。读出来之后逐行 `validate`，遇到第一个错误就记下来并继续下一行，不中断，这样才能一次返回所有问题。

行号算的是 `i + 2`：列表下标从 0 开始，Excel 第一行是表头，所以第一行数据是第 2 行。这里最容易差一，用户拿着行号去表里找，差一行就找错了。

错误明细写入 `work-dir`，文件名用 `import-error-` 加 UUID，返回下载路径。`errorFile` 里做了白名单校验：必须以 `import-error-` 开头，不能带斜杠和 `..`，防止有人拿 `../application.yml` 来读配置。

`template()` 单独一个方法，因为模板要带一行示例数据和下拉框，和导出报表不一样。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/service/ExcelService.java`

```java
package top.mqxu.toolkit.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.ImportResult;
import top.mqxu.toolkit.dto.OrderRow;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel 导入导出
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 导出带样式，导入逐行校验并生成标红的错误明细
 */
@Service
@RequiredArgsConstructor
public class ExcelService {

    private static final List<String> HEADERS =
            List.of("订单号", "客户", "手机号", "金额", "状态", "下单时间");
    private static final List<String> STATUSES = List.of("待支付", "已支付", "已取消");

    private final ToolkitProperties properties;

    /**
     * 导出订单报表
     */
    public byte[] export() {
        List<Map<String, Object>> rows = demoData();
        try (ExcelWriter writer = ExcelUtil.getWriter(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            writer.renameSheet("订单");
            writer.writeHeadRow(HEADERS);
            styleHeader(writer);
            writer.write(rows, false);

            // 金额列保留两位小数
            CellStyle moneyStyle = writer.getWorkbook().createCellStyle();
            moneyStyle.setDataFormat(writer.getWorkbook().createDataFormat().getFormat("#,##0.00"));
            writer.setColumnStyleIfHasData(3, 1, moneyStyle);

            colorStatusColumn(writer, rows.size());
            writer.setFreezePane(1);
            setColumnWidths(writer);
            addStatusValidation(writer.getSheet());

            writer.flush(out, true);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出失败：" + e.getMessage());
        }
    }

    /**
     * 导入模板：表头 + 一行示例 + 状态下拉
     */
    public byte[] template() {
        try (ExcelWriter writer = ExcelUtil.getWriter(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            writer.renameSheet("订单导入");
            writer.writeHeadRow(HEADERS);
            styleHeader(writer);

            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("订单号", "SO20261021001");
            sample.put("客户", "张三");
            sample.put("手机号", "13800001001");
            sample.put("金额", new BigDecimal("199.00"));
            sample.put("状态", "待支付");
            sample.put("下单时间", "2026-10-21 10:00:00");
            writer.write(List.of(sample), false);

            writer.setFreezePane(1);
            addStatusValidation(writer.getSheet());
            setColumnWidths(writer);

            writer.flush(out, true);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板生成失败：" + e.getMessage());
        }
    }

    /**
     * 导入并校验，失败行写进一份标红的 Excel
     */
    public ImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        List<OrderRow> rows;
        try (ExcelReader reader = ExcelUtil.getReader(file.getInputStream())) {
            rows = reader.readAll(OrderRow.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXCEL_READ_FAILED,
                    "无法解析文件，请使用 .xlsx 格式：" + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.EXCEL_EMPTY);
        }

        List<ImportResult.RowError> errors = new ArrayList<>();
        Set<String> seenOrderNos = new HashSet<>();
        int total = 0;
        int success = 0;

        for (int i = 0; i < rows.size(); i++) {
            OrderRow row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            total++;
            // Excel 第 1 行是表头，数据从第 2 行开始
            int lineNo = i + 2;
            String message = validate(row, seenOrderNos);
            if (message == null) {
                success++;
            } else {
                errors.add(new ImportResult.RowError(lineNo, row.getOrderNo(), message));
            }
        }

        String errorFileUrl = errors.isEmpty() ? null : writeErrorReport(errors);
        return new ImportResult(total, success, errors.size(), errors, errorFileUrl);
    }

    /**
     * 下载错误明细，文件名做了白名单校验，避免路径穿越
     */
    public File errorFile(String fileName) {
        if (StrUtil.isBlank(fileName)
                || !fileName.startsWith("import-error-")
                || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        File file = new File(properties.getWorkDir(), fileName);
        if (!file.exists()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在或已被清理");
        }
        return file;
    }

    private String validate(OrderRow row, Set<String> seenOrderNos) {
        if (StrUtil.isBlank(row.getOrderNo())) {
            return "订单号不能为空";
        }
        if (!seenOrderNos.add(row.getOrderNo().trim())) {
            return "订单号重复：" + row.getOrderNo();
        }
        if (StrUtil.isBlank(row.getCustomer())) {
            return "客户不能为空";
        }
        if (!Validator.isMobile(StrUtil.trim(row.getPhone()))) {
            return "手机号格式不正确：" + StrUtil.nullToEmpty(row.getPhone());
        }

        BigDecimal amount = parseAmount(row.getAmount());
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "金额必须是大于 0 的数字：" + StrUtil.nullToEmpty(row.getAmount());
        }
        if (!STATUSES.contains(StrUtil.trim(row.getStatus()))) {
            return "状态只能是待支付、已支付、已取消：" + StrUtil.nullToEmpty(row.getStatus());
        }
        if (!isDateTime(row.getCreatedAt())) {
            return "下单时间格式应为 yyyy-MM-dd HH:mm:ss：" + StrUtil.nullToEmpty(row.getCreatedAt());
        }
        return null;
    }

    private BigDecimal parseAmount(String value) {
        try {
            return Convert.toBigDecimal(StrUtil.trim(value));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isDateTime(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        try {
            DateUtil.parse(value, DatePattern.NORM_DATETIME_PATTERN);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBlankRow(OrderRow row) {
        return StrUtil.isBlank(row.getOrderNo())
                && StrUtil.isBlank(row.getCustomer())
                && StrUtil.isBlank(row.getPhone())
                && StrUtil.isBlank(row.getAmount())
                && StrUtil.isBlank(row.getStatus())
                && StrUtil.isBlank(row.getCreatedAt());
    }

    private String writeErrorReport(List<ImportResult.RowError> errors) {
        File dir = FileUtil.mkdir(properties.getWorkDir());
        String fileName = "import-error-" + IdUtil.fastSimpleUUID() + ".xlsx";
        File file = new File(dir, fileName);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ImportResult.RowError error : errors) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("行号", error.row());
            row.put("订单号", StrUtil.nullToEmpty(error.orderNo()));
            row.put("原因", error.message());
            rows.add(row);
        }

        try (ExcelWriter writer = ExcelUtil.getWriter(true)) {
            writer.renameSheet("错误明细");
            writer.writeHeadRow(List.of("行号", "订单号", "原因"));
            styleHeader(writer);
            writer.write(rows, false);

            CellStyle red = writer.getWorkbook().createCellStyle();
            Font font = writer.createFont();
            font.setColor(IndexedColors.RED.getIndex());
            red.setFont(font);
            for (int i = 1; i <= rows.size(); i++) {
                Row row = writer.getSheet().getRow(i);
                if (row == null) {
                    continue;
                }
                for (int c = 0; c < 3; c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null) {
                        cell.setCellStyle(red);
                    }
                }
            }

            writer.setColumnWidth(0, 8);
            writer.setColumnWidth(1, 22);
            writer.setColumnWidth(2, 56);
            writer.flush(file);
        }
        return "/toolkit/excel/download/" + fileName;
    }

    private void styleHeader(ExcelWriter writer) {
        CellStyle style = writer.getHeadCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = writer.createFont();
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setBold(true);
        style.setFont(font);
    }

    private void colorStatusColumn(ExcelWriter writer, int dataSize) {
        Map<String, CellStyle> styles = new HashMap<>();
        styles.put("已支付", fillStyle(writer, IndexedColors.LIGHT_GREEN, IndexedColors.DARK_GREEN));
        styles.put("待支付", fillStyle(writer, IndexedColors.LIGHT_ORANGE, IndexedColors.ORANGE));
        styles.put("已取消", fillStyle(writer, IndexedColors.GREY_25_PERCENT, IndexedColors.GREY_50_PERCENT));

        Sheet sheet = writer.getSheet();
        for (int i = 1; i <= dataSize; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Cell cell = row.getCell(4);
            if (cell == null) {
                continue;
            }
            CellStyle style = styles.get(cell.getStringCellValue());
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    private CellStyle fillStyle(ExcelWriter writer, IndexedColors background,
                                IndexedColors fontColor) {
        CellStyle style = writer.getWorkbook().createCellStyle();
        style.setFillForegroundColor(background.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = writer.createFont();
        font.setColor(fontColor.getIndex());
        style.setFont(font);
        return style;
    }

    private void setColumnWidths(ExcelWriter writer) {
        writer.setColumnWidth(0, 22);
        writer.setColumnWidth(1, 14);
        writer.setColumnWidth(2, 16);
        writer.setColumnWidth(3, 12);
        writer.setColumnWidth(4, 12);
        writer.setColumnWidth(5, 22);
    }

    private void addStatusValidation(Sheet sheet) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint =
                helper.createExplicitListConstraint(STATUSES.toArray(new String[0]));
        // 第 5 列，第 2 到 201 行
        CellRangeAddressList range = new CellRangeAddressList(1, 200, 4, 4);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private List<Map<String, Object>> demoData() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("订单号", "SO20261021" + StrUtil.padPre(String.valueOf(i), 3, '0'));
            row.put("客户", "客户" + i);
            row.put("手机号", "1380000" + StrUtil.padPre(String.valueOf(1000 + i), 4, '0'));
            row.put("金额", new BigDecimal("999.00").add(new BigDecimal(i * 100L)));
            row.put("状态", STATUSES.get(i % 3));
            row.put("下单时间", DateUtil.format(
                    DateUtil.offsetDay(new Date(), -i), DatePattern.NORM_DATETIME_PATTERN));
            rows.add(row);
        }
        return rows;
    }
}
```

#### 步骤 4 · 写控制器

四个接口都是围绕文件流：导出、模板、导入、下载错误明细。

下载响应必须自己设 `Content-Type`，值是 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，Spring 不会自动识别。

文件名里有中文，直接拼进 `Content-Disposition` 会乱码或者被浏览器截断。标准写法是 `filename*=UTF-8''` 加 URL 编码，并且把编码出来的 `+` 换成 `%20`，因为编码器把空格编成 `+`，而 `+` 在 header 里不是空格。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/ExcelController.java`

```java
package top.mqxu.toolkit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.ImportResult;
import top.mqxu.toolkit.service.ExcelService;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 案例 5：Excel 导入导出
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/excel")
@RequiredArgsConstructor
public class ExcelController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelService excelService;

    /**
     * 导出订单报表
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return attachment(excelService.export(), "订单报表.xlsx");
    }

    /**
     * 下载导入模板，状态列带下拉框
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return attachment(excelService.template(), "订单导入模板.xlsx");
    }

    /**
     * 导入并校验，有错误时返回错误明细的下载地址
     */
    @PostMapping("/import")
    public Result<ImportResult> importFile(@RequestParam MultipartFile file) {
        return Result.ok(excelService.importFile(file));
    }

    /**
     * 下载错误明细
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        File file = excelService.errorFile(fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader("导入错误明细.xlsx"))
                .contentType(XLSX)
                .body(new FileSystemResource(file));
    }

    private ResponseEntity<byte[]> attachment(byte[] bytes, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(fileName))
                .contentType(XLSX)
                .body(bytes);
    }

    private String attachmentHeader(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}
```

### 7.4 接口实测

导出报表，文件 4.5KB，用 openpyxl 回读检查样式：

```bash
curl --noproxy '*' -o /tmp/orders.xlsx http://127.0.0.1:8015/toolkit/excel/export
ls -l /tmp/orders.xlsx
```

```text
export HTTP 200 size=4524
```

用 openpyxl 回读，能核对的部分都对：工作表名“订单”，`freeze_panes` 是 A2，金额列格式 `#,##0.00`，数据校验范围 E2:E201，下拉内容是“待支付,已支付,已取消”，列宽依次 22、14、16、12、12、22。表头和状态列的底色用的是 POI 的索引色，openpyxl 读不出 RGB 值，用 Excel 或 WPS 打开才能看到深蓝表头和白字。

下载导入模板：

```text
template HTTP 200 size=4036
```

把模板原样导入，一行示例数据全部通过：

```json
{"code":200,"data":{"total":1,"success":1,"failed":0,"errors":[],"errorFileUrl":null},"message":"操作成功"}
```

准备一份七行的问题数据，覆盖重复、空值、手机号、金额、状态、时间六种错误，导入结果：

```json
{"code":200,"data":{"total":7,"success":1,"failed":6,"errors":[
{"row":3,"orderNo":"SO20261021001","message":"订单号重复：SO20261021001"},
{"row":4,"orderNo":"SO20261021003","message":"客户不能为空"},
{"row":5,"orderNo":"SO20261021004","message":"手机号格式不正确：12345"},
{"row":6,"orderNo":"SO20261021005","message":"金额必须是大于 0 的数字：abc"},
{"row":7,"orderNo":"SO20261021006","message":"状态只能是待支付、已支付、已取消：已发货"},
{"row":8,"orderNo":"SO20261021007","message":"下单时间格式应为 yyyy-MM-dd HH:mm:ss：2026/10/21"}],
"errorFileUrl":"/toolkit/excel/download/import-error-4933db9e07e146b19604c027135da596.xlsx"},"message":"操作成功"}
```

第 1 行重复是因为和第 2 行订单号相同，错误记在真正重复的那一行上。行号和 Excel 里看到的一致，从 2 开始。

下载错误明细，三列：行号、订单号、原因，整行红字：

```bash
curl --noproxy '*' -o /tmp/errors.xlsx \
  "http://127.0.0.1:8015/toolkit/excel/download/import-error-4933db9e07e146b19604c027135da596.xlsx"
```

传一个不是 Excel 的文件：

```json
{"code":41031,"data":null,"message":"无法解析文件，请使用 .xlsx 格式：IOException: Can't open workbook - unsupported file type: UNKNOWN"}
```

尝试路径穿越：

```bash
curl --noproxy '*' -o /dev/null -w '%{http_code}\n' \
  "http://127.0.0.1:8015/toolkit/excel/download/..%2Fapplication.yml"
```

```text
404
```

### 7.5 容易踩的坑

1. 以为 Spring Boot 管了 POI 的版本。它不管，不写版本号会报 `dependencies.dependency.version is missing`。pom 里显式写了 5.5.1。
2. `ExcelReader` 不关。它持有输入流和临时文件，不关闭在 Windows 上会删不掉临时文件。用 try-with-resources。
3. 用字段名对表头。中文表头配英文属性名，必须加 `@Alias`，否则读出来全是 null，而且不报错，很难查。
4. 行号直接取列表下标。列表下标从 0 开始，Excel 第 1 行是表头，所以真实行号是下标加 2。少了这个偏移，用户永远找不到那一行。
5. 字段类型直接用 BigDecimal 和 Date。格式不合规时 `readAll` 整体抛异常，拿不到“哪一行错了”，用户体验很差。先按 String 接，再自己转换。
6. 导入时遇到空行报错。用户经常在末尾多敲几个回车，最后几行是空的。判断所有列都为空就跳过，别当错误。
7. 下载文件名中文乱码。`Content-Disposition` 里直接写中文，不同浏览器表现不一样。用 `filename*=UTF-8''` 加编码，并把 `+` 换成 `%20`。
8. 导出几万行还全放内存。`ExcelUtil.getWriter(true)` 是内存模式，十万行就会 OOM。大数据量改用 `BigExcelWriter` 或者流式写，分批 flush。

## 8. 案例 6：分片上传

### 8.1 需求

几百 MB 的文件一次传，网络一抖就前功尽弃，网关和 Nginx 也常有限制。做法是把文件切成若干片，一片一片传，全部传完再在服务端按顺序拼回去。

要求四件事：能初始化拿到上传标识和建议分片大小；能查已传和缺失的分片，实现断点续传；缺片时不允许合并；合并后能校验整个文件的 MD5，并把文件下载回来。

流程是固定的五步：

| 步骤 | 接口 | 说明 |
| --- | --- | --- |
| 1 | POST /toolkit/chunk/init | 传文件名、分片数、整文件 MD5，拿到 uploadId |
| 2 | POST /toolkit/chunk/upload | 带上 uploadId 和 index 逐片上传 |
| 3 | GET /toolkit/chunk/status | 查已传和缺失的分片 |
| 4 | POST /toolkit/chunk/merge | 全传完后合并，返回 fileId 和 MD5 |
| 5 | GET /toolkit/chunk/download/{fileId} | 下载合并后的文件 |

### 8.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/ChunkInitRequest.java` | 文件名、分片数、MD5 |
| 2 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/ChunkInitResponse.java` | uploadId、分片大小、已传分片 |
| 3 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/ChunkStatusResponse.java` | 已传与缺失 |
| 4 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/ChunkMergeResponse.java` | 文件标识、大小、MD5、下载地址 |
| 5 | `15-toolkit/src/main/java/top/mqxu/toolkit/support/ChunkSession.java` | 保存分片数与文件信息 |
| 6 | `15-toolkit/src/main/java/top/mqxu/toolkit/service/ChunkService.java` | 初始化、收片、查状态、合并、取文件 |
| 7 | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/ChunkController.java` | 五个接口 |


### 8.3 实现步骤

#### 步骤 1 · 定义初始化请求

`totalChunks` 用包装类型 Integer，这样前端不传时能拿到 null 并给出明确提示，用 int 的话不传会默认 0，错误信息会变成“分片数必须大于 0”，不够准确。`md5` 可选，传了就在合并时校验。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/ChunkInitRequest.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 初始化分片上传
 *
 * @param fileName    文件名
 * @param totalChunks 分片总数
 * @param md5         整个文件的 MD5，可选，传了会在合并时校验
 * @author mqxu
 * @date 2026-10-21
 */
public record ChunkInitRequest(String fileName, Integer totalChunks, String md5) {
}
```

#### 步骤 2 · 定义初始化结果

`uploaded` 在初始化时通常是空数组，但如果同一个文件之前传过一半，这里会把已传的序号带回来，前端就只传缺的。这个字段是断点续传的关键。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/ChunkInitResponse.java`

```java
package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 初始化结果
 *
 * @param uploadId    上传会话标识
 * @param chunkSize   建议分片大小（字节）
 * @param totalChunks 分片总数
 * @param uploaded    已上传的分片序号，用于断点续传
 * @author mqxu
 * @date 2026-10-21
 */
public record ChunkInitResponse(String uploadId, long chunkSize,
                                int totalChunks, List<Integer> uploaded) {
}
```

#### 步骤 3 · 定义分片状态

`missing` 由服务端算好，不让前端自己减。前端算容易和服务端的实际情况不一致，尤其是重复上传同一片的时候。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/ChunkStatusResponse.java`

```java
package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 分片状态
 *
 * @param uploadId    上传会话标识
 * @param totalChunks 分片总数
 * @param uploaded    已上传
 * @param missing     缺失
 * @author mqxu
 * @date 2026-10-21
 */
public record ChunkStatusResponse(String uploadId, int totalChunks,
                                  List<Integer> uploaded, List<Integer> missing) {
}
```

#### 步骤 4 · 定义合并结果

`md5` 是合并后实际算出来的，前端拿它和自己算的原文件 MD5 比对，一致就说明传输过程没坏。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/ChunkMergeResponse.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 合并结果
 *
 * @param fileId      文件标识
 * @param fileName    文件名
 * @param size        文件大小（字节）
 * @param md5         合并后计算的 MD5
 * @param downloadUrl 下载地址
 * @author mqxu
 * @date 2026-10-21
 */
public record ChunkMergeResponse(String fileId, String fileName, long size,
                                 String md5, String downloadUrl) {
}
```

#### 步骤 5 · 定义上传会话

会话里只存元信息，分片文件本身落盘，存在 `work-dir/chunks/{uploadId}/0.part` 这样的路径下。之所以不把分片内容放内存，是因为几 GB 的文件放内存直接 OOM。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/support/ChunkSession.java`

```java
package top.mqxu.toolkit.support;

import lombok.Getter;

/**
 * 分片上传会话
 *
 * @author mqxu
 * @date 2026-10-21
 */
@Getter
public class ChunkSession {

    private final String uploadId;
    private final String fileName;
    private final int totalChunks;
    private final long chunkSize;
    private final String md5;
    private final long createdAt = System.currentTimeMillis();

    public ChunkSession(String uploadId, String fileName, int totalChunks,
                        long chunkSize, String md5) {
        this.uploadId = uploadId;
        this.fileName = fileName;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.md5 = md5;
    }
}
```

#### 步骤 6 · 写分片服务

初始化时先做文件名清洗，`FileUtil.getName` 只留最后一段，再显式检查 `..`。文件名会参与拼路径，不处理就能被写成 `../../etc/passwd`。

每片落盘成 `{index}.part`，`uploadedIndexes` 直接扫目录，用文件名的主名解析成整数并排序。不额外维护一个已传列表，好处是重启后文件还在，状态就能恢复；坏处是目录 IO 稍慢，练习足够。

合并时按 0 到 total-1 顺序读，用 `BufferedOutputStream` 写，不能并行也不能乱序。合并完算 MD5，和会话里的比对，不一致就删掉合并结果并报错，避免把一个坏文件当成功返回。

成功后删掉分片目录、清掉会话，避免临时文件越积越多。

`file()` 用正则 `[a-f0-9]{32}` 校验 fileId，再按前缀找文件，防止有人传 `../` 来读别的文件。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/service/ChunkService.java`

```java
package top.mqxu.toolkit.service;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.ChunkInitRequest;
import top.mqxu.toolkit.dto.ChunkInitResponse;
import top.mqxu.toolkit.dto.ChunkMergeResponse;
import top.mqxu.toolkit.dto.ChunkStatusResponse;
import top.mqxu.toolkit.support.ChunkSession;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 分片上传
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 一片一片传，断点续传靠查询已传分片实现，合并后校验整个文件的 MD5
 */
@Service
@RequiredArgsConstructor
public class ChunkService {

    private final ToolkitProperties properties;

    private TimedCache<String, ChunkSession> cache;

    @PostConstruct
    void init() {
        cache = CacheUtil.newTimedCache(properties.getChunk().getExpireSeconds() * 1000);
        cache.schedulePrune(60_000);
        FileUtil.mkdir(new File(properties.getWorkDir(), "chunks"));
        FileUtil.mkdir(new File(properties.getWorkDir(), "uploads"));
    }

    @PreDestroy
    void destroy() {
        cache.cancelPruneSchedule();
        cache.clear();
    }

    public ChunkInitResponse init(ChunkInitRequest request) {
        String fileName = safeName(request.fileName());
        int total = request.totalChunks() == null ? 0 : request.totalChunks();
        if (total <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "totalChunks 必须大于 0");
        }

        String uploadId = IdUtil.fastSimpleUUID();
        FileUtil.mkdir(chunkDir(uploadId));

        ChunkSession session = new ChunkSession(uploadId, fileName, total,
                properties.getChunk().getChunkSize(), request.md5());
        cache.put(uploadId, session);

        return new ChunkInitResponse(uploadId, session.getChunkSize(), total,
                uploadedIndexes(session));
    }

    public ChunkStatusResponse upload(String uploadId, int index, MultipartFile file) {
        ChunkSession session = get(uploadId);
        if (index < 0 || index >= session.getTotalChunks()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "分片序号超出范围：0 到 " + (session.getTotalChunks() - 1));
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        File part = new File(chunkDir(uploadId), index + ".part");
        try {
            file.transferTo(part);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "分片写入失败：" + e.getMessage());
        }
        return status(uploadId);
    }

    public ChunkStatusResponse status(String uploadId) {
        ChunkSession session = get(uploadId);
        List<Integer> uploaded = uploadedIndexes(session);
        List<Integer> missing = IntStream.range(0, session.getTotalChunks())
                .filter(i -> !uploaded.contains(i))
                .boxed()
                .toList();
        return new ChunkStatusResponse(uploadId, session.getTotalChunks(), uploaded, missing);
    }

    public ChunkMergeResponse merge(String uploadId) {
        ChunkSession session = get(uploadId);
        List<Integer> uploaded = uploadedIndexes(session);
        if (uploaded.size() < session.getTotalChunks()) {
            throw new BusinessException(ErrorCode.CHUNK_NOT_COMPLETE,
                    "还缺 " + (session.getTotalChunks() - uploaded.size()) + " 片");
        }

        String fileId = IdUtil.fastSimpleUUID();
        File uploadDir = new File(properties.getWorkDir(), "uploads");
        File target = new File(uploadDir, fileId + "_" + session.getFileName());

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            for (int i = 0; i < session.getTotalChunks(); i++) {
                File part = new File(chunkDir(uploadId), i + ".part");
                FileUtil.writeToStream(part, out);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.CHUNK_MERGE_FAILED, e.getMessage());
        }

        String md5 = DigestUtil.md5Hex(target);
        if (StrUtil.isNotBlank(session.getMd5()) && !md5.equalsIgnoreCase(session.getMd5())) {
            FileUtil.del(target);
            throw new BusinessException(ErrorCode.CHUNK_MERGE_FAILED,
                    "MD5 校验失败，文件可能在传输中损坏");
        }

        FileUtil.del(chunkDir(uploadId));
        cache.remove(uploadId);

        return new ChunkMergeResponse(fileId, session.getFileName(), target.length(), md5,
                "/toolkit/chunk/download/" + fileId);
    }

    public File file(String fileId) {
        if (StrUtil.isBlank(fileId) || !fileId.matches("[a-f0-9]{32}")) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        File dir = new File(properties.getWorkDir(), "uploads");
        File[] matches = dir.listFiles((d, name) -> name.startsWith(fileId + "_"));
        if (matches == null || matches.length == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return matches[0];
    }

    private ChunkSession get(String uploadId) {
        ChunkSession session = StrUtil.isBlank(uploadId) ? null : cache.get(uploadId, false);
        if (session == null) {
            throw new BusinessException(ErrorCode.CHUNK_SESSION_NOT_FOUND);
        }
        return session;
    }

    private List<Integer> uploadedIndexes(ChunkSession session) {
        File[] parts = chunkDir(session.getUploadId())
                .listFiles((d, name) -> name.endsWith(".part"));
        if (parts == null || parts.length == 0) {
            return List.of();
        }
        return Arrays.stream(parts)
                .map(FileUtil::mainName)
                .map(Integer::parseInt)
                .sorted()
                .toList();
    }

    private File chunkDir(String uploadId) {
        return new File(properties.getWorkDir(), "chunks/" + uploadId);
    }

    /**
     * 只取文件名，挡掉 ../ 和绝对路径
     */
    private String safeName(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "fileName 不能为空");
        }
        // 只留文件名本身，去掉浏览器或攻击者塞进来的路径部分
        String name = FileUtil.getName(fileName);
        if (StrUtil.isBlank(name) || name.contains("..")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不合法");
        }
        return name;
    }
}
```

#### 步骤 7 · 写控制器

上传接口用 `@RequestParam` 接 `uploadId`、`index`、`file` 三个表单字段，`index` 是 int，Spring 会自动转换，传 `abc` 会走类型不匹配的异常处理，返回 40000。

下载时文件名从磁盘文件名里取，格式是 `{fileId}_{原文件名}`，用 `StrUtil.subAfter` 切出下划线后面的部分，再做 UTF-8 编码。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/ChunkController.java`

```java
package top.mqxu.toolkit.controller;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.ChunkInitRequest;
import top.mqxu.toolkit.dto.ChunkInitResponse;
import top.mqxu.toolkit.dto.ChunkMergeResponse;
import top.mqxu.toolkit.dto.ChunkStatusResponse;
import top.mqxu.toolkit.service.ChunkService;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 案例 6：分片上传
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/chunk")
@RequiredArgsConstructor
public class ChunkController {

    private final ChunkService chunkService;

    @PostMapping("/init")
    public Result<ChunkInitResponse> init(@RequestBody ChunkInitRequest request) {
        return Result.ok(chunkService.init(request));
    }

    @PostMapping("/upload")
    public Result<ChunkStatusResponse> upload(@RequestParam String uploadId,
                                              @RequestParam int index,
                                              @RequestParam MultipartFile file) {
        return Result.ok(chunkService.upload(uploadId, index, file));
    }

    @GetMapping("/status")
    public Result<ChunkStatusResponse> status(@RequestParam String uploadId) {
        return Result.ok(chunkService.status(uploadId));
    }

    @PostMapping("/merge")
    public Result<ChunkMergeResponse> merge(@RequestParam String uploadId) {
        return Result.ok("合并成功", chunkService.merge(uploadId));
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId) {
        File file = chunkService.file(fileId);
        String fileName = StrUtil.subAfter(file.getName(), "_", false);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}
```

### 8.4 接口实测

先造一个 2MB 多 4 个字节的文件，切成三片（1MB、1MB、4 字节），记下整文件 MD5。下面这次运行的结果，因为内容是随机的，MD5 每次都不一样，你自己跑出来会是另一个值：

```bash
dd if=/dev/urandom of=/tmp/big.bin bs=1024 count=2048
printf 'ABCD' >> /tmp/big.bin
ls -l /tmp/big.bin
md5 -q /tmp/big.bin
split -b 1048576 /tmp/big.bin /tmp/part-
```

```text
源文件字节数 2097156
源文件 md5=2ee45b824acf3e0e28a2e3a79e8d5a12
```

初始化，拿到 uploadId，分片数 3，已传为空：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/chunk/init \
  -H 'Content-Type: application/json' \
  -d '{"fileName":"big.bin","totalChunks":3,"md5":"2ee45b824acf3e0e28a2e3a79e8d5a12"}'
```

```json
{"code":200,"data":{"uploadId":"e32c1f52354543b98101cb73eead678c","chunkSize":1048576,"totalChunks":3,"uploaded":[]},"message":"操作成功"}
```

故意先传第 0 片和第 2 片，跳过第 1 片，每传一片都会返回当前进度：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/chunk/upload \
  -F "uploadId=e32c1f52354543b98101cb73eead678c" -F "index=0" -F "file=@/tmp/part-aa"
```

```json
{"code":200,"data":{"uploadId":"e32c1f52354543b98101cb73eead678c","totalChunks":3,"uploaded":[0],"missing":[1,2]},"message":"操作成功"}
{"code":200,"data":{"uploadId":"e32c1f52354543b98101cb73eead678c","totalChunks":3,"uploaded":[0,2],"missing":[1]},"message":"操作成功"}
```

缺一片就合并，被拒绝并告知缺几片：

```json
{"code":41042,"data":null,"message":"还缺 1 片"}
```

补上第 1 片，三片齐了：

```json
{"code":200,"data":{"uploadId":"e32c1f52354543b98101cb73eead678c","totalChunks":3,"uploaded":[0,1,2],"missing":[]},"message":"操作成功"}
```

合并，返回的 MD5 和源文件一致：

```json
{"code":200,"data":{"fileId":"54a8fd53835b49f69e269d3641bf5dac","fileName":"big.bin","size":2097156,"md5":"2ee45b824acf3e0e28a2e3a79e8d5a12","downloadUrl":"/toolkit/chunk/download/54a8fd53835b49f69e269d3641bf5dac"},"message":"合并成功"}
```

下载回来逐字节比对：

```bash
curl --noproxy '*' -o /tmp/merged.bin \
  http://127.0.0.1:8015/toolkit/chunk/download/54a8fd53835b49f69e269d3641bf5dac
cmp /tmp/big.bin /tmp/merged.bin && echo "字节完全一致"
```

```text
download HTTP 200 size=2097156
字节完全一致
```

用已合并掉的 uploadId 再传片，会话已经被清掉了：

```json
{"code":41041,"data":null,"message":"分片会话不存在或已过期"}
```

### 8.5 容易踩的坑

1. 分片序号从 1 开始。代码里 index 是 0 到 total-1，前端从 1 开始传会全部越界，最后一片永远传不进来。
2. 合并顺序错。按文件系统列目录的顺序拼，拼出来的文件 MD5 一定不对，而且大小是对的，不校验根本发现不了。必须按 0 到 total-1 显式排序。
3. 不校验 MD5。网络抖动、分片覆盖写都会导致内容损坏，只有大小对。合并后算一次 MD5，和前端给的比对，不一致就删文件报错。
4. 分片直接放内存。每个请求存一个 byte[]，几十个人同时传大文件就 OOM。落盘，用 `transferTo` 让容器直接写文件。
5. 文件名不校验。`fileName` 参与拼路径，传 `../../application.yml` 就能覆盖配置。先取 basename，再拒绝含 `..` 的名字，两步都要做。
6. 合并完不清理。分片目录和省话都不删，跑几天磁盘满。合并成功后删目录、清会话，失败时也要保证不留下半个文件。
7. Nginx 和 Spring 的上传限制没调。默认 `max-file-size` 是 1MB，分片大小也是 1MB 时刚好卡在边界上，偶尔失败。分片大小要明显小于限制，配置里两个都调到 20MB 和 50MB。
8. 以为分片上传就是断点续传。断点续传还要前端记录 uploadId 和已传序号，刷新页面后先调 status 查进度，只补缺失的片。

## 9. 案例 7：上传安全

### 9.1 需求

用户上传的文件不能信。改了扩展名的脚本、伪装成图片的 PDF，都是常见套路。要按文件头判断真实类型，而不是看后缀。

另外两个配套功能：上传的图片太大要能压缩，多个文件要能打成一个 zip 下载。

### 9.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/FileCheckResult.java` | 扩展名、真实类型、是否放行 |
| 2 | `15-toolkit/src/main/java/top/mqxu/toolkit/service/FileService.java` | 类型识别、压缩、打包 |
| 3 | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/FileController.java` | 三个接口 |


### 9.3 实现步骤

#### 步骤 1 · 定义检查结果

把扩展名、文件头识别出来的类型、是否允许、判定理由都返回给前端，排查问题时不用去翻日志。`md5` 顺带算出来，重复文件可以直接秒传。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/FileCheckResult.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 文件检查结果
 *
 * @param originalName 原始文件名
 * @param extension    扩展名
 * @param realType     真实类型（按文件头识别）
 * @param size         字节数
 * @param md5          MD5
 * @param allowed      是否在白名单内
 * @param remark       扩展名与真实类型是否一致
 * @author mqxu
 * @date 2026-10-21
 */
public record FileCheckResult(String originalName, String extension, String realType,
                              long size, String md5, boolean allowed, String remark) {
}
```

#### 步骤 2 · 写文件服务

`FileTypeUtil.getType(InputStream)` 只看文件头的前几个字节，返回 `png`、`pdf`、`zip` 这类类型名。它不是看后缀，所以把 shell 脚本改名成 png，返回的是空字符串。

判定放行要同时满足两个条件：真实类型在白名单里，并且扩展名和真实类型一致。只看真实类型不够，因为 `sh` 脚本的真实类型识别不出来，直接放行反而危险；只看扩展名更不行，改个名字就绕过了。

`sameType` 里单独处理了 jpg 和 jpeg，它们是同一个格式，不处理会把正常的 `.jpeg` 挡掉。

压缩用 `Img.from(...)` 读图，宽度超过 `maxWidth` 才缩放，没超过就原样输出。缩放后统一转 PNG，透明通道不会丢。

打包时先建一个临时目录，把每个文件 `transferTo` 进去，再用 `ZipUtil.zip(zip, false, list)` 打成一个包。`false` 表示不要把目录本身打进去。最后在 `finally` 里删临时目录和 zip 文件，用完就清。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/service/FileService.java`

```java
package top.mqxu.toolkit.service;

import cn.hutool.core.img.Img;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.FileCheckResult;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * 文件上传安全
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 按文件头识别真实类型，挡住改扩展名的做法；图片压缩与多文件打包
 */
@Service
@RequiredArgsConstructor
public class FileService {

    private static final Set<String> ALLOWED =
            Set.of("jpg", "jpeg", "png", "gif", "pdf", "zip", "docx", "xlsx");

    private final ToolkitProperties properties;

    public FileCheckResult check(MultipartFile file) {
        byte[] bytes = readBytes(file);
        String originalName = StrUtil.nullToEmpty(file.getOriginalFilename());
        String extension = StrUtil.nullToEmpty(FileUtil.extName(originalName)).toLowerCase();
        String realType = StrUtil.nullToEmpty(FileTypeUtil.getType(new ByteArrayInputStream(bytes)));

        // 白名单只看真实类型还不够，扩展名和文件头对不上的一律拒绝
        boolean allowed = ALLOWED.contains(realType.toLowerCase()) && sameType(extension, realType);

        String remark;
        if (StrUtil.isBlank(realType)) {
            remark = "扩展名是 " + extension + "，无法识别文件类型";
        } else if (sameType(extension, realType)) {
            remark = "扩展名与文件头一致";
        } else {
            remark = "扩展名是 " + extension + "，文件头实际是 " + realType;
        }

        return new FileCheckResult(originalName, extension, realType,
                bytes.length, DigestUtil.md5Hex(bytes), allowed, remark);
    }

    /**
     * 按最大宽度等比缩小，宽度没超就原样返回
     */
    public byte[] compress(MultipartFile file, int maxWidth) {
        if (maxWidth <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "maxWidth 必须大于 0");
        }
        try {
            Img img = Img.from(file.getInputStream());
            int width = img.getImg().getWidth(null);
            int height = img.getImg().getHeight(null);
            if (width > maxWidth) {
                int targetHeight = (int) Math.round((double) height * maxWidth / width);
                img.scale(maxWidth, targetHeight);
            }
            return ImgUtil.toBytes(img.getImg(), "png");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片处理失败：" + e.getMessage());
        }
    }

    /**
     * 多个文件打成一个 zip
     */
    public byte[] zip(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new BusinessException(ErrorCode.FILE_EMPTY, "请至少上传一个文件");
        }
        File dir = FileUtil.mkdir(new File(properties.getWorkDir(),
                "zip/" + IdUtil.fastSimpleUUID()));
        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                File dest = new File(dir, safeName(file.getOriginalFilename()));
                file.transferTo(dest);
            }
            File[] list = dir.listFiles();
            if (list == null || list.length == 0) {
                throw new BusinessException(ErrorCode.FILE_EMPTY);
            }
            File zip = new File(dir.getParentFile(), dir.getName() + ".zip");
            ZipUtil.zip(zip, false, list);
            return FileUtil.readBytes(zip);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "打包失败：" + e.getMessage());
        } finally {
            FileUtil.del(dir);
            FileUtil.del(new File(dir.getParentFile(), dir.getName() + ".zip"));
        }
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件读取失败");
        }
    }

    private boolean sameType(String extension, String realType) {
        if (extension.equals(realType)) {
            return true;
        }
        // jpg 与 jpeg 是同一个格式
        return List.of("jpg", "jpeg").contains(extension)
                && List.of("jpg", "jpeg").contains(realType);
    }

    private String safeName(String fileName) {
        // FileUtil.getName 会把 / 和 \ 两种分隔符都处理掉，比手写 subAfter 稳妥
        String name = FileUtil.getName(StrUtil.nullToEmpty(fileName));
        if (StrUtil.isBlank(name) || name.contains("..")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不合法");
        }
        return name;
    }
}
```

#### 步骤 3 · 写控制器

`/check` 返回 JSON；`/compress` 返回 PNG；`/zip` 返回 `application/zip` 并把 `Content-Disposition` 设成 attachment，浏览器点开就是下载。

`files` 参数用数组接多个文件，`@RequestParam MultipartFile[] files`，前端表单里同名字段传多次即可。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/FileController.java`

```java
package top.mqxu.toolkit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.FileCheckResult;
import top.mqxu.toolkit.service.FileService;

/**
 * 案例 7：上传安全
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 检查真实文件类型，改扩展名的文件会在这里露出来
     */
    @PostMapping("/check")
    public Result<FileCheckResult> check(@RequestParam MultipartFile file) {
        return Result.ok(fileService.check(file));
    }

    /**
     * 图片等比压缩，返回处理后的图片
     */
    @PostMapping(value = "/compress", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> compress(
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "800") int maxWidth) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(fileService.compress(file, maxWidth));
    }

    /**
     * 多个文件打包下载
     */
    @PostMapping(value = "/zip", produces = "application/zip")
    public ResponseEntity<byte[]> zip(@RequestParam MultipartFile[] files) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header("Content-Disposition", "attachment; filename=bundle.zip")
                .body(fileService.zip(files));
    }
}
```

### 9.4 接口实测

先写一个 shell 脚本，改名成 fake.png，再上传：

```bash
printf '#!/bin/sh\necho hi\n' > /tmp/fake.png
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/file/check -F "file=@/tmp/fake.png"
```

```json
{"code":200,"data":{"originalName":"fake.png","extension":"png","realType":"","size":20,"md5":"5da44a019df9b7353418668568dbb9b2","allowed":false,"remark":"扩展名是 png，无法识别文件类型"},"message":"操作成功"}
```

上传一张真图片，扩展名和文件头一致：

```json
{"code":200,"data":{"originalName":"poster.png","extension":"png","realType":"png","size":20527,"md5":"195d1817a9a3c8dba0bf09053b2009f1","allowed":true,"remark":"扩展名与文件头一致"},"message":"操作成功"}
```

再试一个 PDF 改名成 jpg，会被识破：

```json
{"code":200,"data":{"originalName":"disguise.jpg","extension":"jpg","realType":"pdf","size":34,"md5":"51d25b602134730d9c956dc6f785a12a","allowed":false,"remark":"扩展名是 jpg，文件头实际是 pdf"},"message":"操作成功"}
```

压缩：宽度 600 的图压到 300，输出还是 PNG，尺寸减半：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/file/compress \
  -F "file=@/tmp/poster.png" -F "maxWidth=300" -o /tmp/small.png
file /tmp/small.png
```

```text
compress HTTP 200 size=7968
/tmp/small.png: PNG image data, 300 x 400, 8-bit/color RGBA, non-interlaced
```

两个文件打包，解压后目录结构是平的：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/file/zip \
  -F "files=@/tmp/poster.png" -F "files=@/tmp/orders.xlsx" -o /tmp/bundle.zip
unzip -l /tmp/bundle.zip
```

```text
zip HTTP 200 size=20367
    20527  10-21-2026 10:56   poster.png
     4524  10-21-2026 10:56   orders.xlsx
    25051                     2 files
```

### 9.5 容易踩的坑

1. 只校验扩展名。改个后缀就进来了，等于没有校验。
2. 只校验真实类型。识别不出来的类型（脚本、纯文本）会返回空字符串，白名单里没有空串，看起来是挡住了；但如果白名单写成“不在黑名单里就放行”，这些文件反而全部通过。白名单加扩展名一致，两个条件一起判。
3. 把 jpg 和 jpeg 当成两种格式。用户传 `a.jpeg` 会被挡掉，报“扩展名与文件头不一致”，非常冤。
4. `FileTypeUtil` 当真了安全防线。它只读文件头，精心构造的文件可以让头部像 PNG、内容是可执行的东西。真正的上传目录必须禁止执行，文件放对象存储，用随机名，不要用原文件名落盘。
5. 压缩用 `Img.scale` 后没管格式。原图是 jpg 带透明通道，转出来的图背景变黑。统一转 PNG，或者先铺一层白底。
6. zip 临时目录没清。每次打包留一个目录，几天就堆满。用 finally 删掉，异常也要删。
7. 打包时把用户文件名直接当路径。`../../x` 会在临时目录外面写文件。取 basename 并拒绝 `..`。

## 10. 案例 8：开放接口签名

### 10.1 需求

给第三方调用的接口不能只靠 token。要防止请求被篡改、被重放、被无限期使用。常见做法是 HMAC 签名加时间窗加 nonce。

规则定死四条：请求头带 appId、timestamp、nonce、sign；签名内容是 appId、timestamp、nonce、body 四段用换行拼起来；用 app-secret 做 HMAC-SHA256；服务端校验时间窗、签名、nonce 是否用过。

签名串的样子是这样，四行，顺序不能变：

```text
toolkit-demo
1792551406
3en33rNk33D7
{"orderNo":"SO1"}
```

为了让 curl 能直接测，额外提供一个 `/sign/demo` 接口，按同样的规则算一个签名返回，复制过去就能用。

### 10.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/SignResponse.java` | 规范串与签名值 |
| 2 | `15-toolkit/src/main/java/top/mqxu/toolkit/service/SignService.java` | 生成签名、校验签名、记 nonce |
| 3 | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/SignController.java` | 生成签名与受保护接口 |


### 10.3 实现步骤

#### 步骤 1 · 定义签名结果

`canonical` 是参与签名的那串文本，一起返回是为了让人能肉眼比对：签名不对时，先看规范串是不是和预期一致，能排除一大半问题。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/SignResponse.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 签名示例
 *
 * @param appId     应用标识
 * @param timestamp 秒级时间戳
 * @param nonce     随机串
 * @param body      请求体原文
 * @param canonical 参与签名的字符串
 * @param sign      签名结果
 * @author mqxu
 * @date 2026-10-21
 */
public record SignResponse(String appId, String timestamp, String nonce,
                           String body, String canonical, String sign) {
}
```

#### 步骤 2 · 写签名服务

校验顺序很重要：先查必填，再查 appId，再查时间戳，最后才验签。顺序反了会把“参数没传”报成“签名不对”，排查时容易被带偏。

时间窗用 `Math.abs(now - ts)`，同时挡住太久以前和太久以后的请求。只挡过去不挡未来，攻击者拿一个未来时间戳就能长期重放。

签名比对用 `MessageDigest.isEqual`，它是常量时间比较。用 `equals` 的话，比较过程在第一个不同字符处返回，理论上可以通过响应时间一点点猜出签名。实践中难度很高，但成本几乎为零，没有理由不用。

nonce 缓存的有效期设成时间窗的两倍。太短的话，一个还在时间窗内的请求重放时 nonce 已经过期，挡不住；太长只是多占一点内存。

nonce 的键是 `appId + ":" + nonce`，不加 appId 的话，两个应用碰巧用了同一个 nonce 会互相干扰，A 应用把 B 应用的请求顶掉。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/service/SignService.java`

```java
package top.mqxu.toolkit.service;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.HmacAlgorithm;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.SignResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 开放接口签名
 *
 * @author mqxu
 * @date 2026-10-21
 * @description appId + 时间戳 + nonce + body 拼成规范串，用 HMAC-SHA256 签名，
 * 服务端校验时间窗、签名、nonce 是否用过
 */
@Service
@RequiredArgsConstructor
public class SignService {

    private final ToolkitProperties properties;

    /**
     * 已用过的 nonce，有效期取时间窗的两倍
     */
    private TimedCache<String, Boolean> nonceCache;

    @PostConstruct
    void init() {
        nonceCache = CacheUtil.newTimedCache(
                properties.getSign().getToleranceSeconds() * 2000);
        nonceCache.schedulePrune(60_000);
    }

    @PreDestroy
    void destroy() {
        nonceCache.cancelPruneSchedule();
        nonceCache.clear();
    }

    /**
     * 生成签名，供调用方参考，也方便 curl 直接测
     */
    public SignResponse demo(String appId, Long timestamp, String nonce, String body) {
        String realAppId = StrUtil.blankToDefault(appId, properties.getSign().getAppId());
        long ts = timestamp == null ? System.currentTimeMillis() / 1000 : timestamp;
        String realNonce = StrUtil.blankToDefault(nonce, RandomUtil.randomString(12));
        String realBody = StrUtil.nullToEmpty(body);

        String canonical = canonical(realAppId, String.valueOf(ts), realNonce, realBody);
        String sign = SecureUtil.hmac(HmacAlgorithm.HmacSHA256,
                        properties.getSign().getAppSecret())
                .digestHex(canonical);

        return new SignResponse(realAppId, String.valueOf(ts), realNonce,
                realBody, canonical, sign);
    }

    /**
     * 校验签名，通过后记录 nonce，重复提交会被拒绝
     */
    public void verify(String appId, String timestamp, String nonce, String sign, String body) {
        if (StrUtil.hasBlank(appId, timestamp, nonce, sign)) {
            throw new BusinessException(ErrorCode.SIGN_MISSING);
        }
        if (!properties.getSign().getAppId().equals(appId)) {
            throw new BusinessException(ErrorCode.SIGN_INVALID, "appId 不存在");
        }

        long ts = Convert.toLong(timestamp, 0L);
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - ts) > properties.getSign().getToleranceSeconds()) {
            throw new BusinessException(ErrorCode.SIGN_EXPIRED,
                    "请求时间与服务器相差超过 " + properties.getSign().getToleranceSeconds() + " 秒");
        }

        String expected = SecureUtil.hmac(HmacAlgorithm.HmacSHA256,
                        properties.getSign().getAppSecret())
                .digestHex(canonical(appId, timestamp, nonce, StrUtil.nullToEmpty(body)));

        // 常量时间比较，避免通过响应时间猜签名
        boolean matched = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sign.getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            throw new BusinessException(ErrorCode.SIGN_INVALID);
        }

        String nonceKey = appId + ":" + nonce;
        if (nonceCache.containsKey(nonceKey)) {
            throw new BusinessException(ErrorCode.SIGN_REPLAY);
        }
        nonceCache.put(nonceKey, Boolean.TRUE);
    }

    private String canonical(String appId, String timestamp, String nonce, String body) {
        return appId + "\n" + timestamp + "\n" + nonce + "\n" + body;
    }
}
```

#### 步骤 3 · 写控制器

受保护接口用 `@RequestHeader` 逐个取四个头，`required = false`，缺了不报 400 而是进到 Service 报 41051，错误码更准确。

body 用 `String` 接，不用对象。签名签的是原始字节，如果先反序列化成对象再重新序列化，空格、字段顺序、null 字段的处理都可能变，规范串就和客户端算的不一样了。

`/demo` 的请求体是可选的，`@RequestBody(required = false)` 加判空，这样直接 POST 空 body 也能拿到一组默认参数算出的签名。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/SignController.java`

```java
package top.mqxu.toolkit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.SignResponse;
import top.mqxu.toolkit.service.SignService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 案例 8：开放接口签名
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/sign")
@RequiredArgsConstructor
public class SignController {

    private final SignService signService;

    /**
     * 生成一个可用的签名，方便直接复制到下面那个接口
     */
    @PostMapping("/demo")
    public Result<SignResponse> demo(@RequestBody(required = false) DemoRequest request) {
        DemoRequest req = request == null ? new DemoRequest(null, null, null, null) : request;
        return Result.ok(signService.demo(req.appId(), req.timestamp(), req.nonce(), req.body()));
    }

    /**
     * 受签名保护的接口
     */
    @PostMapping("/order")
    public Result<Map<String, Object>> order(
            @RequestHeader(value = "X-App-Id", required = false) String appId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Sign", required = false) String sign,
            @RequestBody(required = false) String body) {

        signService.verify(appId, timestamp, nonce, sign, body);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accepted", true);
        data.put("appId", appId);
        data.put("requestBody", body);
        return Result.ok("签名校验通过", data);
    }

    public record DemoRequest(String appId, Long timestamp, String nonce, String body) {
    }
}
```

### 10.4 接口实测

先生成一个签名，响应里带着规范串：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/sign/demo \
  -H 'Content-Type: application/json' \
  -d '{"body":"{\"orderNo\":\"SO1\"}"}'
```

```json
{"code":200,"data":{"appId":"toolkit-demo","timestamp":"1792551406","nonce":"3en33rNk33D7",
"body":"{\"orderNo\":\"SO1\"}",
"canonical":"toolkit-demo\n1792551406\n3en33rNk33D7\n{\"orderNo\":\"SO1\"}",
"sign":"3f5b32909ba0db8839280da4f63749038c3f53277782095d5672b4819a778fbf"},"message":"操作成功"}
```

拿这组参数调受保护接口，通过：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/sign/order \
  -H 'Content-Type: application/json' \
  -H 'X-App-Id: toolkit-demo' \
  -H 'X-Timestamp: 1792551406' \
  -H 'X-Nonce: 3en33rNk33D7' \
  -H 'X-Sign: 3f5b32909ba0db8839280da4f63749038c3f53277782095d5672b4819a778fbf' \
  -d '{"orderNo":"SO1"}'
```

```json
{"code":200,"data":{"accepted":true,"appId":"toolkit-demo","requestBody":"{\"orderNo\":\"SO1\"}"},"message":"签名校验通过"}
```

原样再发一次，nonce 已被记录，判定为重放：

```json
{"code":41054,"data":null,"message":"重复请求"}
```

换个 nonce 重新签名，但把 body 改成别的，签名对不上：

```json
{"code":41052,"data":null,"message":"签名不正确"}
```

一个头都不带：

```json
{"code":41051,"data":null,"message":"缺少签名参数"}
```

用一小时前的时间戳，即使签名正确也被拒：

```json
{"code":41053,"data":null,"message":"请求时间与服务器相差超过 300 秒"}
```

appId 对不上：

```json
{"code":41052,"data":null,"message":"appId 不存在"}
```

### 10.5 容易踩的坑

1. body 用对象接再重新序列化。JSON 序列化不保证字段顺序，也不保证空格一致，客户端算的规范串和服务端算的必然不同。要签原始字符串。
2. 签名大小写不一致。HMAC-SHA256 的十六进制输出有小写也有大写实现，客户端用大写、服务端用小写，比对就失败。约定清楚，或者比对前统一转小写。
3. 时间戳用毫秒。约定的是秒，客户端传了 13 位，`Math.abs(now - ts)` 永远大于时间窗，全部报过期。接口文档要写清单位。
4. 只校验时间窗不校验 nonce。攻击者抓到一个请求，在时间窗内可以重放无数次。nonce 必须落缓存或 Redis。
5. nonce 缓存 TTL 比时间窗短。时间窗 5 分钟、缓存 1 分钟，剩下的 4 分钟里重放照样成功。TTL 至少是时间窗的两倍。
6. app-secret 写在代码或 yml 里提交到仓库。练习工程可以，线上必须走配置中心或者密钥管理服务，并且支持轮换。
7. 签名算法自己发明。比如把参数排序后拼成 `k=v&k=v`，看着能用，但 URL 编码、空值、数组的处理很容易不一致。用标准做法，或者直接用现成的开放平台签名规范。
8. 把校验写在拦截器里但只拦部分路径。漏掉一个新加的接口，等于给了一个无签名的后门。受保护接口统一前缀，拦截器按前缀匹配。

## 11. 案例 9：敏感数据处理

### 11.1 需求

客户表里有手机号、身份证号、密码。三类数据三种处理方式，不能混：手机号和身份证要能还原，所以加密存储；密码永远不需要还原，只能哈希；查询返回时按角色决定给明文还是打码。

四个接口：创建客户、按角色查客户、查看存储层的原始内容（教学用）、校验密码。

先把三种方式的区别摆清楚，后面写代码才不会乱：

| 处理方式 | 能不能还原 | 用什么 | 用在哪 |
| --- | --- | --- | --- |
| 对称加密 | 能 | AES-128 | 手机号、身份证，业务需要回显和计算 |
| 哈希 | 不能 | BCrypt | 密码，只做校验 |
| 脱敏 | 能（原文还在） | DesensitizedUtil | 出站展示，按角色决定打几颗星 |

### 11.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | `15-toolkit/src/main/java/top/mqxu/toolkit/common/ErrorCode.java（新增一行）` | 密码错误 |
| 2 | `15-toolkit/src/main/java/top/mqxu/toolkit/support/Customer.java` | 密文字段与密码哈希 |
| 3 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/CreateCustomerRequest.java` | 接收明文 |
| 4 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/CustomerView.java` | 按角色脱敏后的结果 |
| 5 | `15-toolkit/src/main/java/top/mqxu/toolkit/service/SensitiveDataService.java` | 加密、哈希、脱敏、校验 |
| 6 | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/SensitiveDataController.java` | 创建、查询、看存储层、校验密码 |


### 11.3 实现步骤

#### 步骤 1 · 补一个错误码

密码错误单独一个码，不要复用 40000。前端要根据这个码把焦点定位到密码框，而不是弹一个“参数不合法”。

完整文件在第 2 章步骤 6 已经给过，这里只是多了最后一行。

```java
// 案例 9：敏感数据
CUSTOMER_NOT_FOUND(41061, "客户不存在"),
PASSWORD_WRONG(41062, "密码错误");
```

#### 步骤 2 · 定义存储对象

字段名带 `Encrypted` 和 `Hash` 后缀，是刻意的。看到 `phoneEncrypted` 就知道存的是密文，不会有人误以为是明文直接塞到响应里。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/support/Customer.java`

```java
package top.mqxu.toolkit.support;

import lombok.Data;

/**
 * 客户存储对象
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 手机号、身份证以密文保存，密码只保存 BCrypt 哈希
 */
@Data
public class Customer {

    private String id;
    private String name;
    private String phoneEncrypted;
    private String idCardEncrypted;
    private String passwordHash;
    private String level;
    private long createdAt;
}
```

#### 步骤 3 · 定义创建请求

请求里是明文，这是唯一一次明文出现在内存里。Service 加密后就不再保留明文，也不写日志。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/CreateCustomerRequest.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 创建客户
 *
 * @param name     姓名
 * @param phone    手机号
 * @param idCard   身份证号
 * @param password 明文密码
 * @param level    等级，normal / vip
 * @author mqxu
 * @date 2026-10-21
 */
public record CreateCustomerRequest(String name, String phone, String idCard,
                                    String password, String level) {
}
```

#### 步骤 4 · 定义对外视图

对外对象和存储对象分开，是这个案例最重要的设计。如果直接把 Customer 返回出去，密文会暴露，哪天有人加了字段也可能顺带把密码哈希带出去。

`age` 和 `gender` 是从身份证算出来的派生字段，不需要单独存。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/CustomerView.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 客户视图
 *
 * @param id     客户 ID
 * @param name   姓名
 * @param phone  手机号，按角色脱敏
 * @param idCard 身份证，按角色脱敏
 * @param age    年龄，由身份证算出
 * @param gender 性别，由身份证算出
 * @param level  等级
 * @author mqxu
 * @date 2026-10-21
 */
public record CustomerView(String id, String name, String phone, String idCard,
                           int age, String gender, String level) {
}
```

#### 步骤 5 · 写敏感数据服务

`@PostConstruct` 里检查密钥长度。AES 的密钥只能 16、24、32 字节，对应 128、192、256 位，长度不对在第一次加密时才抛异常，启动时检查能提前暴露配置问题。

加密用 `aes.encryptBase64(...)`，输出是 Base64 字符串，方便存字符串字段，不会因为二进制内容弄坏数据库列。

密码用 `BCrypt.hashpw`。BCrypt 每次加盐，同一个密码两次哈希结果不同，这是正常现象，所以校验只能 `checkpw`，不能拿哈希值比对。

脱敏放在 `toView` 里，不在存储层做。存储层做脱敏就再也还原不回来了，业务需要完整号码时（比如发短信）就没办法。

`IdcardUtil.getAgeByIdCard` 和 `getGenderByIdCard` 直接算年龄性别，不用自己切字符串。18 位身份证第 17 位奇数是男、偶数是女，工具类已经处理了。

`raw()` 只给教学演示用，真实项目绝对不能开这种接口。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/service/SensitiveDataService.java`

```java
package top.mqxu.toolkit.service;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.IdcardUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.symmetric.AES;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.CreateCustomerRequest;
import top.mqxu.toolkit.dto.CustomerView;
import top.mqxu.toolkit.support.Customer;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 敏感数据处理
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 手机号、身份证 AES 加密存储，密码 BCrypt 哈希，查询时按角色脱敏
 */
@Service
@RequiredArgsConstructor
public class SensitiveDataService {

    private final ToolkitProperties properties;

    private final Map<String, Customer> store = new ConcurrentHashMap<>();

    private AES aes;

    @PostConstruct
    void init() {
        String key = properties.getCrypto().getAesKey();
        if (StrUtil.isBlank(key) || key.getBytes(StandardCharsets.UTF_8).length != 16) {
            throw new IllegalStateException("toolkit.crypto.aes-key 必须是 16 位字符串");
        }
        aes = SecureUtil.aes(key.getBytes(StandardCharsets.UTF_8));
    }

    public CustomerView create(CreateCustomerRequest request) {
        if (StrUtil.isBlank(request.name())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "姓名不能为空");
        }
        if (!Validator.isMobile(request.phone())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
        if (!Validator.isCitizenId(request.idCard())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "身份证号不正确");
        }
        if (StrUtil.length(request.password()) < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码至少 6 位");
        }

        Customer customer = new Customer();
        customer.setId(IdUtil.fastSimpleUUID());
        customer.setName(request.name());
        customer.setPhoneEncrypted(aes.encryptBase64(request.phone()));
        customer.setIdCardEncrypted(aes.encryptBase64(request.idCard()));
        customer.setPasswordHash(BCrypt.hashpw(request.password()));
        customer.setLevel(StrUtil.blankToDefault(request.level(), "normal"));
        customer.setCreatedAt(System.currentTimeMillis());

        store.put(customer.getId(), customer);
        return toView(customer, "admin");
    }

    public CustomerView get(String id, String role) {
        return toView(find(id), role);
    }

    /**
     * 直接看存储层的内容，教学演示用
     */
    public Map<String, Object> raw(String id) {
        Customer customer = find(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phoneEncrypted", customer.getPhoneEncrypted());
        data.put("idCardEncrypted", customer.getIdCardEncrypted());
        data.put("passwordHash", customer.getPasswordHash());
        return data;
    }

    public boolean verifyPassword(String id, String rawPassword) {
        Customer customer = find(id);
        return BCrypt.checkpw(StrUtil.nullToEmpty(rawPassword), customer.getPasswordHash());
    }

    private Customer find(String id) {
        Customer customer = store.get(id);
        if (customer == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        return customer;
    }

    private CustomerView toView(Customer customer, String role) {
        String phone = aes.decryptStr(customer.getPhoneEncrypted());
        String idCard = aes.decryptStr(customer.getIdCardEncrypted());

        int age = IdcardUtil.getAgeByIdCard(idCard);
        String gender = IdcardUtil.getGenderByIdCard(idCard) == 1 ? "男" : "女";

        boolean full = "admin".equalsIgnoreCase(role);
        return new CustomerView(
                customer.getId(),
                customer.getName(),
                full ? phone : DesensitizedUtil.mobilePhone(phone),
                full ? idCard : DesensitizedUtil.idCardNum(idCard, 6, 4),
                age,
                gender,
                customer.getLevel());
    }
}
```

#### 步骤 6 · 写控制器

查询接口的 `role` 用 `@RequestParam` 传，默认 agent，也就是默认打码。这个默认值很关键：漏传参数时给最保守的结果，而不是给明文。

密码校验失败抛 `BusinessException(ErrorCode.PASSWORD_WRONG)`，由全局处理器转成 400 加 41062。早期写法是 `Result.fail(41062, ...)` 直接返回 200，状态码和 body 不一致，监控看不出异常。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/SensitiveDataController.java`

```java
package top.mqxu.toolkit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.CreateCustomerRequest;
import top.mqxu.toolkit.dto.CustomerView;
import top.mqxu.toolkit.service.SensitiveDataService;

import java.util.Map;

/**
 * 案例 9：敏感数据处理
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/customer")
@RequiredArgsConstructor
public class SensitiveDataController {

    private final SensitiveDataService sensitiveDataService;

    @PostMapping
    public Result<CustomerView> create(@RequestBody CreateCustomerRequest request) {
        return Result.ok(sensitiveDataService.create(request));
    }

    /**
     * role=admin 返回完整信息，其他角色返回脱敏信息
     */
    @GetMapping("/{id}")
    public Result<CustomerView> get(@PathVariable String id,
                                    @RequestParam(defaultValue = "agent") String role) {
        return Result.ok(sensitiveDataService.get(id, role));
    }

    /**
     * 查看存储层的密文，演示加密确实生效
     */
    @GetMapping("/{id}/raw")
    public Result<Map<String, Object>> raw(@PathVariable String id) {
        return Result.ok(sensitiveDataService.raw(id));
    }

    @PostMapping("/{id}/verify-password")
    public Result<Boolean> verifyPassword(@PathVariable String id,
                                          @RequestBody PasswordRequest request) {
        if (!sensitiveDataService.verifyPassword(id, request.password())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }
        return Result.ok("密码正确", true);
    }

    public record PasswordRequest(String password) {
    }
}
```

### 11.4 接口实测

创建客户，身份证用一个人人都知道的合法测试号：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/customer \
  -H 'Content-Type: application/json' \
  -d '{"name":"张三","phone":"13800001234","idCard":"11010519491231002X","password":"abc123","level":"vip"}'
```

```json
{"code":200,"data":{"id":"037b682880ba47ff95cb6904bea5ecf5","name":"张三","phone":"13800001234","idCard":"11010519491231002X","age":76,"gender":"女","level":"vip"},"message":"操作成功"}
```

以客服身份查，手机号中间四位打码，身份证只留前 6 后 4：

```json
{"code":200,"data":{"id":"037b682880ba47ff95cb6904bea5ecf5","name":"张三","phone":"138****1234","idCard":"110105********002X","age":76,"gender":"女","level":"vip"},"message":"操作成功"}
```

以管理员身份查，返回完整值：

```json
{"code":200,"data":{"id":"037b682880ba47ff95cb6904bea5ecf5","name":"张三","phone":"13800001234","idCard":"11010519491231002X","age":76,"gender":"女","level":"vip"},"message":"操作成功"}
```

看存储层，手机号和身份证是 Base64 密文，密码是 BCrypt 哈希：

```json
{"code":200,"data":{"phoneEncrypted":"8HIEEukWqDync5zv/MF11g==",
"idCardEncrypted":"V5X8EEXzzXyte0zwUv9+pkCPa9EMOrfVIcJTi+GWU00=",
"passwordHash":"$2a$10$xBXMpl5d1w/.zbZBuMOz.enSmw5KRj/VHsYx7.fT/m2z58QhN3tvO"},"message":"操作成功"}
```

校验密码，对和错：

```bash
curl --noproxy '*' -X POST \
  http://127.0.0.1:8015/toolkit/customer/037b682880ba47ff95cb6904bea5ecf5/verify-password \
  -H 'Content-Type: application/json' -d '{"password":"wrong"}'
curl --noproxy '*' -X POST \
  http://127.0.0.1:8015/toolkit/customer/037b682880ba47ff95cb6904bea5ecf5/verify-password \
  -H 'Content-Type: application/json' -d '{"password":"abc123"}'
```

```json
{"code":200,"data":true,"message":"密码正确"}
{"code":41062,"data":null,"message":"密码错误"}
```

密码错的这次，HTTP 状态码是 400，不是 200：

```text
400
```

传一个校验位不对的身份证号：

```json
{"code":40000,"data":null,"message":"身份证号不正确"}
```

### 11.5 容易踩的坑

1. 把存储对象直接返回给前端。密文、密码哈希全暴露。一定要有独立的对外视图对象。
2. AES 密钥随便写。长度不是 16、24、32 字节直接抛异常；用固定字符串当密钥、还提交到仓库，等于没有加密。密钥要放密钥管理服务，并支持轮换。
3. 用 AES 加密密码。能加密就能解密，运维或者拿到密钥的人能看到所有人的密码。密码只能用单向哈希，而且要用 BCrypt、Argon2 这类慢哈希，不要用 MD5 加盐。
4. 拿哈希值做等值比对。BCrypt 每次加盐，同一个明文两次结果不同，`hash.equals(savedHash)` 永远 false。必须 `checkpw`。
5. 在存储层直接存脱敏后的值。以后要发短信、要核对实名，就再也拿不到完整号码了。脱敏只做出站展示。
6. 脱敏规则写死成一种。客服看全码、运营看后四位、日志全打码，不同场景规则不同。抽成按角色或按场景的配置。
7. 日志里打了明文。`log.info` 直接把整个 request 打出来，会把手机号、身份证、密码全写进日志文件，比数据库泄露还容易。日志脱敏或者干脆不打。
8. 加密字段还建普通索引。密文每次加密结果不同（用了随机 IV），等值查询查不到。要查就用确定性加密，或者额外存一个哈希列做索引。

## 12. 案例 10：系统状态面板

### 12.1 需求

运维想要一个页面，打开就能看到这台机器的 CPU、内存、磁盘和 JVM 情况，不用登服务器敲命令。

后端出一个 `/toolkit/system` 接口返回 JSON，再配一个静态页面每 5 秒拉一次，画成卡片和进度条。

硬件信息用 oshi，依赖已经在第 2 章的 pom 里引过，这里不用再动依赖。

### 12.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/SystemInfo.java` | CPU、内存、磁盘、JVM、系统 |
| 2 | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/SystemController.java` | 读硬件与 JVM |
| 3 | `15-toolkit/src/main/resources/static/system.html` | 卡片、进度条、自动刷新 |


### 12.3 实现步骤

#### 步骤 1 · 定义状态结构

外层一个 record，里面五个内嵌 record。用 record 是因为这些数据只读，不需要 setter。

所有容量字段统一用字节数，单位换算交给前端。后端返回 `34359738368`，前端显示成 32.0 GB，这样前端想换成 MB 或 GB 都行。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/SystemInfo.java`

```java
package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 系统状态
 *
 * @param cpu    CPU
 * @param memory 物理内存
 * @param disks  磁盘
 * @param jvm    JVM
 * @param os     操作系统
 * @author mqxu
 * @date 2026-10-21
 */
public record SystemInfo(Cpu cpu, Memory memory, List<Disk> disks, Jvm jvm, Os os) {

    /**
     * @param model CPU 型号
     * @param cores 核心数
     * @param usage 使用率（百分比）
     */
    public record Cpu(String model, int cores, double usage) {
    }

    /**
     * @param total 总字节数
     * @param used  已用字节数
     * @param usage 使用率（百分比）
     */
    public record Memory(long total, long used, double usage) {
    }

    /**
     * @param name  卷名
     * @param mount 挂载点
     * @param total 总字节数
     * @param used  已用字节数
     * @param usage 使用率（百分比）
     */
    public record Disk(String name, String mount, long total, long used, double usage) {
    }

    /**
     * @param heapUsed       堆已用字节数
     * @param heapMax        堆上限字节数
     * @param threads        线程数
     * @param javaVersion    Java 版本
     * @param uptimeSeconds  运行时长（秒）
     */
    public record Jvm(long heapUsed, long heapMax, int threads,
                      String javaVersion, long uptimeSeconds) {
    }

    /**
     * @param name          系统名称
     * @param version       版本
     * @param arch          架构
     * @param uptimeSeconds 开机时长（秒）
     */
    public record Os(String name, String version, String arch, long uptimeSeconds) {
    }
}
```

#### 步骤 2 · 写系统信息控制器

硬件信息走 `OshiUtil`：`getCpuInfo()` 拿型号、核心数、空闲率，`getMemory()` 拿物理内存，`getOs().getFileSystem().getFileStores()` 遍历磁盘。

JVM 信息走 `ManagementFactory`：`getMemoryMXBean()` 拿堆，`getThreadMXBean()` 拿线程数，`getRuntimeMXBean()` 拿运行时长。这两套 API 分工明确，不要混用。

`getCpuInfo()` 默认采样 1 秒。这个接口本来就会慢一秒，换来的是真实的 CPU 使用率，不是瞬时值。嫌慢可以传采样间隔，但太快的数据没有参考价值。

每个读取方法都做了容错：CPU 读不到就返回 unknown，磁盘总量为 0 就跳过（容器里会出现这种伪设备），保证接口不会因为某一项失败整体 500。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/SystemController.java`

```java
package top.mqxu.toolkit.controller;

import cn.hutool.system.oshi.CpuInfo;
import cn.hutool.system.oshi.OshiUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.SystemInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * 案例 10：系统状态面板
 *
 * @author mqxu
 * @date 2026-10-21
 * @description OshiUtil 读硬件，ManagementFactory 读 JVM，合成一份状态
 */
@Slf4j
@RestController
@RequestMapping("/toolkit/system")
public class SystemController {

    @GetMapping
    public Result<SystemInfo> info() {
        SystemInfo.Cpu cpu = readCpu();
        SystemInfo.Memory memory = readMemory();
        List<SystemInfo.Disk> disks = readDisks();
        SystemInfo.Jvm jvm = readJvm();
        SystemInfo.Os os = readOs();
        return Result.ok(new SystemInfo(cpu, memory, disks, jvm, os));
    }

    private SystemInfo.Cpu readCpu() {
        try {
            // 默认采样 1 秒，接口会慢一点，换来的是真实使用率
            CpuInfo info = OshiUtil.getCpuInfo();
            double usage = Math.max(0, Math.min(100, 100 - info.getFree()));
            return new SystemInfo.Cpu(info.getCpuModel(), info.getCpuNum(), round(usage));
        } catch (Exception e) {
            log.warn("读取 CPU 信息失败：{}", e.getMessage());
            return new SystemInfo.Cpu("unknown", Runtime.getRuntime().availableProcessors(), 0);
        }
    }

    private SystemInfo.Memory readMemory() {
        var memory = OshiUtil.getMemory();
        long total = memory.getTotal();
        long used = total - memory.getAvailable();
        return new SystemInfo.Memory(total, used, round(percent(used, total)));
    }

    private List<SystemInfo.Disk> readDisks() {
        List<SystemInfo.Disk> disks = new ArrayList<>();
        OshiUtil.getOs().getFileSystem().getFileStores().forEach(store -> {
            long total = store.getTotalSpace();
            if (total <= 0) {
                return;
            }
            long used = total - store.getUsableSpace();
            disks.add(new SystemInfo.Disk(store.getName(), store.getMount(),
                    total, used, round(percent(used, total))));
        });
        return disks;
    }

    private SystemInfo.Jvm readJvm() {
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = runtime.maxMemory();
        return new SystemInfo.Jvm(heapUsed, heapMax, threadBean.getThreadCount(),
                System.getProperty("java.version"), runtimeBean.getUptime() / 1000);
    }

    private SystemInfo.Os readOs() {
        return new SystemInfo.Os(
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                OshiUtil.getOs().getSystemUptime());
    }

    private double percent(long used, long total) {
        return total <= 0 ? 0 : used * 100.0 / total;
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
```

#### 步骤 3 · 写静态面板页

放在 `static` 目录下，Spring Boot 直接当静态资源映射，访问路径是 `/system.html`，不带 `/toolkit` 前缀。这一点最容易搞错。

页面不用任何前端框架，原生 fetch 加模板字符串。三个指标卡加一个磁盘列表，进度条颜色按占用率变：60% 以下蓝色，60% 到 85% 橙色，85% 以上红色。

`setInterval(load, 5000)` 每 5 秒刷新一次。CPU 接口本身要 1 秒，5 秒的间隔不会把服务压垮。

路径：`15-toolkit/src/main/resources/static/system.html`

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>系统状态面板</title>
    <style>
        :root {
            --bg: #0b1220;
            --card: #131c2e;
            --line: #1f2b42;
            --text: #e6edf7;
            --muted: #8b9bb4;
            --accent: #38bdf8;
            --ok: #34d399;
            --warn: #fbbf24;
            --danger: #f87171;
        }

        * {
            box-sizing: border-box;
        }

        body {
            margin: 0;
            padding: 32px 24px 48px;
            background: radial-gradient(circle at 15% 0%, #152442 0%, var(--bg) 45%);
            color: var(--text);
            font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
            min-height: 100vh;
        }

        header {
            max-width: 1080px;
            margin: 0 auto 24px;
            display: flex;
            align-items: flex-end;
            justify-content: space-between;
            gap: 16px;
        }

        h1 {
            margin: 0;
            font-size: 26px;
            letter-spacing: 1px;
        }

        .subtitle {
            margin-top: 6px;
            color: var(--muted);
            font-size: 13px;
        }

        .actions {
            display: flex;
            align-items: center;
            gap: 12px;
            color: var(--muted);
            font-size: 13px;
        }

        button {
            background: #1b2942;
            color: var(--text);
            border: 1px solid var(--line);
            border-radius: 8px;
            padding: 8px 16px;
            font-size: 13px;
            cursor: pointer;
            transition: border-color .2s, background .2s;
        }

        button:hover {
            border-color: var(--accent);
            background: #22334f;
        }

        main {
            max-width: 1080px;
            margin: 0 auto;
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 18px;
        }

        .card {
            background: linear-gradient(160deg, #16233a 0%, var(--card) 100%);
            border: 1px solid var(--line);
            border-radius: 14px;
            padding: 20px;
        }

        .card.wide {
            grid-column: span 3;
        }

        .card-title {
            color: var(--muted);
            font-size: 13px;
            letter-spacing: .5px;
            margin-bottom: 14px;
        }

        .metric {
            font-size: 34px;
            font-weight: 600;
            line-height: 1.1;
        }

        .metric small {
            font-size: 14px;
            color: var(--muted);
            font-weight: 400;
            margin-left: 4px;
        }

        .detail {
            margin-top: 10px;
            color: var(--muted);
            font-size: 13px;
            line-height: 1.7;
            word-break: break-all;
        }

        .bar {
            margin-top: 16px;
            height: 8px;
            background: #0d1626;
            border-radius: 99px;
            overflow: hidden;
        }

        .bar span {
            display: block;
            height: 100%;
            width: 0;
            border-radius: 99px;
            background: linear-gradient(90deg, #22d3ee, #3b82f6);
            transition: width .6s ease;
        }

        .disk {
            display: grid;
            grid-template-columns: 1.2fr 1fr 1.4fr;
            gap: 16px;
            align-items: center;
            padding: 14px 0;
            border-top: 1px solid var(--line);
        }

        .disk:first-of-type {
            border-top: none;
            padding-top: 0;
        }

        .disk-name {
            font-size: 14px;
        }

        .disk-name small {
            display: block;
            color: var(--muted);
            font-size: 12px;
            margin-top: 4px;
        }

        .disk-size {
            color: var(--muted);
            font-size: 13px;
            text-align: right;
        }

        .footer {
            max-width: 1080px;
            margin: 20px auto 0;
            color: var(--muted);
            font-size: 12px;
            display: flex;
            justify-content: space-between;
        }

        @media (max-width: 860px) {
            main {
                grid-template-columns: 1fr;
            }

            .card.wide {
                grid-column: span 1;
            }

            .disk {
                grid-template-columns: 1fr;
                gap: 8px;
            }

            .disk-size {
                text-align: left;
            }
        }
    </style>
</head>
<body>
<header>
    <div>
        <h1>系统状态面板</h1>
        <div class="subtitle">数据来自 OshiUtil 与 ManagementFactory，每 5 秒自动刷新</div>
    </div>
    <div class="actions">
        <span id="updated">加载中</span>
        <button onclick="load()">刷新</button>
    </div>
</header>

<main>
    <section class="card">
        <div class="card-title">CPU</div>
        <div class="metric"><span id="cpuUsage">--</span><small>%</small></div>
        <div class="bar"><span id="cpuBar"></span></div>
        <div class="detail">
            <div id="cpuModel">--</div>
            <div><span id="cpuCores">--</span> 个逻辑核心</div>
        </div>
    </section>

    <section class="card">
        <div class="card-title">物理内存</div>
        <div class="metric"><span id="memUsage">--</span><small>%</small></div>
        <div class="bar"><span id="memBar"></span></div>
        <div class="detail">
            <div>已用 <span id="memUsed">--</span></div>
            <div>总量 <span id="memTotal">--</span></div>
        </div>
    </section>

    <section class="card">
        <div class="card-title">JVM 堆</div>
        <div class="metric"><span id="heapUsage">--</span><small>%</small></div>
        <div class="bar"><span id="heapBar"></span></div>
        <div class="detail">
            <div>已用 <span id="heapUsed">--</span> / 上限 <span id="heapMax">--</span></div>
            <div><span id="threads">--</span> 个线程，运行 <span id="uptime">--</span></div>
        </div>
    </section>

    <section class="card wide">
        <div class="card-title">磁盘</div>
        <div id="disks"></div>
    </section>
</main>

<div class="footer">
    <span id="osInfo">--</span>
    <span>15-toolkit</span>
</div>

<script>
    const $ = (id) => document.getElementById(id);

    function formatBytes(bytes) {
        if (!bytes) return '0 B';
        const units = ['B', 'KB', 'MB', 'GB', 'TB'];
        const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
        return (bytes / Math.pow(1024, index)).toFixed(1) + ' ' + units[index];
    }

    function colorOf(value) {
        if (value >= 85) return 'linear-gradient(90deg, #f87171, #ef4444)';
        if (value >= 60) return 'linear-gradient(90deg, #fbbf24, #f59e0b)';
        return 'linear-gradient(90deg, #22d3ee, #3b82f6)';
    }

    function setBar(barId, value) {
        const bar = $(barId);
        bar.style.width = Math.min(100, Math.max(0, value)) + '%';
        bar.style.background = colorOf(value);
    }

    function renderDisks(disks) {
        const box = $('disks');
        if (!disks || disks.length === 0) {
            box.innerHTML = '<div class="detail">没有读到磁盘信息</div>';
            return;
        }
        box.innerHTML = disks.map((disk) => `
            <div class="disk">
                <div class="disk-name">${disk.name}<small>${disk.mount}</small></div>
                <div class="disk-size">${formatBytes(disk.used)} / ${formatBytes(disk.total)}</div>
                <div class="bar" style="margin-top:0">
                    <span style="width:${Math.min(100, disk.usage)}%;background:${colorOf(disk.usage)}"></span>
                </div>
            </div>
        `).join('');
    }

    async function load() {
        try {
            const response = await fetch('/toolkit/system');
            const {data} = await response.json();

            $('cpuUsage').textContent = data.cpu.usage.toFixed(1);
            $('cpuModel').textContent = data.cpu.model || 'unknown';
            $('cpuCores').textContent = data.cpu.cores;
            setBar('cpuBar', data.cpu.usage);

            $('memUsage').textContent = data.memory.usage.toFixed(1);
            $('memUsed').textContent = formatBytes(data.memory.used);
            $('memTotal').textContent = formatBytes(data.memory.total);
            setBar('memBar', data.memory.usage);

            const heapUsage = data.jvm.heapMax > 0 ? data.jvm.heapUsed * 100 / data.jvm.heapMax : 0;
            $('heapUsage').textContent = heapUsage.toFixed(1);
            $('heapUsed').textContent = formatBytes(data.jvm.heapUsed);
            $('heapMax').textContent = formatBytes(data.jvm.heapMax);
            $('threads').textContent = data.jvm.threads;
            $('uptime').textContent = Math.floor(data.jvm.uptimeSeconds / 60) + ' 分钟';
            setBar('heapBar', heapUsage);

            renderDisks(data.disks);

            $('osInfo').textContent =
                `${data.os.name} ${data.os.version} · ${data.os.arch} · Java ${data.jvm.javaVersion}`;
            $('updated').textContent = '更新于 ' + new Date().toLocaleTimeString();
        } catch (error) {
            $('updated').textContent = '读取失败：' + error.message;
        }
    }

    load();
    setInterval(load, 5000);
</script>
</body>
</html>
```

### 12.4 接口实测

接口返回的真实数据。CPU 型号原文是多行，这里只留第一行，磁盘也只留前两个：

```bash
curl --noproxy '*' http://127.0.0.1:8015/toolkit/system
```

```json
{"code":200,"data":{
"cpu":{"model":"Apple M1 Max","cores":10,"usage":10.4},
"memory":{"total":34359738368,"used":23533977600,"usage":68.5},
"disks":[{"name":"Macintosh HD","mount":"/","total":994662584320,"used":594051661824,"usage":59.7},
         {"name":"VM","mount":"/System/Volumes/VM","total":994662584320,"used":594051661824,"usage":59.7}],
"jvm":{"heapUsed":49532624,"heapMax":8589934592,"threads":33,"javaVersion":"21.0.12.1","uptimeSeconds":762},
"os":{"name":"Mac OS X","version":"27.0.1","arch":"aarch64","uptimeSeconds":1849912}},
"message":"操作成功"}
```

打开面板页：

```text
http://127.0.0.1:8015/system.html
```

页面每 5 秒自动刷新，右上角显示最近一次更新的时间。直接访问 `/toolkit/system.html` 会是 404，因为静态资源不在那个前缀下。

### 12.5 容易踩的坑

1. 容器里读到的是宿主机的数据。Oshi 读的是 `/proc`，容器没做隔离时会拿到宿主机内存和磁盘，数字看着很大。要容器内的数据，得限制或换 cgroup 读取。
2. CPU 使用率第一次读是 0。Oshi 需要两次采样才能算出使用率，启动后立刻调用会得到 0 或负数。代码里做了 `Math.max(0, ...)` 兜底。
3. CPU 型号里带换行。macOS 上 `getCpuModel()` 返回好几行，直接塞进页面会把布局撑坏。前端截断或者后端取第一行。
4. 磁盘列表把伪文件系统也算进去。`/System/Volumes/VM`、`/dev` 这些总量和主盘一样，看着像重复。按总量为 0 过滤只挡掉一部分，真实项目还要按挂载点白名单过滤。
5. 页面路径写成 `/toolkit/system.html`。静态资源在 `static` 根下，类上的 `@RequestMapping` 只作用于接口，不影响静态文件。
6. 这种接口直接暴露在公网。CPU 型号、磁盘、JVM 版本都是信息泄露，给攻击者挑漏洞用的。要么加鉴权，要么只在内网开。
7. 每次都 new 一个 Oshi 实例。`OshiUtil` 内部是单例，直接用静态方法就好，自己 new 会重复初始化，第一次调用特别慢。

## 13. 案例 11：接口巡检

### 13.1 需求

线上有几个地址，想定时探一下通不通、慢不慢。串行探十个地址，每个超时 3 秒，最坏要 30 秒，必须并发。

输入一批地址，输出每个地址的状态码、耗时、是否可用，再给一个汇总：总数、成功数、失败数、平均耗时。明细按耗时从高到低排，慢的排前面。

### 13.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/InspectRequest.java` | 一批地址 |
| 2 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/InspectResult.java` | 状态码、耗时、是否可用 |
| 3 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/InspectSummary.java` | 总数、成功、失败、平均耗时 |
| 4 | `15-toolkit/src/main/java/top/mqxu/toolkit/service/InspectService.java` | 线程池并发探测 |
| 5 | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/InspectController.java` | 一个 POST 接口 |

### 13.3 实现步骤

#### 步骤 1 · 定义巡检请求

一个 record 包一个 List。也可以用 `String[]`，但 List 在 stream 处理时更顺手。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/InspectRequest.java`

```java
package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 巡检请求
 *
 * @param urls 待探测的地址
 * @author mqxu
 * @date 2026-10-21
 */
public record InspectRequest(List<String> urls) {
}
```

#### 步骤 2 · 定义单条结果

`error` 在成功时是 null，失败时是原因。`ok` 单独一个字段，不让调用方自己拿状态码判断，因为“连接失败”时状态码是 0，判断逻辑容易写漏。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/InspectResult.java`

```java
package top.mqxu.toolkit.dto;

/**
 * 单个地址的探测结果
 *
 * @param url    地址
 * @param status HTTP 状态码，连不上时为 0
 * @param costMs 耗时（毫秒）
 * @param ok     是否可用（2xx 或 3xx）
 * @param error  失败原因
 * @author mqxu
 * @date 2026-10-21
 */
public record InspectResult(String url, int status, long costMs, boolean ok, String error) {
}
```

#### 步骤 3 · 定义汇总结果

汇总字段和明细一起返回，省得前端自己算。`avgCostMs` 用 long，平均耗时不需要小数。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/InspectSummary.java`

```java
package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 巡检汇总
 *
 * @param total     总数
 * @param success   可用数
 * @param failed    失败数
 * @param avgCostMs 平均耗时
 * @param results   明细，按耗时从高到低
 * @author mqxu
 * @date 2026-10-21
 */
public record InspectSummary(int total, int success, int failed,
                             long avgCostMs, List<InspectResult> results) {
}
```

#### 步骤 4 · 写巡检服务

并发数取地址数量和配置上限的较小值。只有 3 个地址却开 8 个线程，浪费；有 100 个地址全开，会把对方打挂，也会耗尽自己的线程。

每个地址提交一个任务，返回 `Future`，再按提交顺序 `get`。`get` 会等，但任务是并行跑的，总耗时约等于最慢的那个，不是所有耗时相加。

`future.get()` 用 try-catch 包住，任何一个任务抛异常都转成一条失败记录，不能让整批挂掉。

`pool.shutdown()` 放在 finally 里。线程池不关，请求结束后线程还在，几百次调用就堆出几千个线程。

排序按耗时倒序，最慢的排最前面。巡检的目的是发现慢的地址，慢的藏在一堆快的里面没有意义。

`probe` 里先校验 URL 格式，不合法直接返回失败记录，不去发请求。请求用 `HttpRequest.get(url).timeout(...)`，超时时间从配置读。状态码 2xx 和 3xx 算可用，4xx 和 5xx 算失败。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/service/InspectService.java`

```java
package top.mqxu.toolkit.service;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.InspectResult;
import top.mqxu.toolkit.dto.InspectSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 接口巡检
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 线程池并发探测，每个地址一个任务，最后按耗时排序汇总
 */
@Service
@RequiredArgsConstructor
public class InspectService {

    private final ToolkitProperties properties;

    public InspectSummary inspect(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "urls 不能为空");
        }
        List<String> targets = urls.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "urls 不能为空");
        }

        int concurrency = Math.min(targets.size(),
                Math.max(1, properties.getInspect().getMaxConcurrency()));
        ExecutorService pool = ThreadUtil.newExecutor(concurrency);
        List<InspectResult> results = new ArrayList<>();

        try {
            List<Future<InspectResult>> futures = new ArrayList<>();
            for (String url : targets) {
                futures.add(pool.submit(() -> probe(url)));
            }
            for (Future<InspectResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    results.add(new InspectResult("unknown", 0, 0, false, e.getMessage()));
                }
            }
        } finally {
            pool.shutdown();
        }

        results.sort(Comparator.comparingLong(InspectResult::costMs).reversed());
        int success = (int) results.stream().filter(InspectResult::ok).count();
        long avg = (long) results.stream().mapToLong(InspectResult::costMs).average().orElse(0);

        return new InspectSummary(results.size(), success, results.size() - success,
                avg, results);
    }

    private InspectResult probe(String url) {
        if (!Validator.isUrl(url)) {
            return new InspectResult(url, 0, 0, false, "地址格式不正确");
        }

        long start = System.currentTimeMillis();
        try {
            int status = HttpRequest.get(url)
                    .timeout(properties.getInspect().getTimeoutMillis())
                    .execute()
                    .getStatus();
            long cost = System.currentTimeMillis() - start;
            return new InspectResult(url, status, cost,
                    status >= 200 && status < 400, null);
        } catch (Exception e) {
            return new InspectResult(url, 0, System.currentTimeMillis() - start,
                    false, StrUtil.maxLength(e.getMessage(), 120));
        }
    }
}
```

#### 步骤 5 · 写控制器

请求体直接是地址数组，`@RequestBody InspectRequest` 接。没做参数校验注解，因为在 Service 里已经判空并给了明确提示，注解只能加在字段上，对 List 元素不好用。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/InspectController.java`

```java
package top.mqxu.toolkit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.InspectRequest;
import top.mqxu.toolkit.dto.InspectSummary;
import top.mqxu.toolkit.service.InspectService;

/**
 * 案例 11：接口巡检
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/inspect")
@RequiredArgsConstructor
public class InspectController {

    private final InspectService inspectService;

    @PostMapping
    public Result<InspectSummary> inspect(@RequestBody InspectRequest request) {
        return Result.ok(inspectService.inspect(request.urls()));
    }
}
```

### 13.4 接口实测

一次探四个地址，包括正常接口、404、连不上的端口、格式错误的字符串：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/inspect \
  -H 'Content-Type: application/json' \
  -d '{"urls":["http://127.0.0.1:8015/toolkit/system","http://127.0.0.1:8015/toolkit/nothing","http://127.0.0.1:9/none","not-a-url"]}'
```

```json
{"code":200,"data":{"total":4,"success":1,"failed":3,"avgCostMs":255,"results":[
{"url":"http://127.0.0.1:8015/toolkit/system","status":200,"costMs":1018,"ok":true,"error":null},
{"url":"http://127.0.0.1:8015/toolkit/nothing","status":404,"costMs":2,"ok":false,"error":null},
{"url":"http://127.0.0.1:9/none","status":0,"costMs":1,"ok":false,"error":"ConnectException: Connection refused"},
{"url":"not-a-url","status":0,"costMs":0,"ok":false,"error":"地址格式不正确"}]},"message":"操作成功"}
```

注意 system 那个地址耗时 1018 毫秒，因为 CPU 信息要采样 1 秒；其余三个几乎是瞬间返回失败。总耗时约 1 秒，四个地址是并发探的，不是相加。

传空数组：

```json
{"code":40000,"data":null,"message":"urls 不能为空"}
```

### 13.5 容易踩的坑

1. 串行 for 循环发请求。十个地址每个 3 秒超时，最坏 30 秒，接口直接超时。必须并发。
2. 线程池不 shutdown。请求一次泄漏一批线程，跑一天线程数上千。用 try-finally。
3. 并发数不设上限。一百个地址开一百个线程，自己的 CPU 和对方服务都受不了。取地址数和配置上限的较小值。
4. 不设超时。对端不响应时 `HttpRequest` 默认等很久，整个巡检卡死。必须设 connect 和 read 超时。
5. 4xx 算成功。连接是通的，但接口已经不可用了，404 和 500 都应该算失败。这里只把 2xx 和 3xx 算 ok。
6. 错误信息直接全量返回。异常 message 里可能有内网地址、依赖库版本、SQL 片段，最多截断到 120 字符，不要原样透出。
7. 允许探测任意地址。这个接口如果不鉴权还放公网，就是一个 SSRF 跳板，可以被用来探测内网端口。真实项目要限制目标域名白名单，或者只允许探测自己配置里的地址。

## 14. 案例 12：文本清洗

### 14.1 需求

用户提交的文本里常混着手机号、邮箱、身份证、链接、IP，直接入库或者展示都有风险。要能自动识别出来、打码、再返回。

另外要做敏感词过滤：命中哪些词、替换成星号后的文本、带高亮的文本，三样都给。

两个接口：`/toolkit/text/extract` 做提取加脱敏，`/toolkit/text/sensitive` 做敏感词。

### 14.2 涉及文件

按下面的顺序建文件，后面的类依赖前面的类。全部写完再启动，避免编译不过。

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/TextExtractResult.java` | 五类数据加脱敏文本 |
| 2 | `15-toolkit/src/main/java/top/mqxu/toolkit/dto/SensitiveResult.java` | 命中、替换、高亮 |
| 3 | `15-toolkit/src/main/java/top/mqxu/toolkit/service/TextService.java` | 正则提取、脱敏、DFA 过滤 |
| 4 | `15-toolkit/src/main/java/top/mqxu/toolkit/controller/TextController.java` | 两个 POST 接口 |


### 14.3 实现步骤

#### 步骤 1 · 定义提取结果

五类分别一个 List，前端可以按需展示。`maskedText` 是整段文本打码后的结果，直接可以展示或者入库。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/TextExtractResult.java`

```java
package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 文本提取结果
 *
 * @param phones     手机号
 * @param emails     邮箱
 * @param ips        IP
 * @param urls       链接
 * @param idCards    身份证
 * @param maskedText 脱敏后的原文
 * @author mqxu
 * @date 2026-10-21
 */
public record TextExtractResult(List<String> phones, List<String> emails, List<String> ips,
                                List<String> urls, List<String> idCards, String maskedText) {
}
```

#### 步骤 2 · 定义敏感词结果

`filtered` 用于入库和展示，`highlighted` 用于审核后台标红。两个都给，省得前端再做一次替换。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/dto/SensitiveResult.java`

```java
package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 敏感词过滤结果
 *
 * @param hits        命中的词
 * @param filtered    替换成星号后的文本
 * @param highlighted 用 mark 标签包起来的文本，前端直接渲染
 * @author mqxu
 * @date 2026-10-21
 */
public record SensitiveResult(List<String> hits, String filtered, String highlighted) {
}
```

#### 步骤 3 · 写文本服务

五条正则在类加载时编译好，不用每次调用都 `Pattern.compile`。这是正则使用的常识，编译一次能用一辈子。

`ReUtil.findAllGroup0` 返回所有匹配的完整内容，不去重、按出现顺序。同一个号码出现两次会返回两条，前端去重是前端的事，服务端保持原样。

脱敏替换的顺序是先身份证、再手机号、再邮箱、再链接、最后 IP。身份证 18 位里包含 11 位连续数字，如果先处理手机号，身份证中间那段会被当成手机号打码，结果就成了一段乱码。先长后短，是这类批量替换的通用原则。

`DesensitizedUtil` 提供现成的规则：`mobilePhone` 留前 3 后 4，`idCardNum(value, 6, 4)` 留前 6 后 4，`email` 只留首字母和域名。链接和 IP 没有现成方法，自己写了 `maskUrl` 和 `maskIp`。

敏感词用 `SensitiveUtil`，底层是 DFA 树。匹配是 O(n) 的，和词库大小关系不大，比逐个 `contains` 快得多。词库在 `@PostConstruct` 里初始化一次，`init` 会重建整棵树，不要每次请求都调。

`getFoundAllSensitive` 拿命中词，`sensitiveFilter` 做替换。替换用 `SensitiveProcessor` 回调，想换成星号就返回等长的星号，想加高亮就返回 `<mark>` 包起来的词。一个方法两种用法，不用写两套逻辑。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/service/TextService.java`

```java
package top.mqxu.toolkit.service;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.SensitiveProcessor;
import cn.hutool.dfa.SensitiveUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.dto.SensitiveResult;
import top.mqxu.toolkit.dto.TextExtractResult;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本清洗
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 正则提取手机号、邮箱、IP、链接、身份证并脱敏，敏感词用 DFA 树匹配
 */
@Service
public class TextService {

    private static final List<String> SENSITIVE_WORDS =
            List.of("暴力", "赌博", "诈骗", "违禁品", "代开发票");

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern IP = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern URL =
            Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[\\dXx]\\b");

    @PostConstruct
    void init() {
        SensitiveUtil.init(SENSITIVE_WORDS);
    }

    public TextExtractResult extract(String text) {
        String source = StrUtil.nullToEmpty(text);

        List<String> idCards = ReUtil.findAllGroup0(ID_CARD, source);
        List<String> phones = ReUtil.findAllGroup0(PHONE, source);
        List<String> emails = ReUtil.findAllGroup0(EMAIL, source);
        List<String> urls = ReUtil.findAllGroup0(URL, source);
        List<String> ips = ReUtil.findAllGroup0(IP, source);

        // 先处理长度更长的身份证，避免 18 位里的数字被手机号规则误伤
        String masked = source;
        for (String value : idCards) {
            masked = masked.replace(value, DesensitizedUtil.idCardNum(value, 6, 4));
        }
        for (String value : phones) {
            masked = masked.replace(value, DesensitizedUtil.mobilePhone(value));
        }
        for (String value : emails) {
            masked = masked.replace(value, DesensitizedUtil.email(value));
        }
        for (String value : urls) {
            masked = masked.replace(value, maskUrl(value));
        }
        for (String value : ips) {
            masked = masked.replace(value, maskIp(value));
        }

        return new TextExtractResult(phones, emails, ips, urls, idCards, masked);
    }

    public SensitiveResult filter(String text) {
        String source = StrUtil.nullToEmpty(text);

        List<String> hits = SensitiveUtil.getFoundAllSensitive(source).stream()
                .map(FoundWord::getWord)
                .distinct()
                .toList();

        String filtered = SensitiveUtil.sensitiveFilter(source, true, new SensitiveProcessor() {
            @Override
            public String process(FoundWord word) {
                return "*".repeat(word.getWord().length());
            }
        });
        String highlighted = SensitiveUtil.sensitiveFilter(source, true, new SensitiveProcessor() {
            @Override
            public String process(FoundWord word) {
                return "<mark>" + word.getWord() + "</mark>";
            }
        });

        return new SensitiveResult(hits, filtered, highlighted);
    }

    /**
     * 保留协议和域名，路径统一打码
     */
    private String maskUrl(String url) {
        String host = ReUtil.getGroup0("^https?://[^/]+", url);
        return StrUtil.isBlank(host) ? "/***" : host + "/***";
    }

    /**
     * 保留前两段，后两段打码
     */
    private String maskIp(String ip) {
        return ip.replaceAll("^(\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}$", "$1.*.*");
    }
}
```

#### 步骤 4 · 写控制器

两个接口共用一个 `TextRequest` record。请求体只有一个字段，没必要建两个类。

没有加 `@Valid` 和必填校验：文本为空时返回空结果比返回 400 更合理，调用方传空串不应该算错误。

路径：`15-toolkit/src/main/java/top/mqxu/toolkit/controller/TextController.java`

```java
package top.mqxu.toolkit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.toolkit.common.Result;
import top.mqxu.toolkit.dto.SensitiveResult;
import top.mqxu.toolkit.dto.TextExtractResult;
import top.mqxu.toolkit.service.TextService;

/**
 * 案例 12：文本清洗
 *
 * @author mqxu
 * @date 2026-10-21
 */
@RestController
@RequestMapping("/toolkit/text")
@RequiredArgsConstructor
public class TextController {

    private final TextService textService;

    /**
     * 提取并脱敏
     */
    @PostMapping("/extract")
    public Result<TextExtractResult> extract(@RequestBody TextRequest request) {
        return Result.ok(textService.extract(request.text()));
    }

    /**
     * 敏感词过滤
     */
    @PostMapping("/sensitive")
    public Result<SensitiveResult> sensitive(@RequestBody TextRequest request) {
        return Result.ok(textService.filter(request.text()));
    }

    public record TextRequest(String text) {
    }
}
```

### 14.4 接口实测

造一段什么都有的文本：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/text/extract \
  -H 'Content-Type: application/json' \
  -d '{"text":"联系 13800001234 或 service@example.com，身份证 11010519491231002X，图片 http://cdn.example.com/a/b.png?x=1，IP 192.168.1.100。"}'
```

```json
{"code":200,"data":{
"phones":["13800001234","19491231002"],
"emails":["service@example.com"],
"ips":["192.168.1.100"],
"urls":["http://cdn.example.com/a/b.png?x=1"],
"idCards":["11010519491231002X"],
"maskedText":"联系 138****1234 或 s******@example.com，身份证 110105********002X，图片 http://cdn.example.com/***，IP 192.168.*.*。"},"message":"操作成功"}
```

这里有个真实的问题：`phones` 里多出来一个 `19491231002`，它是身份证号中间的一段。脱敏后的文本是对的，因为身份证先被替换掉了；但提取列表不准。这条留作练习，见第 17 章第 4 题。

敏感词过滤，同一段文本里放三个词：

```bash
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/text/sensitive \
  -H 'Content-Type: application/json' \
  -d '{"text":"内容涉及赌博和诈骗，代开发票请绕行。"}'
```

```json
{"code":200,"data":{
"hits":["赌博","诈骗","代开发票"],
"filtered":"内容涉及**和**，****请绕行。",
"highlighted":"内容涉及<mark>赌博</mark>和<mark>诈骗</mark>，<mark>代开发票</mark>请绕行。"},"message":"操作成功"}
```

注意“代开发票”是四个字，替换成四个星号，不是一个星号。星号数量按词长来，用户能大致看出被替换了几个字。

### 14.5 容易踩的坑

1. 替换顺序从短到长。先打码手机号，身份证中间那 11 位会被打一次，再处理身份证时就匹配不上了，整段文本变成 `110105138****1234X` 这种四不像。先长后短。
2. 正则不加边界。`1[3-9]\d{9}` 会匹配身份证里的连续数字。真要准确，得用后行断言排除前后相邻的数字，或者先把身份证的区间剔除再匹配手机号。上面的实测结果就是这个问题。
3. `\b` 在中文里不好用。Java 正则的 `\b` 是按 `\w` 判断的，中文不算单词字符，中文紧邻数字时的边界行为和直觉不一致。能不用就不用，用明确的前后字符类。
4. 每次请求都 `SensitiveUtil.init`。它会把整棵 DFA 树重建，几百个词也要重建几百个节点，并发时还有线程安全问题。初始化一次就够。
5. 以为敏感词库是静态的。审核要求每天都在变，词库要能热更新：从数据库或配置中心拉，定时重建树，或者换成带版本号的实现。
6. 只做前端过滤。前端过滤是给用户看的，接口必须再过滤一次，抓包直接调接口就绕过了。
7. 把命中词直接返回给用户。审核后台可以看，普通用户看到“你命中了 XX 词”，等于告诉他规则，会被用来试探。给用户的提示要模糊。

## 15. 生产边界

这十二个案例为了能在一台机器上跑通，数据全放进程内存，文件全放本地临时目录。哪些能直接搬走，哪些必须换，这一章逐条说清楚。

| 案例 | 现在的做法 | 上生产要换的 |
| --- | --- | --- |
| 图形验证码 | TimedCache 存答案 | 换 Redis，多实例共享；答案带 TTL，键用业务前缀 |
| 扫码登录 | TimedCache 存会话 | 换 Redis 加发布订阅或 WebSocket，避免前端高频轮询 |
| 商品海报 | Java2D 现场合成 | 图大或并发高时换独立图片服务，加缓存，合成结果存对象存储 |
| 短链服务 | 内存 Map 存映射，AtomicLong 计数 | 映射和计数都进 Redis，落库做持久化和报表 |
| Excel 导入导出 | 内存模式写入，本地目录存错误文件 | 大文件换 BigExcelWriter 或流式；错误文件进对象存储并设过期 |
| 分片上传 | 本地磁盘存分片，扫目录算进度 | 分片进对象存储，进度记 Redis，合并交给对象存储的合并接口 |
| 上传安全 | 文件头识别加白名单 | 再加病毒扫描、图片二次编码、上传目录禁执行、随机文件名 |
| 开放接口签名 | 单个 appId，内存记 nonce | 多应用多密钥，密钥放配置中心并支持轮换，nonce 进 Redis |
| 敏感数据 | AES 加密，ConcurrentHashMap 存储 | 密钥进 KMS，字段加密用确定性方案以便查询，审计日志单独记 |
| 系统面板 | 公开接口 | 加鉴权，只在内网开放，或接 Prometheus 走指标采集 |
| 接口巡检 | 任意地址都能探 | 目标地址白名单，防止被当成 SSRF 跳板，结果落库做趋势 |
| 文本清洗 | 启动时初始化一次词库 | 词库外置，支持热更新和版本化，长文本走异步任务 |

还有几条是跨案例的。临时目录要有清理策略，不能只靠重启。这类“演示接口”不要和生产接口放在同一个应用里，出问题时影响面太大。所有对外暴露的调试开关（比如验证码的 `debug-code`）都要在打包时关掉，最稳的做法是用 profile 隔离，生产 profile 里干脆没有这个字段。

## 16. 常见问题

### 16.1 为什么用 Hutool 5.8 而不是 6.x

6.x 到写这一节时还是里程碑版本，包名从 `cn.hutool` 改成了 `org.dromara.hutool`，API 也有调整，教学和实际项目都不适合用。5.8.48 是 5.x 的稳定版本，包名和网上大部分资料一致。

### 16.2 为什么二维码、Excel、系统信息要单独引依赖

Hutool 把这些第三方库声明成了 optional 或 provided，用 Maven 的传递依赖机制不会带下来。这是作者有意为之：不需要二维码的项目不该被迫引入 zxing。代价是用到这几个功能时必须自己加依赖，而且版本要自己管。

### 16.3 不引依赖会怎样

编译能过，因为 Hutool 的类都在。运行到那一行才抛 `NoClassDefFoundError`，报错信息指向 Hutool 的类，很容易误以为是 Hutool 的问题。看到这个错，先查对应的第三方库有没有引。

### 16.4 为什么业务失败要返回 4xx

统一返回 200、靠 body 里的 code 区分成败，是很多项目的习惯。问题是所有看 HTTP 状态码的东西都被骗了：Nginx 的访问日志里全是 200，监控的失败率永远是 0，巡检探不出问题，网关的重试策略也不生效。业务码映射到状态码，改动不大，收益很明显。

### 16.5 内存里的数据重启就没了，怎么调试

这正是练习工程的设计。每章都给了完整的 curl 流程，重启后照着敲一遍就能复现。需要跨重启保留的，换 Redis 或者加一张表，第 15 章列了对应关系。

### 16.6 端口不是 8015

先看启动日志里的 `Tomcat started on port`。如果和环境变量里的 `SERVER_PORT`、`SERVER__PORT` 不一致，说明被环境变量覆盖了。启动前 `env -u SERVER__PORT` 清掉。另外端口被占用时 Spring Boot 会直接启动失败，不会自动换端口。

### 16.7 curl 报连接失败但服务在跑

本机如果配了 HTTP 代理，curl 访问 127.0.0.1 也会走代理，代理连不上本机就失败。加 `--noproxy '*'`。这一节所有命令都带了，照抄即可。

### 16.8 中文参数变成乱码

URL 上的中文必须 percent-encoding。把中文直接写在查询串里，Tomcat 按 ISO-8859-1 解码，结果是乱码或者 400。用 `-F` 表单方式，或者 `--data-urlencode`。

## 17. 练习

十二个案例的代码都能跑，但都留了口子。下面五道题按难度排，前两道改代码，后三道要动结构。

1. 把验证码的答案存到 Redis，键是 `captcha:{id}`，TTL 60 秒，验证通过后删除。要求多实例部署时也能校验通过。
2. 给扫码登录加一个“已扫码但未确认”的轮询优化：网页轮询到 SCANNED 后把间隔从 2 秒改成 1 秒，CONFIRMED 后停止轮询。前端伪代码写出来即可。
3. 短链加一个按天统计：每天点了多少次，用一个 Map 存日期到计数的映射，统计接口返回最近 7 天。想清楚跨天时怎么处理。
4. 修掉文本清洗里手机号误匹配身份证的问题。提示：先把身份证的匹配区间找出来，在这些区间内不再做手机号匹配，或者用后行断言 `(?<!\d)` 加先行断言 `(?!\d)`。改完用 `11010519491231002X` 这个号验证，`phones` 里不应该再有 `19491231002`。
5. 把分片上传的进度从“扫目录”改成“Redis 里的位图”。用 `SETBIT upload:{id} index 1` 标记已传，`BITCOUNT` 算已传数量，`GETBIT` 判断某片是否已传。对比两种做法在 1000 片时的性能。

## 18. 小结

这一节的十二个案例，用的都是很普通的工具类，难点不在 API，在于把需求拆成正确的步骤、把状态和边界处理干净。

回头看，几件事反复出现。缓存要设过期并定时清理，读缓存要明确是否续期。状态流转要加锁，判断和修改放在同一个同步块里。用户输入的文件名、URL、文本都不能直接信，要么白名单，要么先清洗。对外返回的对象要和存储对象分开，避免顺手泄露字段。错误要有码、有提示、有正确的 HTTP 状态码。

工具库能省掉大量样板代码，但它不负责帮你判断该不该这么做。验证码该不该下发答案、短链该用 301 还是 302、密码该用加密还是哈希，这些是设计问题，API 文档不会告诉你。
