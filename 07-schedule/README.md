# 07-schedule · 定时任务

> 📖 详细教程：[docs/07-schedule.md](../docs/07-schedule.md)

`@Scheduled` 三种触发方式、cron 表达式、调度线程池、运行期动态增删任务。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/schedule/hello` | 基础接口 |
| GET | `/schedule/tasks` | 各任务执行次数与最近执行时间 |
| GET | `/schedule/tasks/records?limit=` | 最近执行记录（含线程名） |
| POST | `/schedule/tasks/dynamic` | 注册动态任务（name、cron） |
| DELETE | `/schedule/tasks/dynamic/{name}` | 取消动态任务 |
| GET | `/schedule/tasks/dynamic` | 当前运行的动态任务 |
| GET | `/schedule/weather` | 今日天气（mock 模式返回内置数据） |
| POST | `/schedule/weather/send` | 手动触发天气邮件 |

## 内置任务

| 任务 | 触发方式 | 周期 |
| --- | --- | --- |
| `heartBeat` | `fixedRate` | 3 秒（上次开始算） |
| `cleanExpiredData` | `fixedDelay` | 5 秒（上次结束算） |
| `dailyReport` | `cron` | 每 10 秒 |
| `sendDailyWeather` | `cron` | 每天 9:22，可配置 |

## 运行

```bash
mvn -pl 07-schedule spring-boot:run
node tools/run-cases.mjs 07-schedule
```

## 测试

```bash
mvn -pl 07-schedule test
```

14 个用例，分两类：

| 测试类 | 用例数 | 说明 |
| --- | --- | --- |
| `CronTriggerTest` | 4 | 不启动 Spring，验证 cron 解析、下次触发时间、非法表达式 |
| `TaskExecutionRecorderTest` | 3 | 记录倒序、分组统计、200 条容量上限 |
| `ScheduleIntegrationTest` | 7 | 启动真实调度器，验证三类任务确实触发、动态任务增删、天气与邮件 |

集成测试会等 11 秒让三类任务各跑一轮，整个测试约 15 秒。

## 外部依赖

天气接口与邮件默认走模拟通道（`weather.mock`、`email.mock`），不需要任何密钥即可跑通流程。接真实服务时改成 `false`，并配好三个环境变量：

```bash
export MAIL_USERNAME=xxx@qq.com
export MAIL_PASSWORD=授权码
export WEATHER_API_KEY=和风天气key
```

密钥不写进仓库。调度线程池在 `spring.task.scheduling.pool.size` 配置，默认单线程会拖慢其他任务。
