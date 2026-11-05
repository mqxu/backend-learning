---
layout: home

hero:
  name: 后端工程化开发
  text: Spring Boot 多模块学习仓库
  tagline: 从快速入门到部署上线，14 个技术模块加 3 个综合案例打通后端工程链路
  actions:
    - theme: brand
      text: 开始学习
      link: /01-quickstart
    - theme: alt
      text: 查看仓库
      link: https://github.com/mqxu/backend-learning

features:
  - title: 循序渐进
    details: 按 01~14 模块递进，每个模块独立可跑，一篇教程配一套实测截图
  - title: 每个接口都有实测
    details: 截图由脚本启动真实服务后发起真实请求生成，不是示意图
  - title: 动手实战
    details: 每篇含概念铺垫、逐步实战、接口清单、常见问题与练习
---

## 模块导航

| 序号 | 模块 | 主题 |
| --- | --- | --- |
| 01 | [快速入门](/01-quickstart) | 项目骨架、REST 接口、参数绑定 |
| 02 | [配置管理](/02-config) | @Value、@ConfigurationProperties、多环境、优先级 |
| 03 | [日志管理](/03-logging) | SLF4J、Logback、MDC、脱敏 |
| 04 | [Web 开发进阶](/04-web) | 统一响应、全局异常、参数校验、过滤器与拦截器 |
| 05 | [数据访问](/05-mybatis) | MyBatis-Plus、条件构造器、分页、事务、多表 |
| 06 | [缓存与分布式锁](/06-redis) | Redis 五种类型、缓存三问题、Redisson、短信登录 |
| 07 | [定时任务](/07-schedule) | fixedRate、fixedDelay、cron、动态任务 |
| 08 | [认证与授权](/08-security) | Spring Security、JWT、RBAC、短信登录、CSRF |
| 09 | [文件上传下载](/09-file) | 三层校验、存储抽象、流式下载、预签名 |
| 10 | [接口文档](/10-doc) | OpenAPI、springdoc、分组与导入 |
| 11 | [测试](/11-test) | JUnit 5、Mockito、MockMvc、JaCoCo |
| 12 | [应用监控](/12-actuator) | Actuator、Micrometer、Prometheus |
| 13 | [部署与 CI/CD](/13-deploy) | 多阶段镜像、Compose、GitHub Actions |
| 14 | [工具库实战](/14-toolkit) | 验证码、短链、签名、脱敏、分片上传、Excel |

### 综合案例

| 序号 | 案例 | 主题 |
| --- | --- | --- |
| 15 | [案例总览](/15-cases) | 三个项目的定位、运行矩阵与阅读顺序 |
| 15-1 | [知乎问答社区](/15-zhihu) | 八张表、批量查询、事务计数、投票与动态流 |
| 15-2 | [任务提交与批改](/15-task-commit) | 三道关、Spring Security、越权防护 |
| 15-3 | [校园活动小程序](/15-campus-activity) | 三个入口、七个状态、并发名额、动态签到码 |

## 配套资料

| 资料 | 内容 |
| --- | --- |
| [接口清单与 Apifox 导入](/apifox/README) | 14 个模块共 150 个接口的 OpenAPI 描述，可直接导入 Apifox |

## 怎么验证教程里的结论

每篇教程里的截图都对应一条可复现的命令：

```bash
# 编译
mvn -DskipTests compile

# 跑某个模块：启动服务 → 发真实请求 → 出截图与 OpenAPI
node tools/run-cases.mjs 05-mybatis

# 跑测试并生成覆盖率报告
mvn -pl 11-test test
node tools/shot-test.mjs 11-test
```

`tools/run-cases.mjs` 会做四件事：构建模块、用 `java -jar` 真启动、按用例清单发 HTTP 请求、把请求与响应渲染成截图。用例定义在 `tools/cases/<module>.json`，改接口后改这个文件再跑一遍即可。
