# 08-schedule · 定时任务
> 📖 详细教程：[docs/08-schedule.md](../docs/08-schedule.md)

> 状态：🚧 骨架已创建，核心案例待开发

定时任务调度。

## 学习目标

- `@Scheduled` 与 cron
- Quartz 动态任务

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| `@Scheduled` | fixedRate / fixedDelay / cron |
| cron 表达式 | 秒 分 时 日 月 周 |
| Quartz | 持久化、动态增删改 |
| 分布式调度 | 多实例下的幂等 |

## 目录结构

```
08-schedule
└── src/main
    ├── java/top/mqxu/schedule
    │   ├── ScheduleApplication.java
    │   └── controller/ScheduleController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 08-schedule -am spring-boot:run
```

端口：`8008`

## 待引入依赖

- 无 —— `@Scheduled` 内置；Quartz 可选 `spring-boot-starter-quartz`
