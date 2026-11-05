# 01-quickstart · 快速入门

> 📖 详细教程：[docs/01-quickstart.md](../docs/01-quickstart.md)

Spring Boot 最小可运行示例：启动类、REST 接口、实体类、配置文件。重点是把"请求怎么进来、对象怎么出去"讲清楚。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/user/hello` | 纯文本响应 |
| GET | `/user/info` | 返回对象（自动转 JSON） |
| GET | `/user/list?keyword=` | 列表查询，keyword 可选 |
| GET | `/user/{id}` | 路径参数，不存在返回 404 |
| POST | `/user` | JSON 请求体，服务端补 ID |

## 目录结构

```
01-quickstart
└── src/main
    ├── java/top/mqxu/quickstart
    │   ├── QuickStartApplication.java
    │   ├── controller/UserController.java
    │   └── entity/User.java
    └── resources/application.yml     # 端口 8001
```

## 运行

```bash
mvn -pl 01-quickstart spring-boot:run
node tools/run-cases.mjs 01-quickstart   # 跑接口并生成截图
```

## 要点

- `@SpringBootApplication` = 配置类 + 自动装配 + 组件扫描，控制器必须在启动类所在包或其子包下
- 返回值是 `String` 时响应 `text/plain`，是对象时响应 `application/json`
- 查不到用 `ResponseEntity.notFound()` 返回 404，不要返回 `null` 假装成功
