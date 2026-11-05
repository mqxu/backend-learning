# 后端工程化多模块学习仓库

基于 Spring Boot 4.x 的"后端工程化开发"课程配套代码，按模块循序渐进，覆盖从"快速入门"到"部署上线"的完整后端工程链路。每个模块都可独立运行，配有一篇自包含教程和一套真实运行的接口截图。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言与构建 | Java 21、Maven 多模块 |
| 框架 | Spring Boot 4.1（父 POM 统一版本） |
| 数据 | MySQL 8、MyBatis-Plus 3.5 |
| 中间件 | Redis 7、Redisson |
| 安全 | Spring Security 7、JWT |
| 文档 | springdoc-openapi 3 |
| 交付 | Docker、GitHub Actions |

## 模块导航

| 序号 | 模块 | 主题 | 状态 | 教程 |
| --- | --- | --- | --- | --- |
| 01 | [01-quickstart](01-quickstart/README.md) | 快速入门：REST 接口与参数绑定 | ✅ 完成 | [教程](docs/01-quickstart.md) |
| 02 | [02-config](02-config/README.md) | 配置管理：绑定、多环境、优先级 | ✅ 完成 | [教程](docs/02-config.md) |
| 03 | [03-logging](03-logging/README.md) | 日志管理：级别、MDC、脱敏 | ✅ 完成 | [教程](docs/03-logging.md) |
| 04 | [04-web](04-web/README.md) | Web 进阶：统一响应、异常、过滤器 | ✅ 完成 | [教程](docs/04-web.md) |
| 05 | [05-mybatis](05-mybatis/README.md) | 数据访问：CRUD、分页、事务、多表 | ✅ 完成 | [教程](docs/05-mybatis.md) |
| 06 | [06-redis](06-redis/README.md) | 缓存与分布式锁：五种类型、三大问题、Redisson | ✅ 完成 | [教程](docs/06-redis.md) |
| 07 | [07-schedule](07-schedule/README.md) | 定时任务：三种触发方式、动态任务 | ✅ 完成 | [教程](docs/07-schedule.md) |
| 08 | [08-security](08-security/README.md) | 认证与授权：JWT、RBAC、短信登录 | ✅ 完成 | [教程](docs/08-security.md) |
| 09 | [09-file](09-file/README.md) | 文件上传下载：三层校验、存储抽象 | ✅ 完成 | [教程](docs/09-file.md) |
| 10 | [10-doc](10-doc/README.md) | 接口文档：OpenAPI、springdoc、分组 | ✅ 完成 | [教程](docs/10-doc.md) |
| 11 | [11-test](11-test/README.md) | 测试：JUnit 5、Mockito、MockMvc、JaCoCo | ✅ 完成 | [教程](docs/11-test.md) |
| 12 | [12-actuator](12-actuator/README.md) | 应用监控：Actuator、Micrometer、Prometheus | ✅ 完成 | [教程](docs/12-actuator.md) |
| 13 | [13-deploy](13-deploy/README.md) | 部署与 CI/CD：镜像、Compose、Actions | ✅ 完成 | [教程](docs/13-deploy.md) |
| 14 | [14-toolkit](14-toolkit/README.md) | 工具库实战：验证码、短链、签名、分片上传 | ✅ 完成 | [教程](docs/14-toolkit.md) |
| 15-1 | [zhihu](15-cases/zhihu) | 综合案例：知乎问答社区（账号、问答、投票、动态流） | ✅ 完成 | [教程](docs/15-zhihu.md) |
| 15-2 | [task-commit](15-cases/task-commit) | 综合案例：任务提交与批改（三道关、越权防护） | ✅ 完成 | [教程](docs/15-task-commit.md) |
| 15-3 | [campus-activity](15-cases/campus-activity) | 综合案例：校园活动小程序（三端、状态机、签到） | ✅ 完成 | [教程](docs/15-campus-activity.md) |

## 各模块端口

