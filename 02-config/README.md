# 配置管理学习模块
> 📖 详细教程：[docs/02-config.md](../docs/02-config.md)

演示 Spring Boot 中常见的配置管理用法，涵盖 5 个实用案例。

## 目录结构

```
02-config
└── src/main
    ├── java/top/mqxu/config
    │   ├── ConfigApplication.java          # 启动类
    │   ├── controller
    │   │   ├── ConfigController.java       # @Value / 随机值 / SpEL / 多环境 / @Profile
    │   │   └── StudentController.java      # @ConfigurationProperties 绑定
    │   ├── properties
    │   │   ├── StudentProperties.java      # 案例 1：类型安全绑定
    │   │   └── AppProperties.java          # 案例 4：配置校验
    │   └── service
    │       ├── EnvService.java             # 案例 2：@Profile 接口
    │       ├── DevEnvService.java          # dev 实现
    │       └── ProdEnvService.java         # prod 实现
    └── resources
        ├── application.yml                 # 主配置（所有环境共享）
        ├── application-dev.yml             # 开发环境
        └── application-prod.yml            # 生产环境
```

## 案例 1：`@ConfigurationProperties` 类型安全绑定

用 `@Value` 一个个注入字段，配置一多就很啰嗦。`@ConfigurationProperties(prefix = "...")`
可以把**同一前缀**的一组配置一次性绑定到一个 POJO 上，并支持复杂结构：

- 简单字段：`student.name`、`student.age`
- List：`student.hobbies`
- Map：`student.scores`
- 嵌套对象：`student.address`
- List 嵌套对象：`student.courses`

```java
@Data
@Component
@ConfigurationProperties(prefix = "student")
public class StudentProperties {
    private String name;
    private List<String> hobbies;
    private Map<String, Integer> scores;
    private Address address;
    private List<Course> courses;
    // 内部静态类 Address、Course ...
}
```

> 约定：`@Component` 注册 Bean，`@ConfigurationProperties` 声明绑定前缀；也可以用
> `@ConfigurationPropertiesScan` / `@EnableConfigurationProperties` 替代 `@Component`。

验证：`GET /student/info`

## 案例 2：多环境配置与 `@Profile`

### 多环境配置文件

- 通用配置写在 `application.yml`
- 环境差异写在 `application-{profile}.yml`（如 `application-dev.yml`）
- 通过 `spring.profiles.active` 指定当前环境，命令行 `--spring.profiles.active=prod` 可覆盖

不同环境下读取 `env.name`、`env.description` 会得到不同结果。

### `@Profile` 注解

同一个 `EnvService` 接口，在不同环境注入不同实现：

```java
@Service
@Profile("dev")
public class DevEnvService implements EnvService { ... }

@Service
@Profile("prod")
public class ProdEnvService implements EnvService { ... }
```

激活 `dev` 时容器里只有 `DevEnvService`，激活 `prod` 时只有 `ProdEnvService`。

验证：`GET /config/env`（默认激活 dev，可用 `--spring.profiles.active=prod` 启动后重看）

## 案例 3：`@Value` 的进阶用法

在 `ConfigController` 中演示了 `@Value` 的四种常见形态：

| 形态 | 写法 | 说明 |
| --- | --- | --- |
| 占位符引用 | `${app.author}` | yml 里 `author: ${mqxu.name}` 引用其他配置 |
| 默认值 | `${app.remark:暂无备注}` | key 不存在时用默认值兜底 |
| 随机值 | `${random.uuid}`、`${random.int(1,100)}` | 启动时生成随机值 |
| SpEL | `#{${student.age} >= 18 ? '成年' : '未成年'}` | 先解析占位符再算表达式 |

验证：`GET /config/value`

## 案例 4：配置校验 `@Validated`

在属性类上加 `@Validated` + JSR-303 注解（`@NotBlank`、`@Min`、`@Max` 等），
应用启动时会校验配置值，不合法直接报错阻止启动，避免错误配置流入运行期。

```java
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    @NotBlank(message = "应用名称 app.name 不能为空")
    private String name;
    @Min(1) @Max(65535)
    private Integer port;
    @Min(1) @Max(1000)
    private Integer maxCount;   // 宽松绑定：max-count -> maxCount
}
```

验证：`GET /config/app`；把 `app.port` 改成 `99999` 再启动，会看到启动失败并提示校验错误。

## 小结

| 案例 | 使用场景 |
| --- | --- |
| `@ConfigurationProperties` | 一组同前缀、结构复杂的配置 |
| 多环境 + `@Profile` | 开发 / 测试 / 生产环境差异 |
| `@Value` 进阶 | 零散字段、默认值、随机值、动态表达式 |
| 配置校验 | 启动时兜底，防止非法配置 |
