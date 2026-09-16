# 12-test · 测试

> 状态：🚧 骨架已创建，核心案例待开发

质量保障。

## 学习目标

- JUnit5 单元测试
- MockMvc 接口测试、覆盖率

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| JUnit5 | `@Test` / `@BeforeEach` / `@ParameterizedTest` |
| MockMvc | 模拟请求与断言 |
| `@SpringBootTest` | 集成测试 |
| 覆盖率 | jacoco |

## 目录结构

```
12-test
└── src/main
    ├── java/top/mqxu/test
    │   ├── TestApplication.java
    │   └── controller/TestController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 12-test -am spring-boot:run
```

端口：`8012`

## 待引入依赖

- 无 —— `spring-boot-starter-webmvc-test`（父 POM 已含）
