# 15 综合案例：三个项目，三种难点

> 目录：`15-cases/`。三个案例各自独立，可以单看。

## 0. 为什么单独放一章

前 14 篇是按技术点切开的：配置、日志、数据访问、缓存、定时任务……每个模块都只讲一件事，代码为了让概念清楚而刻意简化。

真实项目不是这样长出来的。一个功能上线，通常同时用到登录、分页、事务、缓存、权限，还要考虑「两个用户同时点会怎样」。

所以这一章换成按项目切：

| 案例 | 一句话 | 难点 |
| --- | --- | --- |
| [知乎问答社区](/15-zhihu) | 提问、回答、评论、投票、关注、动态流 | 内容之间的关系，列表查询不能慢 |
| [任务提交与批改](/15-task-commit) | 学生交、教师批、管理员管账号 | 谁能做什么，越权怎么防 |
| [校园活动小程序](/15-campus-activity) | 学生报名签到，组织者发布，团委审核 | 一个后端三个入口，七个状态的报名单 |

三个项目分别对应后端工作里最常见的三类麻烦：**数据关系**、**权限边界**、**业务流转**。

## 1. 怎么选

如果只有时间看一个：

- 想学「一个业务从零到完整」→ 看知乎。它从一张表扩到八张表，每一步都在解释为什么这么设计。
- 想学「权限怎么做对」→ 看任务批改。三道关的顺序和 401/403 的区别，比任何八股都实用。
- 想学「真实项目的复杂度」→ 看校园活动。名额、候补、审核、签到码，业务规则比技术点更难。

三个都看，会发现它们在同一件事上有不同选择：

| 问题 | 知乎 | 任务批改 | 校园活动 |
| --- | --- | --- | --- |
| 认证 | 手写 JWT 拦截器 | Spring Security 过滤器链 | 拦截器 + 注解 |
| 成功码 | 200 | 200 | 0 |
| 业务错误 | 映射真实 HTTP 状态 | 大多放在 200 里 | 部分映射、部分 200 |
| 主键 | 自增 | 自增 | 雪花（MyBatis-Plus assign_id） |
| 分页返回 | 自定义 `PageResult` | `List` 全量 | 自定义 `PageResult` |

这些差异不是对错，是团队习惯。放在一起看，比只看一种更容易形成判断。

## 2. 运行矩阵

| 案例 | 后端端口 | 数据库 | 前端 | 测试数 |
| --- | --- | --- | --- | --- |
| 知乎 | 8080 | `zhihu` | `web/`（Vue3，5173） | 22 |
| 任务批改 | 8017 | `backend_learning`（`sec_` 前缀） | `web/`（Vue3，5173） | 22 |
| 校园活动 | 8016 | `campus_activity` | `app/`（uni-app）、`admin/`（Vue3） | 5 |

端口刻意错开，三个可以同时启动。

### 2.1 构建方式不一样

`zhihu` 与 `task-commit` 继承父工程的依赖管理，用 `-pl` 构建：

```bash
mvn -pl 15-cases/zhihu spring-boot:run
mvn -pl 15-cases/task-commit spring-boot:run
```

`campus-activity` 是完整的前后端项目，后端有自己的父 POM（`spring-boot-starter-parent`），不在 `15-cases` 的 `<modules>` 里：

```bash
cd 15-cases/campus-activity/server
mvn spring-boot:run
```

`15-cases/pom.xml` 里对此有注释说明。把它塞进聚合器会导致「模块目录不是 Maven 根目录」的问题——它的 `pom.xml` 在 `server/` 子目录下。

### 2.2 数据库准备

知乎与任务批改会自动建表：

```bash
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS zhihu DEFAULT CHARSET utf8mb4"
```

校园活动需要手工导入（脚本在 `server/src/main/resources/db/`）：

```bash
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS campus_activity DEFAULT CHARSET utf8mb4"
mysql -uroot -p123456 campus_activity < 15-cases/campus-activity/server/src/main/resources/db/schema.sql
mysql -uroot -p123456 campus_activity < 15-cases/campus-activity/server/src/main/resources/db/data.sql
```

原因是它的演示数据里有「报名单」「签到记录」这类会变化的数据，自动重置会让每次启动都清空，不适合边用边看。

### 2.3 跑接口用例与截图

```bash
node tools/run-cases.mjs 15-cases/zhihu
node tools/run-cases.mjs 15-cases/task-commit
node tools/run-cases.mjs 15-cases/campus-activity
```

三个案例共 50 个用例，全部真实发请求。用例定义在 `tools/cases/15-cases/` 下。

校园活动的演示数据是**有状态**的：跑之前建议重新导入 `schema.sql` 与 `data.sql`，否则「重复报名」那一条会一直是失败状态。

## 3. 目录约定

```
15-cases/
├── pom.xml                      聚合 zhihu 与 task-commit
├── zhihu/
│   ├── src/main/java/top/mqxu/zhihu
│   ├── src/main/resources/{schema.sql, data.sql}
│   └── web/                     Vue3 前端
├── task-commit/
│   ├── src/main/java/top/mqxu/security
│   ├── src/main/resources/{schema.sql, data.sql}
│   ├── guide/                   教学引导与常见坑
│   └── web/                     Vue3 前端
└── campus-activity/
    ├── server/                  Spring Boot 后端（独立工程）
    ├── app/                     uni-app 小程序
    ├── admin/                   Vue3 管理后台
    └── scripts/smoke.sh         端到端冒烟脚本
```

每个案例的 `README.md` 里有接口速查表与账号密码，教程里讲的是「为什么这样设计」。

## 4. 三个案例用到的技术点

| 技术点 | 知乎 | 任务批改 | 校园活动 |
| --- | --- | --- | --- |
| JWT | 手写 `JwtService` | jjwt + Security 过滤器 | 自研 `JwtUtil` |
| 密码存储 | BCrypt | BCrypt | BCrypt |
| 分页 | MyBatis-Plus 插件 | 全量返回 | MyBatis-Plus 插件 |
| 事务 | 回答、采纳、投票 | 提交、批改 | 报名、审核、取消 |
| 逻辑删除 | 问题、回答、评论 | 无 | 活动、报名 |
| 并发控制 | 计数用 SQL 自增 | 无 | 名额条件更新 |
| 定时任务 | 无 | 无 | 活动状态推进 |
| 参数校验 | `@Valid` + 自定义消息 | 同 | 同 |
| 统一响应 | `{code, message, data}` | `{code, message, data}` | `{code, message, data}` |

## 5. 建议的阅读顺序

如果按课程上，建议的顺序和目录顺序一致：

1. **知乎**：先把「一个业务怎么建模」这件事走通。八张表、23 个接口，工作量适中。
2. **任务批改**：在会写业务之后，学怎么限制「谁能做什么」。这部分最容易在项目里埋雷。
3. **校园活动**：最后看，因为它的业务规则最多，适合当综合练习。

每个案例的最后一节都有练习。知乎的练习偏「补功能」，任务批改的偏「改规则」，校园活动的偏「设计取舍」——难度是递增的。
