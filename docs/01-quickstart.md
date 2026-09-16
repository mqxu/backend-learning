# 01 · 快速入门 —— 从零启动第一个 Spring Boot 应用

> - 难度：入门
> - 前置模块：无
> - 预计时长：约 30 分钟
> - 对应代码：[01-quickstart](../01-quickstart)

## 1. 本节导读

学完本节，你将能独立完成三件事：

1. 用 Maven 建出一个最小的 Spring Boot 模块骨架；
2. 启动应用，并写一个返回 JSON 的 REST 接口；
3. 说清「启动类、控制器、实体类」三者的分工。

**前置知识**：会一点 Java 语法（类、方法、包）即可，不要求会 Spring。

---

## 2. 概念铺垫

### 2.1 什么是 Spring Boot

普通 Java Web 项目要配一堆东西：Tomcat 容器、一堆 XML、各种依赖版本。Spring Boot 的理念是**「约定优于配置」**：

- 内置 Tomcat，`main` 方法一跑就是一个 Web 服务，不用自己部署到服务器；
- 自动装配（Auto Configuration）：根据你引入的依赖自动配置好组件，少写甚至不写配置；
- 依赖版本统一管理：父 POM 锁定版本，子模块不用写版本号。

### 2.2 什么是 REST 接口

浏览器访问 `http://localhost:8001/user/hello`，服务器返回一段文字或 JSON，这就是一个 REST 接口。它的核心是「用 URL 定位资源，用 HTTP 方法表达操作」：

| 方法 | 含义 | 示例 |
| --- | --- | --- |
| GET | 查询 | `GET /user/info` |
| POST | 新增 | `POST /user` |
| PUT | 更新 | `PUT /user/1001` |
| DELETE | 删除 | `DELETE /user/1001` |

本节只用最简单的 GET。

### 2.3 三个关键词：Bean、注入、容器

这三个词后面会反复出现，先有个印象：

- **容器**：Spring 启动后维护的一个「对象仓库」，帮你管理对象从创建到销毁的一生；
- **Bean**：放进容器、由 Spring 管理的对象就叫 Bean；
- **注入**：你不在代码里 `new`，而是让 Spring 把建好的对象「塞」给你，这就是依赖注入（DI）。

```java
// 不用自己 new，Spring 会帮你把 User 造好 —— 这就是「控制反转」
@RestController
public class UserController {
    // 里面直接 return new User(...)，暂时先 new，后面模块会讲注入
}
```

> 本节为了不吓到新手，控制器里先直接 `new` 一个对象返回；注入会在 `02-config` 正式登场。

---

## 3. 环境准备

| 项目 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 父 POM 里 `maven.compiler.source/target` 已设为 21 |
| Maven | 3.8+ | 用于构建 |
| IDE | IDEA | 可选，命令行也能跑 |

本仓库是**多模块 Maven 项目**，根目录 `pom.xml` 是父 POM，它统一了依赖版本，并声明了所有子模块。子模块 `01-quickstart/pom.xml` 只写自己的坐标，其余继承父 POM。

父 POM 已经为所有模块引入了：

- `spring-boot-starter-webmvc`：Web 能力（内置 Tomcat、Spring MVC）
- `spring-boot-devtools`：热部署，改代码自动重启（开发期用）
- `lombok`：用注解省去 getter/setter 等样板代码

> 本模块无需额外加依赖，直接继承即可。

---

## 4. 动手实战

我们从零搭出 `01-quickstart`，共 5 步。

### 步骤 1：建模块骨架

目标：让 Maven 认识这个子模块。

`01-quickstart/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承父 POM，获得统一依赖与插件 -->
    <parent>
        <groupId>top.mqxu</groupId>
        <artifactId>backend-learning</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>01-quickstart</artifactId>
</project>
```

讲解：

- `<parent>` 指向父 POM，`groupId/version` 都不用重复声明；
- 子模块只有唯一的 `artifactId`，这就是「约定优于配置」在构建上的体现。

验证：在仓库根目录执行 `mvn -pl 01-quickstart -am compile` 能通过（`-pl` 指定模块，`-am` 连带编译它依赖的模块）。

### 步骤 2：写启动类

目标：提供一个能启动整个应用的入口。

`src/main/java/top/mqxu/quickstart/QuickStartApplication.java`：

```java
package top.mqxu.quickstart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类
 *
 * @author mqxu
 */
@SpringBootApplication
public class QuickStartApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuickStartApplication.class, args);
    }
}
```

讲解：

- `@SpringBootApplication` 是一个**组合注解**，等于同时开启三件事：
  - `@Configuration`：本类可以声明配置；
  - `@EnableAutoConfiguration`：自动装配，根据依赖自动配置；
  - `@ComponentScan`：扫描当前包及其子包，把带注解的类注册成 Bean。
- `SpringApplication.run(...)` 启动内嵌 Tomcat 并加载整个容器。

> 注意启动类必须放在**包的最外层**（`top.mqxu.quickstart`），这样组件扫描才能覆盖到 `controller`、`service` 等子包。

### 步骤 3：写实体类 User

目标：定义一个「用户」模型，用 Lombok 省掉样板代码。

`src/main/java/top/mqxu/quickstart/entity/User.java`：

```java
package top.mqxu.quickstart.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 用户实体
 *
 * @author mqxu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String name;
    private LocalDate birthday;
}
```

