# 06 · 缓存与分布式锁 —— 用 Redis 提升性能

> - 难度：进阶
> - 前置模块：05-mybatis
> - 预计时长：约 60 分钟
> - 对应代码：[06-redis](../06-redis)

## 1. 本节导读

学完本节，你将能：

1. 用 `StringRedisTemplate` 操作 Redis；
2. 用 Spring 缓存抽象 `@Cacheable` / `@CacheEvict` 给查询加速；
3. 理解并防护缓存穿透、击穿、雪崩；
4. 用 Redisson 实现分布式锁。

**前置知识**：会 `05-mybatis`；了解「缓存就是更快的内存存储」。

---

## 2. 概念铺垫

### 2.1 为什么要缓存

数据库是磁盘，Redis 是内存，读内存比读磁盘快几个数量级。把「读多写少」的热点数据放进 Redis，能大幅降低数据库压力。

### 2.2 缓存三大问题（必考）

| 问题 | 场景 | 后果 | 对策 |
| --- | --- | --- | --- |
| 穿透 | 查一个**不存在**的 key | 每次都打到数据库 | 缓存空值 / 布隆过滤器 |
| 击穿 | **热点 key 过期**瞬间大量请求 | 全部打到数据库 | 互斥锁 / 逻辑过期 |
| 雪崩 | **大量 key 同时过期** | 数据库瞬间被打垮 | 过期时间加随机值 |

### 2.3 分布式锁解决什么

单机用 `synchronized` 加锁没问题；但服务部署了多台机器，`synchronized` 只能锁住一台，锁不住跨机器的并发。Redis 是各机器共享的，所以用它做**分布式锁**。

---

## 3. 环境准备

1. 安装并启动 Redis（默认 `localhost:6379`）；
2. 依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
</dependency>
```

连接配置：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

---

## 4. 动手实战

> 本模块当前是骨架，下面带你逐步补全缓存能力。

### 步骤 1：`StringRedisTemplate` 基本操作

```java
package top.mqxu.redis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Redis 演示接口
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/redis")
@RequiredArgsConstructor
public class RedisController {

    private final StringRedisTemplate redisTemplate;

    @PutMapping("/{key}")
    public void set(@PathVariable String key, @RequestParam String value) {
        redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(10));
    }

    @GetMapping("/{key}")
    public String get(@PathVariable String key) {
        return redisTemplate.opsForValue().get(key);
    }
}
```

讲解：

- `StringRedisTemplate` 针对「字符串」场景，简单够用；存对象时用 `RedisTemplate` + 序列化器；
- `set` 带 `Duration` 设置过期时间（防雪崩的关键）。

### 步骤 2：缓存抽象 `@Cacheable`

目标：给「查数据库」的方法自动加缓存，代码零侵入。

开启缓存：

```java
package top.mqxu.redis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 启动类
 *
 * @author mqxu
 */
@EnableCaching
@SpringBootApplication
public class RedisApplication {
    public static void main(String[] args) {
        SpringApplication.run(RedisApplication.class, args);
    }
}
```

使用：

```java
@Cacheable(value = "user", key = "#id")
public User getById(Long id) {
    // 命中缓存时不执行这里；未命中则查库并自动写入缓存
    return userMapper.selectById(id);
}

@CacheEvict(value = "user", key = "#id")
public void delete(Long id) {
    userMapper.deleteById(id);
}
```

讲解：

- `@Cacheable`：先查缓存，命中直接返回，未命中执行方法体并把结果写进缓存；
- `@CacheEvict`：删除数据时同时清缓存，避免读到脏数据；
- `key = "#id"` 用 SpEL 指定缓存键。

### 步骤 3：防护三大问题

```java
// 穿透：空结果也缓存，避免每次都打库
@Cacheable(value = "user", key = "#id", unless = "#result == null")
public User getById(Long id) {
    return userMapper.selectById(id);
}
```

- **穿透**：`unless = "#result == null"` 表示结果非空才缓存；更彻底可缓存空值标记；
- **击穿**：对热点 key 加互斥锁（用步骤 4 的分布式锁），只有一个请求去回源；
- **雪崩**：写缓存时给 TTL 加随机值：`Duration.ofMinutes(10 + random)`。

### 步骤 4：Redisson 分布式锁

目标：多台机器下，同一时刻只有一个线程能执行某段逻辑。

```java
package top.mqxu.redis.controller;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁演示
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/lock")
@RequiredArgsConstructor
public class LockController {

