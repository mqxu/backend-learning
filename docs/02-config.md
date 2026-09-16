# 02 · 配置管理 —— 优雅地读取与管理配置

> - 难度：入门
> - 前置模块：01-quickstart
> - 预计时长：约 45 分钟
> - 对应代码：[02-config](https://github.com/mqxu/backend-learning/tree/main/02-config)

## 1. 本节导读

学完本节，你将掌握：

1. 用 `@Value` 读取单个配置，并会用默认值、占位符、随机值、SpEL；
2. 用 `@ConfigurationProperties` 把一组配置**类型安全**地绑定到对象；
3. 用多环境 profile 区分开发/生产配置；
4. 用 `@Validated` 在启动时校验配置合法性。

**前置知识**：会 `01-quickstart`；知道什么是 Bean 与依赖注入。

---

## 2. 概念铺垫

### 2.1 配置应该放哪，为什么要「外部化」

把端口、数据库地址、密钥等写死在代码里，换个环境就得改代码、重新打包。Spring Boot 的**外部化配置**把配置放到代码之外（`application.yml`、环境变量、命令行参数等），改配置不用改代码。

### 2.2 配置的优先级

同一项配置出现在多个地方时，Spring Boot 有严格的覆盖顺序（越高越优先）：

1. 命令行参数（`--server.port=9000`）
2. Java 系统属性
3. 操作系统环境变量
4. `application-{profile}.yml`（环境专属）
5. `application.yml`（通用）

> 记忆点：**越「外面」、越「临时」的越优先**。所以 `--spring.profiles.active=prod` 能覆盖 yml 里的 `active: dev`。

### 2.3 为什么官方推荐 `@ConfigurationProperties` 而非 `@Value`

`@Value` 只能一个个注入标量值；配置一多、结构一复杂就非常啰嗦。Spring Boot 官方文档明确建议：**成组的、结构化的配置用 `@ConfigurationProperties` 绑定到对象**，只有零散的单个值才用 `@Value`。

```yaml
student:
  name: 张三
  age: 18
  hobbies: [篮球, 编程]
```

用 `@Value` 要写 3 行，用 `@ConfigurationProperties(prefix = "student")` 一行搞定整组。

### 2.4 什么是 profile

profile 是「环境」的代号（`dev` / `prod`）。同一个应用，开发时连本地库、生产时连线上库，这两份差异写进 `application-dev.yml`、`application-prod.yml`，靠一个开关切换。

---

## 3. 环境准备

无需外部服务。本模块比 `01` 多一个依赖（用于配置校验）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

它引入 `jakarta.validation`（JSR-380），提供 `@NotBlank`、`@Min` 等校验注解。

---

## 4. 动手实战

### 步骤 1：主配置文件 `application.yml`

目标：写出通用配置与结构化配置。

```yaml
server:
  port: 8002

spring:
  application:
    name: 02-配置管理
  profiles:
    active: dev          # 默认激活 dev

mqxu:
  name: 许莫淇
  job: 教师

# 供 @ConfigurationProperties 绑定的结构化配置
student:
  name: 张三
  age: 18
  hobbies:
    - 篮球
    - 编程
  scores:
    chinese: 90
    math: 95
  address:
    province: 江苏省
    city: 南京市
  courses:
    - name: 高等数学
      credit: 4
    - name: 大学英语
      credit: 3

# 供 @Validated 校验的配置
app:
  name: 配置管理模块
  author: ${mqxu.name}    # 占位符：引用其它配置项
  port: 8002
  max-count: 100
```

讲解：

- `student` 段演示了 YAML 的四种结构：标量、List、Map（`scores`）、嵌套对象；
- `author: ${mqxu.name}` 是**占位符引用**，运行时会被替换成「许莫淇」。

### 步骤 2：`@ConfigurationProperties` 类型安全绑定

目标：把 `student.*` 一次绑定到对象。

`src/main/java/top/mqxu/config/properties/StudentProperties.java`：

```java
package top.mqxu.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 学生配置
 *
 * @author mqxu
 */
@Data
@Component
@ConfigurationProperties(prefix = "student")
public class StudentProperties {

    private String name;

    private Integer age;

    /** 简单 List */
    private List<String> hobbies;

    /** Map */
    private Map<String, Integer> scores;

    /** 嵌套对象 */
    private Address address;

    /** List 嵌套对象 */
    private List<Course> courses;

    @Data
    public static class Address {
        private String province;
        private String city;
    }

    @Data
    public static class Course {
        private String name;
        private Integer credit;
    }
}
```

讲解：

- `@Component` 把它注册成 Bean；`@ConfigurationProperties(prefix = "student")` 声明「把 student 前缀下的配置按字段名绑定进来」；
- 绑定是**宽松绑定**：yml 里的 `max-count` 可对应 Java 的 `maxCount`；
- 嵌套静态类 `Address`、`Course` 同样用 `@Data` 生成 setter，绑定器靠 setter 赋值。

### 步骤 3：`@Value` 进阶用法

目标：演示 `@Value` 的四种形态。这里同时展示**构造器注入**。

`src/main/java/top/mqxu/config/controller/ConfigController.java`：

```java
package top.mqxu.config.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.config.properties.AppProperties;
import top.mqxu.config.service.EnvService;

/**
 * 配置管理接口
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final AppProperties appProperties;
    private final EnvService envService;

    @Value("${server.port}")
    private Integer serverPort;

    @Value("${mqxu.name}")
    private String myName;

    // 占位符引用：app.author 引用了 mqxu.name
    @Value("${app.author}")
    private String author;

    // 默认值：app.remark 未配置时用冒号后的值兜底
    @Value("${app.remark:暂无备注}")
    private String remark;

    // 随机值
    @Value("${random.uuid}")
    private String randomUuid;

    @Value("${random.int(1,100)}")
    private Integer randomInt;

    // SpEL：先解析 ${student.age}，再算三元表达式
    @Value("#{${student.age} >= 18 ? '成年' : '未成年'}")
    private String adult;

    @GetMapping("/value")
    public String getValueCases() {
        return "author=" + author
                + "；remark=" + remark
                + "；randomUuid=" + randomUuid
                + "；randomInt=" + randomInt
                + "；adult=" + adult;
    }

    @GetMapping("/app")
    public AppProperties getApp() {
        return appProperties;
    }

    @GetMapping("/env")
    public String getEnv() {
        return envService.envInfo();
    }
}
```

讲解：

- `@RequiredArgsConstructor` 会为**所有 `final` 字段**生成构造器，Spring 用构造器注入 `AppProperties`、`EnvService`。这是官方推荐的注入方式——**依赖不可变（`final`），且不会出现「字段注入下对象还没初始化就被用」的问题**；
- `@Value` 四种形态：
  - `${app.author}` 占位符引用；
  - `${app.remark:暂无备注}` 带默认值（key 不存在也不报错）；
  - `${random.uuid}` / `${random.int(1,100)}` 随机值；
  - `#{${student.age} >= 18 ? '成年' : '未成年'}` SpEL 表达式。

> **规范点**：`@Value` 多用于零散标量；成组配置走 `@ConfigurationProperties`，别本末倒置。

### 步骤 4：多环境 profile 与 `@Profile`

目标：让同一份代码在不同环境表现不同。

`application-dev.yml`：

```yaml
env:
  name: dev
  description: 开发环境
```

`application-prod.yml`：

```yaml
env:
  name: prod
  description: 生产环境
```

再定义环境专属的 Bean：

```java
public interface EnvService {
    String envInfo();
}
```

```java
@Service
@Profile("dev")
public class DevEnvService implements EnvService {
    @Override
    public String envInfo() {
        return "我是 dev 环境专属的 Bean";
    }
}
```

```java
@Service
@Profile("prod")
public class ProdEnvService implements EnvService {
    @Override
    public String envInfo() {
        return "我是 prod 环境专属的 Bean";
    }
}
```

讲解：

- `@Profile("dev")` 表示「仅当激活了 dev 时才创建这个 Bean」；
- 激活 `dev` 时容器里只有 `DevEnvService`，`ConfigController` 注入的 `EnvService` 就是它；切到 `prod` 则换成 `ProdEnvService`。

> **规范点**：覆写接口方法必须加 `@Override`（阿里手册强制项）。

### 步骤 5：`@Validated` 配置校验

目标：把非法配置挡在启动阶段。

`src/main/java/top/mqxu/config/properties/AppProperties.java`：

```java
package top.mqxu.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 应用配置（带校验）
 *
 * @author mqxu
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotBlank(message = "应用名称 app.name 不能为空")
    private String name;

    private String author;

    @Min(value = 1, message = "app.port 必须大于等于 1")
    @Max(value = 65535, message = "app.port 必须小于等于 65535")
    private Integer port;

    @Min(1)
    @Max(1000)
    private Integer maxCount;
}
```

讲解：

- `@Validated` 开启校验；`@NotBlank`、`@Min`、`@Max` 来自 JSR-380；
- 把 `app.port` 改成 `99999` 再启动，应用会直接报错、拒绝启动，避免坏配置流入运行期。

---

## 5. 完整代码与目录结构

```
02-config
└── src/main
    ├── java/top/mqxu/config
    │   ├── ConfigApplication.java
    │   ├── controller
    │   │   ├── ConfigController.java
    │   │   └── StudentController.java
    │   ├── properties
    │   │   ├── StudentProperties.java
    │   │   └── AppProperties.java
    │   └── service
    │       ├── EnvService.java
    │       ├── DevEnvService.java
    │       └── ProdEnvService.java
    └── resources
        ├── application.yml
        ├── application-dev.yml
        └── application-prod.yml
```

---

## 6. 运行与验证

```bash
mvn -pl 02-config -am spring-boot:run
```

| 接口 | 期望现象 |
| --- | --- |
| `GET /student/info` | 返回完整的 student JSON（含 hobbies、scores、address、courses） |
| `GET /config/value` | 打印占位符引用、默认值、随机 UUID、随机整数、SpEL 结果 |
| `GET /config/app` | 返回 `app` 配置对象 |
| `GET /config/env` | 输出「我是 dev 环境专属的 Bean」 |

**切换环境验证**：

```bash
mvn -pl 02-config -am spring-boot:run \
  -Dspring-boot.run.arguments=--spring.profiles.active=prod
```

再次访问 `/config/env`，应输出「我是 prod 环境专属的 Bean」。

**校验验证**：把 `application.yml` 里 `app.port` 改成 `99999`，启动会报「app.port 必须小于等于 65535」。

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| `@ConfigurationProperties` 绑定后字段全是 null | 前缀写错，或类没加 `@Component` 注册成 Bean | 核对 `prefix` 与 yml 层级一致，补 `@Component` |
| `@Value` 报 `Could not resolve placeholder` | key 不存在且没给默认值 | 加 `:默认值`，或补上配置 |
| 改了 `application-dev.yml` 不生效 | 没激活 dev | 确认 `spring.profiles.active=dev` |
| 注入的 `EnvService` 报 `NoSuchBeanDefinition` | 激活的 profile 没有对应实现 | 确保 active 的值有对应的 `@Profile` Bean |
| 校验没生效 | 没加 `@Validated` | 属性类上补 `@Validated` |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

> 跟着做即可，做完立刻用 curl 验证，追求「当场跑通」。

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 给 `student` 加 `Map<String, String> extras` | `/student/info` 能看到 `extras` |
| 2 | 把 `mqxu.name` 改成你自己的名字 | `/config/value` 中 `author` 跟着变（占位符引用） |
| 3 | 用 `--spring.profiles.active=prod` 重启 | `/config/env` 变成 prod 实现 |

### 8.2 课后练习（课后完成并提交）

- **基础**：把 `ConfigController` 里零散的 `mqxu.name`/`mqxu.job` 改造成一个 `@ConfigurationProperties` 类。
- **进阶**：新增 `application-test.yml` 与 `@Profile("test")` 实现，完成 dev/test/prod 三环境切换。
- **挑战**：给 `AppProperties` 加一个带 `@Email` 校验的 `email` 字段，验证填非法邮箱时应用启动失败。

---

## 9. 小结与知识地图

| 知识点 | 什么时候用 |
| --- | --- |
| `@Value` | 零散单个值，需要默认值/随机值/SpEL |
| `@ConfigurationProperties` | 成组、结构化配置 |
| profile | 区分开发/测试/生产环境 |
| `@Validated` | 启动时校验配置，防非法值 |

下一节 [03-logging](03-logging.md) 会讲日志，你将学会用 SLF4J 规范地记录日志、用 MDC 追踪一次请求的完整链路。

> 本节遵循的规范（官方 + 阿里手册）：成组配置优先 `@ConfigurationProperties`；构造器注入（`@RequiredArgsConstructor`）；覆写方法加 `@Override`；POJO 属性用包装类型；配置校验用 JSR-380。说明：Service 接口本模块按 Spring 官方风格命名 `EnvService`（不加 `I` 前缀）；若项目约定阿里风格可写作 `IEnvService`，二者取其一并保持一致即可。