| 模块 | 端口 | 外部依赖 |
| --- | --- | --- |
| 01-quickstart | 8001 | 无 |
| 02-config | 8002 | 无 |
| 03-logging | 8003 | 无 |
| 04-web | 8004 | 无 |
| 05-mybatis | 8005 | MySQL |
| 06-redis | 8006 | MySQL、Redis |
| 07-schedule | 8007 | 无（天气与邮件默认走模拟通道） |
| 08-security | 8008 | MySQL、Redis |
| 09-file | 8009 | 无（可选阿里云 OSS） |
| 10-doc | 8010 | 无 |
| 11-test | 8011 | 无 |
| 12-actuator | 8012 | 无 |
| 13-deploy | 8013 | 无（可选 Docker） |
| 14-toolkit | 8014 | 无 |
| 15-cases/zhihu | 8080 | MySQL（`zhihu` 库） |
| 15-cases/campus-activity | 8016 | MySQL（`campus_activity` 库） |
| 15-cases/task-commit | 8017 | MySQL（与 05 共库，`sec_` 前缀） |

## 快速开始

```bash
# 编译整个仓库
mvn -DskipTests compile

# 运行指定模块
mvn -pl 01-quickstart spring-boot:run

# 跑测试并生成覆盖率报告
mvn -pl 11-test test
```

需要 MySQL 的模块默认连接 `localhost:3306/backend_learning`，账号密码在各自 `application.yml` 里改。启动时会自动执行 `schema.sql` 与 `data.sql`，每次都是干净的初始数据。

## 验证方式

教程里的截图不是手绘的，是脚本跑出来的。复现步骤：

```bash
# 单个模块：构建 → 启动 → 按用例清单发真实请求 → 渲染截图 → 生成 OpenAPI
node tools/run-cases.mjs 05-mybatis

# 只跑一个模块的测试并生成覆盖率截图
mvn -pl 11-test test && node tools/shot-test.mjs 11-test

# 把各模块的 OpenAPI 合并成一份，供 Apifox 导入
node tools/merge-openapi.mjs
```

| 工具 | 作用 |
| --- | --- |
| `tools/run-cases.mjs` | 接口实跑：启动服务、发请求、出截图、出 OpenAPI |
| `tools/shot.mjs` | 用 Chrome DevTools Protocol 把 HTML 整页截成 WebP（无损） |
| `tools/shot-test.mjs` | 把 surefire 结果与 JaCoCo 覆盖率渲染成图 |
| `tools/merge-openapi.mjs` | 合并全部模块的 OpenAPI，生成导入说明 |
| `tools/cases/*.json` | 每个模块的接口用例清单（综合案例在 `tools/cases/15-cases/`） |

截图依赖本机装了 Chrome 与 `cwebp`（`brew install webp`）。图是 2x 分辨率渲染后转的无损 WebP，
体积约为 PNG 的三分之一，文字不会糊。

接口文档位于 [docs/apifox](docs/apifox/README.md)，可直接导入 Apifox。

## 目录结构

```
backend-learning
├── pom.xml                 # 父 POM：统一依赖版本与插件
├── README.md
├── docs/                   # 教程、截图、OpenAPI
│   ├── 01-quickstart.md ~ 14-toolkit.md
│   ├── 15-cases.md ~ 15-campus-activity.md   # 三个综合案例
│   ├── apifox/             # 接口描述与导入说明
│   └── public/shots/       # 实测截图
├── tools/                  # 接口实跑与截图工具
├── .github/workflows/      # CI 流水线
├── 01-quickstart/          # 快速入门
├── ...
├── 14-toolkit/             # 工具库实战
└── 15-cases/               # 综合案例：zhihu、task-commit、campus-activity
```

## 约定与规范

- **命名**：模块名 `NN-主题`，包名 `top.mqxu.<主题>`
- **提交**：Conventional Commits（`feat:` / `fix:` / `docs:` + 中文描述）
- **注释**：类头统一 `@author` / `@date` / `@description`
- **响应**：从 `04-web` 起统一 `Result<T>`，业务码与 HTTP 状态语义对应
- **密钥**：一律走环境变量，不进仓库
