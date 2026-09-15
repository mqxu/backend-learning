# 快速入门学习模块

Spring Boot 最小可运行示例，演示一个后端模块的基本骨架：启动类、REST 接口、实体类与配置文件。

## 目录结构

```
01-quickstart
└── src/main
    ├── java/top/mqxu/quickstart
    │   ├── QuickStartApplication.java   # 启动类
    │   ├── controller
    │   │   └── UserController.java      # REST 接口
    │   └── entity
    │       └── User.java                # 实体类
    └── resources
        └── application.yml              # 配置文件（端口 8001）
```

## 各部分说明

### 1. 启动类

`@SpringBootApplication` 是一个组合注解，等于 `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`：

```java
@SpringBootApplication
public class QuickStartApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuickStartApplication.class, args);
    }
}
```

`main` 方法里调用 `SpringApplication.run(...)` 会启动内嵌的 Web 容器并加载整个 Spring 上下文。

### 2. 实体类

`User` 使用 Lombok 简化样板代码：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String name;
    private LocalDate birthday;
}
```

- `@Data`：自动生成 getter / setter / toString / equals / hashCode
- `@NoArgsConstructor` / `@AllArgsConstructor`：生成无参 / 全参构造器

### 3. REST 接口

`@RestController` 表示返回内容直接作为响应体（JSON/字符串），`@RequestMapping` 定义类级别的路径前缀，`@GetMapping` 绑定具体的 HTTP 方法与子路径：

```java
@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/info")
    private User getUserInfo() {
        return new User(1001L, "张三", LocalDate.of(2005, 10, 24));
    }

    @GetMapping("/hello")
    public String getHello() {
        return "hello world";
    }
}
```

> 注意：`/user/info` 的方法被写成了 `private`，Spring 仍能通过代理调用，但
> 约定上建议接口方法使用 `public`。

## 运行与验证

在仓库根目录执行：

```bash
mvn -pl 01-quickstart -am spring-boot:run
```

默认端口为 `8001`，验证接口：

| 请求 | 返回 |
| --- | --- |
| `GET /user/hello` | `hello world` |
| `GET /user/info` | `{"id":1001,"name":"张三","birthday":"2005-10-24"}` |

## 小结

本模块只做了三件事：一个启动类拉起服务、一个实体类建模数据、一个控制器暴露接口，
是后续所有模块（配置管理、日志、数据库……）的最小地基。
