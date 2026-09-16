# 后端工程化多模块学习仓库

基于 Spring Boot 4.x 的「后端工程化开发」课程配套代码，按模块循序渐进，覆盖从「快速入门」到「部署监控」的完整后端工程链路。每个模块都可独立运行，并配有独立的 `README.md` 说明。

## 技术栈

- 语言：Java 21
- 框架：Spring Boot 4.x（父 POM 统一管理版本）
- 构建：Maven 多模块
- 常用：Lombok、MyBatis-Plus、Redis、RabbitMQ、Spring Security、JWT、OpenAPI、Docker

## 模块导航

| 序号 | 模块 | 主题 | 状态 | 教程 |
| --- | --- | --- | --- | --- |
| 01 | [01-quickstart](01-quickstart/README.md) | 快速入门 | ✅ 完成 | [教程](docs/01-quickstart.md) |
| 02 | [02-config](02-config/README.md) | 配置管理 | ✅ 完成 | [教程](docs/02-config.md) |
| 03 | [03-logging](03-logging/README.md) | 日志管理 | 🚧 骨架 | [教程](docs/03-logging.md) |
| 04 | [04-web](04-web/README.md) | Web 开发进阶 | 🚧 骨架 | [教程](docs/04-web.md) |
| 05 | [05-mybatis](05-mybatis/README.md) | 数据访问 | 🚧 骨架 | [教程](docs/05-mybatis.md) |
| 06 | [06-redis](06-redis/README.md) | 缓存与分布式锁 | 🚧 骨架 | [教程](docs/06-redis.md) |
| 07 | [07-mq](07-mq/README.md) | 消息队列 | 🚧 骨架 | [教程](docs/07-mq.md) |
| 08 | [08-schedule](08-schedule/README.md) | 定时任务 | 🚧 骨架 | [教程](docs/08-schedule.md) |
| 09 | [09-security](09-security/README.md) | 认证与授权 | 🚧 骨架 | [教程](docs/09-security.md) |
| 10 | [10-file](10-file/README.md) | 文件上传下载 | 🚧 骨架 | [教程](docs/10-file.md) |
| 11 | [11-doc](11-doc/README.md) | 接口文档 | 🚧 骨架 | [教程](docs/11-doc.md) |
| 12 | [12-test](12-test/README.md) | 测试 | 🚧 骨架 | [教程](docs/12-test.md) |
| 13 | [13-actuator](13-actuator/README.md) | 应用监控 | 🚧 骨架 | [教程](docs/13-actuator.md) |
| 14 | [14-deploy](14-deploy/README.md) | 部署与 CI/CD | 🚧 骨架 | [教程](docs/14-deploy.md) |

## 模块详解

| 模块 | 学习目标 |
| --- | --- |
| **01-quickstart** | 建立 Spring Boot 最小骨架：启动类、REST 接口、实体类、Lombok 简化样板代码 |
| **02-config** | 掌握配置读取：`@Value` 进阶、`@ConfigurationProperties` 类型安全绑定、多环境 profile、配置校验 |
| **03-logging** | 规范日志输出：日志级别、格式、MDC 链路追踪、日志脱敏与按环境分文件 |
| **04-web** | 工程化接口：RESTful 规范、参数校验、统一响应体 `Result<T>`、全局异常处理、拦截器与过滤器 |
| **05-mybatis** | 数据持久化：MyBatis-Plus 增删改查、分页、事务、连接池与代码生成 |
| **06-redis** | 高性能缓存：缓存抽象、缓存三大问题防护、Redisson 分布式锁 |
| **07-mq** | 异步解耦：RabbitMQ 生产/消费、消息可靠性、延迟队列、削峰 |
| **08-schedule** | 定时任务：`@Scheduled`、Quartz、动态任务与分布式调度 |
| **09-security** | 安全防护：Spring Security + JWT、RBAC 权限模型、接口鉴权 |
| **10-file** | 文件能力：上传下载、分片断点续传、对接 MinIO/OSS |
| **11-doc** | 接口文档：OpenAPI/Swagger、knife4j 在线调试 |
| **12-test** | 质量保障：JUnit5、MockMvc、集成测试与覆盖率 |
| **13-actuator** | 可观测性：健康检查、指标采集、Prometheus 对接 |
| **14-deploy** | 交付上线：Docker 镜像、Compose 编排、CI/CD 流水线 |

## 目录结构

```
backend-learning
├── pom.xml                 # 父 POM：统一依赖版本与插件
├── README.md               # 本文件
├── docs/                   # 各模块详细教程
├── 01-quickstart/          # 快速入门
├── 02-config/              # 配置管理
├── 03-logging/             # 日志管理
├── 04-web/                 # Web 开发进阶
├── 05-mybatis/             # 数据访问
├── 06-redis/               # 缓存与分布式锁
├── 07-mq/                  # 消息队列
├── 08-schedule/            # 定时任务
├── 09-security/            # 认证与授权
├── 10-file/                # 文件上传下载
├── 11-doc/                 # 接口文档
├── 12-test/                # 测试
├── 13-actuator/            # 应用监控
└── 14-deploy/              # 部署与 CI/CD
```

## 快速开始

```bash
# 编译整个仓库
mvn clean install

# 运行指定模块（以 01-quickstart 为例）
mvn -pl 01-quickstart -am spring-boot:run
```

各模块默认端口按模块序号规划（`8001`、`8002` …），避免冲突。

## 约定与规范

- **命名**：模块名 `NN-主题`，包名 `top.mqxu.<主题>`（如 `top.mqxu.config`）
- **提交**：遵循 Conventional Commits（`feat:` / `docs:` / `fix:` 等 + 中文描述）
- **注释**：类头统一 `@author` / `@date` / `@description`
- **响应**：从 `04-web` 起统一返回结构 `Result<T>`，全局异常兜底

## 学习路径

建议按序号顺序学习：先 `01` 跑通最小工程 → `02` 掌握配置 → `03/04` 打好日志与接口工程化基础 → `05/06/07/08` 打通数据与中间件 → `09/10` 补齐安全与文件能力 → `11/12/13/14` 收口文档、测试、监控与部署。