    private final RedissonClient redissonClient;

    @GetMapping("/buy")
    public String buy(@RequestParam Long userId) {
        RLock lock = redissonClient.getLock("lock:buy:" + userId);
        try {
            // 尝试加锁：等待 5 秒，锁 30 秒后自动释放（防死锁）
            if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                // 扣库存等临界区代码
                return "抢购成功";
            }
            return "系统繁忙，请稍后再试";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "操作被中断";
        } finally {
            // 只有持锁的线程才能释放
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

讲解：

- `tryLock(waitTime, leaseTime, unit)`：最多等 5 秒，拿到锁后 30 秒自动释放（看门狗续期机制）；
- **规范点**：`finally` 里 `isHeldByCurrentThread()` 判断后再 `unlock()`，避免释放别人的锁。

---

## 5. 完整代码与目录结构

```
06-redis
└── src/main
    ├── java/top/mqxu/redis
    │   ├── RedisApplication.java      # @EnableCaching
    │   ├── controller
    │   │   ├── RedisController.java
    │   │   └── LockController.java
    │   ├── service/UserService.java
    │   └── entity/User.java
    └── resources/application.yml
```

---

## 6. 运行与验证

```bash
mvn -pl 06-redis -am spring-boot:run
```

| 操作 | 请求 | 期望 |
| --- | --- | --- |
| 写缓存 | `PUT /redis/name?value=张三` | 返回成功 |
| 读缓存 | `GET /redis/name` | 返回「张三」 |
| 加锁抢购 | `GET /lock/buy?userId=1001` | 返回「抢购成功」或「系统繁忙」 |

验证缓存生效：查两次 `/users/1`，第二次日志里**没有** SQL 查询（命中缓存）。

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 存对象乱码 | `RedisTemplate` 默认 JDK 序列化 | 配 Jackson 序列化器，或改用 `StringRedisTemplate` |
| 缓存不生效 | 没加 `@EnableCaching`，或方法内部自调用 | 加注解；同类自调用绕过了代理，抽到独立 Bean |
| 缓存与数据库不一致 | 更新数据没清缓存 | 写操作配 `@CacheEvict` / `@CachePut` |
| 释放锁报错 | 释放了别人的锁 | `finally` 里 `isHeldByCurrentThread()` 判断 |
| 锁一直占着 | 没设 `leaseTime` 且进程崩溃 | 设 `leaseTime`，靠看门狗/自动过期兜底 |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 用 `StringRedisTemplate` 写一个带过期时间的 key | 过期后再读返回 null |
| 2 | 给一个查询方法加 `@Cacheable` | 第二次查询无 SQL 日志 |
| 3 | 用 `@CacheEvict` 删除后，再查询触发回源 | 日志重新出现 SQL |

### 8.2 课后练习（课后完成并提交）

- **基础**：给 `@Cacheable` 配置 `RedisCacheManager` 统一 TTL。
- **进阶**：实现一个「缓存穿透空值」的查询，验证查询不存在 id 时也会缓存空标记。
- **挑战**：两个请求同时抢购，用分布式锁验证只有一个能拿到锁；写一段话说明 `tryLock` 两个超时参数的含义。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| `StringRedisTemplate` | 字符串场景的 Redis 操作 |
| `@Cacheable/@CacheEvict` | 声明式缓存，零侵入 |
| 穿透/击穿/雪崩 | 空值缓存 / 互斥锁 / 随机 TTL |
| Redisson 锁 | `tryLock(wait, lease, unit)` + 安全释放 |

下一节 [07-mq](07-mq.md) 会讲消息队列，用异步解耦进一步削峰。

> 本节遵循的规范（官方 + 阿里手册）：缓存与 DB 一致性靠「先更新 DB 再删缓存」；锁释放用 `finally` + 持有判断；缓存 key 规范命名、带业务前缀。