讲解：

- `@Data`：自动生成 getter / setter / `toString` / `equals` / `hashCode`；
- `@NoArgsConstructor` / `@AllArgsConstructor`：生成无参 / 全参构造器；
- **规范点**：`id` 用包装类型 `Long` 而非基本类型 `long`——POJO 属性统一用包装类型，能区分「没赋值」和「值为 0」。

验证：编译通过，无需运行。

### 步骤 4：写控制器 UserController

目标：暴露两个 GET 接口。

`src/main/java/top/mqxu/quickstart/controller/UserController.java`：

```java
package top.mqxu.quickstart.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.quickstart.entity.User;

import java.time.LocalDate;

/**
 * 用户接口
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/hello")
    public String getHello() {
        return "hello world";
    }

    @GetMapping("/info")
    public User getUserInfo() {
        return new User(1001L, "张三", LocalDate.of(2005, 10, 24));
    }
}
```

讲解：

- `@RestController` = `@Controller` + `@ResponseBody`，意思是「方法的返回值直接写进响应体」，而不是去跳转页面；
- `@RequestMapping("/user")` 是类级前缀，`@GetMapping("/hello")` 是方法级子路径，拼起来就是 `GET /user/hello`；
- 返回 `User` 对象时，Spring 会自动用 Jackson 把它转成 JSON。

> **规范点**：接口方法应为 `public`。如果看到仓库里的 `getUserInfo` 写成了 `private`，那是待修正点——Spring 虽能通过代理反射调用，但按 Java 与 Spring 规范，对外暴露的方法必须 `public`。

### 步骤 5：配置端口

目标：让应用监听 8001 端口。

`src/main/resources/application.yml`：

```yaml
server:
  port: 8001
```

讲解：Spring Boot 默认端口 8080，这里改为 8001（本课程各模块端口按序号 8001、8002… 排布，避免冲突）。

验证：启动后浏览器/curl 访问 8001 端口。

---

## 5. 完整代码与目录结构

```
01-quickstart
└── src/main
    ├── java/top/mqxu/quickstart
    │   ├── QuickStartApplication.java   # 启动类
    │   ├── controller/UserController.java  # 控制器
    │   └── entity/User.java             # 实体
    └── resources/application.yml        # 配置
```

---

## 6. 运行与验证

在仓库根目录执行：

```bash
mvn -pl 01-quickstart -am spring-boot:run
```

看到 `Tomcat started on port 8001` 即启动成功，然后验证：

```bash
# 返回字符串
curl http://localhost:8001/user/hello
# 期望输出：hello world

# 返回 JSON
curl http://localhost:8001/user/info
# 期望输出：{"id":1001,"name":"张三","birthday":"2005-10-24"}
```

> `birthday` 输出为 `2005-10-24`：Spring Boot 自动注册了 Java 8 时间模块，`LocalDate` 默认按 ISO-8601 序列化。

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| `Whitelabel Error Page` 404 | 路径写错或没加 `@RestController` | 检查 `@RequestMapping` 前缀与方法路径拼接 |
| 端口被占用 | 8001 已起过服务 | 换端口，或 `lsof -i:8001` 找到并结束进程 |
| 返回的不是 JSON 而是报错 | 实体类缺 getter（没加 `@Data`） | Jackson 靠 getter 序列化，补 `@Data` |
| 启动报「无法解析主类」 | 启动类包名与目录不一致 | 确保类在 `top.mqxu.quickstart` 下 |
| 改了代码没生效 | devtools 未生效 / 没重新编译 | 重启，或确认 IDE 开了自动编译 |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

> 跟着做即可，做完立刻用 curl 验证，追求「当场跑通」。

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 给 `User` 加 `email` 字段，并让 `/user/info` 返回它 | `curl /user/info` 能看到 `"email":...` |
| 2 | 新增 `GET /user/hello?name=张三`，用 `@RequestParam` 接收，返回 `hello, 张三` | 带参访问返回正确拼接 |
| 3 | 重启后确认服务仍监听 8001 | `curl /user/hello` 返回 `hello world` |

### 8.2 课后练习（课后完成并提交）

- **基础**：给 `User` 增加 `email`、`phone` 两个字段，并让 `/user/info` 完整返回。
- **进阶**：实现 `GET /user/{id}`，用 `@PathVariable` 按 id 返回不同用户（提示：`new User(id, ...)`）。
- **挑战**：写一个返回 `List<User>` 的 `/user/list`，观察 JSON 数组格式；并写一段话说明 `@RestController` 与 `@Controller` 的区别及各自适用场景。

---

## 9. 小结与知识地图

| 概念 | 一句话回顾 |
| --- | --- |
| Spring Boot | 约定优于配置，内置容器，自动装配 |
| 启动类 | `@SpringBootApplication` 开启自动装配 + 组件扫描 |
| `@RestController` | 方法返回值直接作为响应体（JSON） |
| 实体类 + Lombok | `@Data` 省样板代码，属性用包装类型 |

下一节 [02-config](02-config.md) 会讲配置读取，同时正式引入**依赖注入**——控制器将不再 `new` 对象，而是让 Spring 注入。

> 本节遵循的规范（官方 + 阿里手册）：POJO 属性用包装类型；类名 UpperCamelCase、方法/变量 lowerCamelCase；对外方法 `public`；类头带 `@author` 注释。
