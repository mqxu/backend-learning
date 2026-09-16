# 11-doc · 接口文档
> 📖 详细教程：[docs/11-doc.md](../docs/11-doc.md)

> 状态：🚧 骨架已创建，核心案例待开发

自动生成在线接口文档。

## 学习目标

- OpenAPI / Swagger 注解
- knife4j 在线调试

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| OpenAPI | `@Operation` / `@Parameter` |
| knife4j | UI 界面、接口调试 |
| 文档分组 | 按模块分组 |

## 目录结构

```
11-doc
└── src/main
    ├── java/top/mqxu/doc
    │   ├── DocApplication.java
    │   └── controller/DocController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 11-doc -am spring-boot:run
```

端口：`8011`

## 待引入依赖

- `springdoc-openapi-starter-webmvc-ui`
- `knife4j-openapi3-jakarta-spring-boot-starter`
