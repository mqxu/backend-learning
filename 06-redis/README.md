# 06-redis · 缓存与分布式锁

> 📖 详细教程：[docs/06-redis.md](../docs/06-redis.md)

> 状态：✅ 已实现（五种数据类型 / 手动缓存 / 空值防穿透 / 互斥锁防击穿 / TTL 抖动防雪崩 / 缓存注解 / Redisson 分布式锁 / SETNX 手写锁 / ZSet 排行榜 / 计数器限流 / 练习参考答案）

用一张商品表把 Redis 的常用能力走一遍：先解决缓存怎么存、怎么读、怎么失效，再处理缓存失效时的穿透、击穿、雪崩，最后用分布式锁解决多机并发的库存竞争。所有接口都能用 `curl` 直接验证，第 8 节的九道练习在本模块内都有参考实现和配套测试。

## 学习目标

- 用 `StringRedisTemplate` / `RedisTemplate` 操作 String、Hash、List、Set、ZSet
- 配置 Redis 的 JSON 序列化，理解类型信息与反序列化安全
- 手写 Cache-Aside 缓存，并加上空值缓存、互斥锁、TTL 抖动三处防护
- 用 `@Cacheable` / `@CachePut` / `@CacheEvict` / `@Caching` 把缓存逻辑从业务代码里拿掉
- 用 Redisson 实现分布式锁，理解手写 `SETNX` 锁的差距
- 用 ZSet 做排行榜、用计数器做接口限流

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| 序列化 | `GenericJacksonJsonRedisSerializer`（Jackson 3 版）配 `enableDefaultTyping`，否则读回来是 `LinkedHashMap` |
| 反序列化安全 | `BasicPolymorphicTypeValidator` 限定可还原的类型前缀：`top.mqxu.`、`java.lang.`、`java.util.`、`java.math.` |
| 空值缓存 | 查不到时写 `"NULL"` 标记，TTL 比正常值短，防穿透 |
| 互斥锁 | 未命中先用 `SETNX` 抢锁，只放一个请求回源，防击穿 |
| TTL 抖动 | 基础 TTL 加 0 到 30 秒随机值，防雪崩 |
| 更新策略 | 先更新库再删缓存，不一致概率最小 |
| 缓存抽象 | `@Cacheable`（查）/ `@CachePut`（覆盖）/ `@CacheEvict`（删）/ `@Caching`（组合） |
| 缓存 key | `cache:goods::<id>` 与 `cache:goods::all`，前缀由 `spring.cache.redis.key-prefix` 决定 |
| 分布式锁 | Redisson `tryLock(等待时间, 持锁时间, 单位)`，解锁前比对持有者 |
| Lua 原子解锁 | `get` 比对与 `del` 放同一个脚本，避免误删别人的锁 |
| ZSet 排行榜 | `incrementScore` 原子加分，`reverseRangeWithScores` 取前 N |
| 固定窗口限流 | key 里带窗口编号，只在计数器创建时设过期 |

## 目录结构

```
06-redis
└── src
    ├── main
    │   ├── java/top/mqxu/redis
    │   │   ├── RedisApplication.java            # @EnableCaching
    │   │   ├── config
    │   │   │   ├── RedisConfig.java             # 序列化 + 缓存配置 + 同步写入
    │   │   │   └── DemoProperties.java          # demo.* 参数
    │   │   ├── controller
    │   │   │   ├── RedisController.java         # 五种数据类型与计数器
    │   │   │   ├── CacheController.java         # 直查库 / 手动缓存 / 注解缓存 对照
    │   │   │   ├── LockController.java          # 三种扣库存写法对照
    │   │   │   ├── RankController.java          # ZSet 排行榜
    │   │   │   └── LimitController.java         # 固定窗口限流
    │   │   ├── service
    │   │   │   ├── GoodsService.java            # 只查库，作为对照
    │   │   │   ├── GoodsCacheService.java       # 手动缓存 + 三处防护
    │   │   │   ├── GoodsSpringCacheService.java # 注解缓存
    │   │   │   └── StockService.java            # 扣库存
    │   │   ├── entity/Goods.java
    │   │   ├── mapper/GoodsMapper.java
    │   │   └── dto/CacheResult.java             # 带 source 与耗时的返回体
    │   └── resources
    │       ├── application.yml                  # Redis / 缓存 / 数据源 / demo.*
    │       ├── schema.sql                       # t_goods 建表，启动时执行
    │       └── data.sql                         # 10 条商品，启动时执行
    └── test/java/top/mqxu/redis
        ├── RedisTypeOpsTest.java                # 9 个用例，数据类型接口
        ├── GoodsCacheTest.java                  # 13 个用例，三种缓存做法
        ├── LockAndLimitTest.java                # 10 个用例，并发、限流、排行榜
        ├── PracticeTest.java                    # 5 个用例，随堂练习
        └── HomeworkTest.java                    # 4 个用例，课后练习
```

## 运行方式

先确认两个外部服务：

```bash
# Redis，密码见 application.yml
redis-cli --no-auth-warning -a abc123 ping
# PONG

# MySQL
mysql -h127.0.0.1 -P3306 -uroot -p123456 -e "show databases;"
```

库沿用 `05-mybatis` 的 `backend_learning`，`t_goods` 由 `schema.sql` 在启动时自动创建：

