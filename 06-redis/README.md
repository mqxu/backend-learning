# 06-redis · 缓存与分布式锁
> 📖 详细教程：[docs/06-redis.md](../docs/06-redis.md)

> 状态：🚧 骨架已创建，核心案例待开发

高性能缓存与分布式锁。

## 学习目标

- Spring Data Redis 使用
- 缓存三大问题防护
- Redisson 分布式锁

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| Spring Data Redis | `StringRedisTemplate` / `RedisTemplate` |
| 缓存抽象 | `@Cacheable` / `@CacheEvict` |
| 缓存三大问题 | 穿透 / 击穿 / 雪崩 |
| Redisson | 分布式锁、看门狗续期 |

## 目录结构

```
06-redis
└── src/main
    ├── java/top/mqxu/redis
    │   ├── RedisApplication.java
    │   └── controller/RedisController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 06-redis -am spring-boot:run
```

端口：`8006`

## 待引入依赖

- `spring-boot-starter-data-redis`
- `redisson`
