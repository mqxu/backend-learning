# 08 · 定时任务 —— 让系统自动执行

> - 难度：进阶
> - 前置模块：07-mq
> - 预计时长：约 45 分钟
> - 对应代码：[08-schedule](../08-schedule)

## 1. 本节导读

学完本节，你将能：

1. 用 `@Scheduled` 写定时任务，掌握三种触发方式；
2. 读懂并编写 cron 表达式；
3. 了解 Quartz 与动态任务；
4. 理解多实例下的定时任务幂等。

**前置知识**：会 Spring Bean 与注解即可。

---

## 2. 概念铺垫

### 2.1 定时任务能做什么

报表每天凌晨生成、订单超时自动关闭、缓存定时刷新…… 这些「到点自动执行」的事都是定时任务。

### 2.2 三种触发方式

| 方式 | 含义 | 适用 |
| --- | --- | --- |
| `fixedRate` | 上次**开始**后固定间隔再触发 | 固定频率 |
| `fixedDelay` | 上次**结束**后固定间隔再触发 | 固定间隔、避免重叠 |
| `cron` | 按表达式精确到秒触发 | 每天几点、每周几 |

> `fixedRate` 与 `fixedDelay` 区别：前者看「开始时间」，后者看「结束时间」。任务执行时间长时，`fixedDelay` 更安全，不会堆积。

### 2.3 cron 表达式

格式：`秒 分 时 日 月 周`（6 位）：

| 字段 | 范围 | 示例 |
| --- | --- | --- |
| 秒 | 0-59 | `0` |
| 分 | 0-59 | `0` |
| 时 | 0-23 | `2` |
| 日 | 1-31 | `*` |
| 月 | 1-12 | `*` |
| 周 | 0-7（0 和 7 都是周日） | `*` |

示例：

| 表达式 | 含义 |
| --- | --- |
| `0 0 2 * * *` | 每天凌晨 2 点 |
| `0 0/5 * * * *` | 每 5 分钟 |
| `0 0 9 * * 1` | 每周一上午 9 点 |

---

## 3. 环境准备

`@Scheduled` 由 Spring 内置，无需额外依赖。如需 Quartz 再引入 `spring-boot-starter-quartz`（本节不展开）。

---

## 4. 动手实战

> 本模块当前是骨架，下面带你逐步补全定时任务能力。

### 步骤 1：开启定时任务

```java
package top.mqxu.schedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 *
 * @author mqxu
 */
@EnableScheduling
@SpringBootApplication
public class ScheduleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScheduleApplication.class, args);
    }
}
```

### 步骤 2：写三种定时任务

```java
package top.mqxu.schedule.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务演示
 *
 * @author mqxu
 */
@Slf4j
@Component
public class DemoTask {

    // 上次开始后固定 5 秒再触发
    @Scheduled(fixedRate = 5000)
    public void fixedRateTask() {
        log.info("fixedRate 任务执行");
    }

    // 上次结束后隔 5 秒再触发
    @Scheduled(fixedDelay = 5000)
    public void fixedDelayTask() {
        log.info("fixedDelay 任务执行");
    }

    // 每天凌晨 2 点
    @Scheduled(cron = "0 0 2 * * *")
    public void cronTask() {
        log.info("cron 任务执行");
    }
}
```

讲解：

- `@Scheduled` 标注的方法必须是 `void`、无参数；
- 单位是**毫秒**（5000 = 5 秒）。

### 步骤 3：把任务参数放到配置里

目标：执行频率可配置，不硬编码。

```yaml
schedule:
  cron: 0 0 2 * * *
```

```java
@Scheduled(cron = "${schedule.cron}")
public void cronTask() {
    log.info("cron 任务执行");
}
```

讲解：`${schedule.cron}` 读取配置，改频率不用改代码——呼应 `02-config` 的外部化配置。

### 步骤 4：多实例幂等

目标：任务部署在多台机器时，别重复执行。

思路：用 `06-redis` 的分布式锁，加锁成功才执行：

```java
@Scheduled(cron = "${schedule.cron}")
public void cronTask() {
    RLock lock = redissonClient.getLock("lock:schedule:cronTask");
    if (lock.tryLock()) {
        try {
            // 只有一台机器能执行到这里
            log.info("cron 任务执行");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

---

## 5. 完整代码与目录结构

```
08-schedule
└── src/main
    ├── java/top/mqxu/schedule
    │   ├── ScheduleApplication.java   # @EnableScheduling
    │   └── task/DemoTask.java
    └── resources/application.yml
```

---

## 6. 运行与验证

```bash
mvn -pl 08-schedule -am spring-boot:run
```

观察控制台：每 5 秒打印一次 `fixedRate`、`fixedDelay` 日志。

临时把 `fixedRate` 改成 1000（1 秒）验证更明显。

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 任务不执行 | 没加 `@EnableScheduling` | 启动类加注解 |
| 任务堆积 | `fixedRate` 间隔小于执行时长 | 改用 `fixedDelay` |
| cron 不触发 | 表达式写错（6 位顺序） | 用在线 cron 工具校验 |
| 多实例重复执行 | 每台机器都跑 | 用分布式锁保证幂等 |
| 单线程串行阻塞 | 默认单线程调度，一个任务慢会拖累其他 | 配置线程池 |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 写一个每 3 秒打印的 `fixedDelay` 任务 | 控制台稳定 3 秒一次 |
| 2 | 写一个 cron 每 1 分钟执行的任务 | 到点触发 |
| 3 | 把间隔改成配置文件读取 | 改 yml 后重启生效 |

### 8.2 课后练习（课后完成并提交）

- **基础**：用 `@Scheduled` 定时清理某个目录下超过 7 天的日志文件。
- **进阶**：给定时任务配置一个自定义线程池（`ThreadPoolTaskScheduler`），避免默认单线程串行。
- **挑战**：用 cron 表达式写「每月最后一天 23:50」的任务，并说明为什么 cron 做不到，需要代码判断。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| `@Scheduled` | 三种方式：fixedRate / fixedDelay / cron |
| cron | 6 位：秒 分 时 日 月 周 |
| 参数化 | `${}` 读取配置，不硬编码 |
| 幂等 | 多实例用分布式锁 |

下一节 [09-security](09-security.md) 会讲认证授权，给接口加安全防线。

> 本节遵循的规范（官方 + 阿里手册）：定时任务用 `@Scheduled` 声明式写法；频率参数化；分布式部署保证幂等；耗时任务避免用默认单线程调度。