```bash
mvn -pl 06-redis spring-boot:run
```

端口：`8006`

启动后库里有 10 条商品，分类覆盖数码、家居、图书、运动、食品，销量各不相同。`schema.sql` 里有 `DROP TABLE`，每次启动和 DevTools 热重启都会重置数据。

主要接口（34 个，每个接口的测试命令与预期返回见 [docs/06-redis.md](../docs/06-redis.md) 第 6 节，这里只作速查）：

| 分组 | 操作 | 请求 |
| --- | --- | --- |
| 数据类型 | 写 String | `POST /redis/string?key=&value=&ttlSeconds=` |
| | 读 String | `GET /redis/string?key=` |
| | 查剩余过期时间 | `GET /redis/ttl?key=` |
| | 删 key | `DELETE /redis/key?key=` |
| | 写 Hash | `POST /redis/hash?key=&字段=值...` |
| | 读 Hash | `GET /redis/hash?key=` |
| | 写 List | `POST /redis/list?key=&values=a&values=b` |
| | 读 List | `GET /redis/list?key=` |
| | 写 Set | `POST /redis/set?key=&values=` |
| | Set 交集 | `GET /redis/set/intersect?key1=&key2=` |
| | 写 ZSet | `POST /redis/zset?key=&member=&score=` |
| | ZSet 前 N | `GET /redis/zset/top?key=&top=` |
| | 计数器 | `POST /redis/counter/incr?key=&delta=` |
| 缓存 | 直查库 | `GET /cache/goods/db/{id}` |
| | 手动缓存 | `GET /cache/goods/manual/{id}` |
| | 手动缓存 + 互斥锁 | `GET /cache/goods/manual-mutex/{id}` |
| | 注解缓存单个 | `GET /cache/goods/anno/{id}` |
| | 注解缓存列表 | `GET /cache/goods/anno/list` |
| | 改价（更新库后删缓存） | `PUT /cache/goods/{id}/price?price=` |
| | 改价（`@CachePut` 写回） | `PUT /cache/goods/{id}/price-anno?price=` |
| | 只删手动缓存 | `DELETE /cache/goods/manual/{id}/cache` |
| | 清注解缓存的明细与列表 | `DELETE /cache/goods/anno/{id}` |
| | 清空注解缓存区域 | `DELETE /cache/goods/anno` |
| 锁 | 不加锁购买 | `POST /lock/buy/no-lock?id=&quantity=` |
| | Redisson 锁购买 | `POST /lock/buy/redisson?id=&quantity=` |
| | 手写 SETNX 购买 | `POST /lock/buy/setnx?id=&quantity=` |
| | 重置库存 | `POST /lock/reset?id=&stock=` |
| | 查库存 | `GET /lock/stock/{id}` |
| | 查锁的剩余时间 | `GET /lock/info?key=` |
| 排行 | 预热 | `POST /rank/warmup` |
| | 加分 | `POST /rank/increase?id=&delta=` |
| | 前 N 名 | `GET /rank/top?top=` |
| | 查名次 | `GET /rank/rank-of?id=` |
| 限流 | 访问一次 | `GET /limit/access?user=` |

参数里带中文时先做 URL 编码，否则 Tomcat 会以 400 拒绝：

```bash
curl -s -X POST -G 'http://127.0.0.1:8006/redis/hash' \
  --data-urlencode 'key=demo:user:2' --data-urlencode 'name=陶老师'
```

并发扣库存的三组对比：

```bash
curl -s -X POST 'http://127.0.0.1:8006/lock/reset?id=1&stock=10'
seq 1 20 | xargs -P 20 -I{} curl -s --noproxy '*' -X POST 'http://127.0.0.1:8006/lock/buy/no-lock?id=1&quantity=1'
curl -s 'http://127.0.0.1:8006/lock/stock/1'
# 20 个请求都返回成功，库存却只减了一两件（具体数字每次不同）
```

## 测试

```bash
mvn -pl 06-redis test
```

41 个用例，连本地真实的 MySQL 与 Redis。缓存与库存相关的用例每条开头都会重跑建表与初始化脚本，跑完库里是初始化数据，不需要额外恢复。

只跑某一个用例：

```bash
mvn -pl 06-redis test -Dtest=LockAndLimitTest#deductWithRedissonLockShouldNotOversell
```

## 练习

随堂练习五题（Hash 单字段更新、Set 交集、TTL 抖动、SETNX 去重、排行榜取邻位）与课后练习四题（购物车、逻辑过期、按接口限流、先删缓存的不一致）的参考答案、测试过程与预期输出，在 [docs/06-redis.md](../docs/06-redis.md) 第 8 节，对应代码在 `PracticeTest` 与 `HomeworkTest`。

## 依赖

- `spring-boot-starter-data-redis`（底层 Lettuce 7.5.2）
- `spring-boot-starter-cache`（Boot 4 把缓存自动配置拆到了独立模块，用 `@Cacheable` 必须显式引入）
- `commons-pool2`（缺了它 `lettuce.pool.*` 会被静默忽略）
- `redisson-spring-boot-starter` 4.8.0（自带 Boot 4 自动配置，读 `spring.data.redis.*`）
- `mybatis-plus-spring-boot4-starter` 3.5.17
- `mysql-connector-j`（runtime，版本由 `spring-boot-dependencies` 管理）
