# 11 · 接口文档 —— 用 OpenAPI 自动生成文档

> - 难度：进阶
> - 前置模块：04-web
> - 预计时长：约 30 分钟
> - 对应代码：[11-doc](../11-doc)

## 1. 本节导读

学完本节，你将能：

1. 用 springdoc + OpenAPI 注解描述接口；
2. 让文档自动生成，无需手写 Markdown；
3. 用 knife4j 提供可视化在线调试界面。

**前置知识**：会 `04-web`。

---

## 2. 概念铺垫

### 2.1 为什么用注解生成文档

手写接口文档最大的问题是**会过时**——代码改了文档没改。OpenAPI 的思路：**用注解把文档写在代码旁边**，工具扫描注解自动生成文档，代码和文档永不脱节。

### 2.2 常用注解

| 注解 | 作用 |
| --- | --- |
| `@Tag` | 给控制器分组 |
| `@Operation` | 描述一个接口 |
| `@Parameter` | 描述参数 |
| `@Schema` | 描述模型字段 |

---

## 3. 环境准备

依赖（版本以官方最新、兼容当前 Spring Boot 为准）：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
</dependency>
```

> springdoc 的 UI 访问 `/swagger-ui.html`；knife4j 的增强 UI 访问 `/doc.html`。

---

## 4. 动手实战

> 本模块当前是骨架，下面带你逐步补全文档能力。

### 步骤 1：给接口加注解

```java
package top.mqxu.doc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import top.mqxu.doc.dto.UserCreateRequest;

/**
 * 用户接口
 *
 * @author mqxu
 */
@Tag(name = "用户管理", description = "用户相关接口")
@RestController
@RequestMapping("/users")
public class UserController {

    @Operation(summary = "创建用户")
    @PostMapping
    public String create(@RequestBody UserCreateRequest request) {
        return "ok";
    }

    @Operation(summary = "查询用户")
    @GetMapping("/{id}")
    public String getById(
            @Parameter(description = "用户 ID") @PathVariable Long id) {
        return "user-" + id;
    }
}
```

DTO 描述字段：

```java
package top.mqxu.doc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建用户请求
 *
 * @author mqxu
 */
@Data
@Schema(description = "创建用户请求")
public class UserCreateRequest {

    @Schema(description = "用户名", example = "张三")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "年龄", example = "20")
    private Integer age;
}
```

### 步骤 2：文档全局信息

```java
package top.mqxu.doc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置
 *
 * @author mqxu
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("接口文档")
                        .version("1.0.0")
                        .description("自动生成的接口文档"));
    }
}
```

---

## 5. 完整代码与目录结构

```
11-doc
└── src/main
    ├── java/top/mqxu/doc
    │   ├── DocApplication.java
    │   ├── controller/UserController.java
    │   ├── dto/UserCreateRequest.java
    │   └── config/OpenApiConfig.java
    └── resources/application.yml
```

---

## 6. 运行与验证

```bash
mvn -pl 11-doc -am spring-boot:run
```

浏览器访问：

- `http://localhost:8011/swagger-ui.html` —— springdoc 原生 UI
- `http://localhost:8011/doc.html` —— knife4j 增强 UI（可直接在线调试）

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 访问 404 | 依赖没引入或版本不兼容 | 核对 springdoc 版本与 Spring Boot 兼容 |
| 字段没展示 | DTO 没加 `@Schema` | 给模型字段加注解 |
| 文档与代码不一致 | 没重新生成 | 文档是扫描注解实时生成，重启即可 |
| 生产不想暴露文档 | 默认放开 | 按 profile 控制是否启用 springdoc |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 给 `/users/{id}` 加 `@Operation` 描述 | 文档里能看到 |
| 2 | 给 DTO 字段加 `@Schema(example=...)` | 文档显示示例值 |
| 3 | 用 knife4j 在线调试创建用户接口 | 返回正确 |

### 8.2 课后练习（课后完成并提交）

- **基础**：给 `UserCreateRequest` 所有字段补 `@Schema` 描述与示例。
- **进阶**：给文档配置分组（`@Tag`），把不同控制器分到不同组。
- **挑战**：研究如何按环境（dev 开启 / prod 关闭）控制文档访问，写出配置。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| OpenAPI | 注解写文档，扫描生成，永不脱节 |
| `@Tag/@Operation/@Schema` | 分组 / 接口 / 字段 |
| springdoc / knife4j | 原生 UI / 增强 UI |

下一节 [12-test](12-test.md) 会讲测试，保证代码质量。

> 本节遵循的规范（官方 + 阿里手册）：接口用 `@Operation` 写清「做什么」；参数用 `@Parameter` 写清含义；文档按环境控制暴露。
