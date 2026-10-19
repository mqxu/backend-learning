# 06 · 缓存与分布式锁 —— 用 Redis 提升性能

> - 难度：进阶
> - 前置模块：05-mybatis
> - 预计时长：约 120 分钟
> - 对应代码：[06-redis](https://github.com/mqxu/backend-learning/tree/main/06-redis)

## 1. 本节导读

上一节把数据放进了 MySQL。数据库读的是磁盘，一个稍复杂的查询加上网络往返，响应时间通常在毫秒到几十毫秒。而相当一部分请求读的是同一批数据，比如商品详情、首页分类、热门榜单，每次都去查库既慢又浪费。这一节把这些数据放进 Redis，同时处理由此带来的缓存一致性、并发和限流问题。

学完本节，你将能：

1. 用 `StringRedisTemplate` 与 `RedisTemplate` 操作 Redis 的五种常用数据结构；
2. 自己写 Cache-Aside 缓存，并加上空值缓存、互斥锁、TTL 抖动三处防护；
3. 用 Spring 缓存注解 `@Cacheable` / `@CachePut` / `@CacheEvict` 把缓存逻辑从业务代码里抽走；
4. 用 Redisson 实现分布式锁，并知道手写 `SETNX` 锁差在哪里；
5. 用 ZSet 做排行榜，用计数器做接口限流；
6. 说清楚缓存穿透、击穿、雪崩分别对应代码里的哪几行。

**前置知识**：完成 `05-mybatis`，会用 MyBatis-Plus 查库；了解 `@Transactional`。

**本节用到的 Redis 版本**：8.2.2；Spring Boot 4.1.1 自带的 Lettuce 7.5.2；Redisson 4.8.0。

---

## 2. 概念铺垫

### 2.1 缓存放在哪一层

一次请求经过的层次越多，能加缓存的位置就越多：

```
浏览器缓存 → CDN → Nginx 缓存 → 应用层缓存（Redis / 本地 Map）→ 数据库缓存
```

本节讲的是应用层缓存。它离业务最近，能缓存数据库查询结果，也能缓存计算结果，灵活度最高。

要判断一份数据适不适合放进缓存，看两点：

| 特征 | 适合缓存 | 不适合缓存 |
| --- | --- | --- |
| 读写比例 | 读多写少，比如商品详情 | 写多读少，比如订单流水 |
| 一致性要求 | 允许短暂不一致，比如销量、榜单 | 必须强一致，比如余额、库存扣减结果 |

第二条是初学时最容易踩的坑。把库存放进缓存、下单时改缓存不改库，看着很快，一旦缓存和库对不上，账就错了。本节里库存扣减的例子始终以数据库为准，Redis 只用来加锁，不用来存库存。

### 2.2 Redis 的数据结构与适用场景

Redis 不是简单的 key-value，它的 value 有明确的数据结构，用对了能省掉很多应用层的处理。

| 结构 | 一句话说明 | 典型场景 | 本节接口 |
| --- | --- | --- | --- |
| String | 一个 key 对应一个值，可当计数器 | 缓存对象、验证码、计数器 | `/redis/string`、`/redis/counter/incr` |
| Hash | 一个 key 下挂多个字段 | 对象的部分字段读写、购物车 | `/redis/hash` |
| List | 有序、可重复的队列 | 最新动态、消息队列 | `/redis/list` |
| Set | 无序、自动去重 | 点赞去重、共同关注 | `/redis/set`、`/redis/set/intersect` |
| ZSet | 带分数、自动按分数排序 | 排行榜、延迟队列 | `/redis/zset`、`/rank/*` |

ZSet 值得单独说一句。要有序，常规做法是查出来后在内存里排序，数据量一大就慢。ZSet 把排序做在写入时，读的时候按分数区间直接取，取前 10 名的时间与总条数无关。

### 2.3 缓存的更新策略与一致性

数据既在库里又在缓存里，改动时先动谁、后动谁，有几种常见组合：

| 策略 | 做法 | 问题 |
| --- | --- | --- |
| 先更新库，再更新缓存 | `update db` → `set cache` | 并发下两次更新写入缓存的顺序可能颠倒，缓存留下旧值 |
| 先删缓存，再更新库 | `del cache` → `update db` | 删完到更新完之间若有读请求，会把旧值重新写回缓存 |
| 先更新库，再删缓存 | `update db` → `del cache` | 出现不一致的概率最小，工业界最常用 |

本节采用第三种。它并非绝对一致：极端情况下，一个读请求在库更新前读到旧值，然后被阻塞，等库更新、缓存删除之后才把旧值写回缓存。要彻底避免，需要延迟双删或订阅 binlog 更新缓存。作为教学，记住“更新库后删缓存，并且让缓存尽量短命”这两条就够了。

### 2.4 缓存穿透、击穿、雪崩

这三件事名字相近，成因完全不同，对策也不一样。

| 问题 | 成因 | 现象 | 对策 |
| --- | --- | --- | --- |
| 穿透 | 反复查询一个库里也不存在的 key | 缓存永远不命中，请求全部落到数据库 | 缓存空值（本节做法）、布隆过滤器 |
| 击穿 | 某个热点 key 到期的瞬间，大量并发同时未命中 | 数据库在很短时间里承受全部压力 | 未命中时用互斥锁只放一个请求回源 |
| 雪崩 | 大批 key 在同一时刻集中过期 | 数据库压力在某个时间点陡增 | 过期时间加随机值，避免同时到期 |

三者在代码里对应的位置：

- 穿透 → `GoodsCacheService.cacheIt()` 里，查不到数据时写入 `NULL` 标记，存活 2 分钟；
- 击穿 → `GoodsCacheService.getByIdWithMutex()` 里，未命中先用 `SETNX` 抢锁；
- 雪崩 → `GoodsCacheService.ttlWithJitter()` 里，基础 TTL 加上 0 到 30 秒的随机值。

顺带一个容易混淆的概念，缓存预热：服务刚启动时缓存是空的，所有请求都会回源。做法是在启动后主动把热点数据灌进缓存，`/rank/warmup` 就是这个思路。

### 2.5 本地缓存与分布式缓存

应用部署多台机器后，缓存放在哪里需要做选择：

| 维度 | 本地缓存（Caffeine / Map） | 分布式缓存（Redis） |
| --- | --- | --- |
| 访问速度 | 纳秒级，不走网络 | 百微秒级，一次网络往返 |
| 容量 | 受单机内存限制 | 可独立扩容 |
| 一致性 | 各机器各自一份，改一处别的机器不知道 | 全局一份，天然一致 |
| 适用 | 变更极少、容得下脏数据的配置类数据 | 需要共享和一致性的业务数据 |

工程上常见的组合是两级缓存：Redis 兜底，本地缓存挡住最热的一小部分。代价是本地缓存那一层要额外处理失效通知，否则会出现同一份数据不同机器看到不同值。本节只讲 Redis。

### 2.6 分布式锁的三个必要条件

单机用 `synchronized` 就够了，多台机器时它只能锁住当前进程。Redis 是所有机器共享的，可以用它做跨机器的锁。一个能用的分布式锁必须同时满足：

1. **互斥**：同一时刻只有一个持有者。
2. **能自动释放**：持有者进程崩溃时锁不能永久留在 Redis 里，所以要给锁设过期时间。
3. **只释放自己的锁**：A 因为业务超时导致锁自动过期，B 拿到锁开始执行，此时 A 执行完去删锁，删掉的是 B 的锁。解决办法是锁的值带上持有者标识，删除前先比对，比对与删除要在一次原子操作里完成，所以用 Lua。

Redisson 的 `RLock` 把这三条都封装好了，还额外提供看门狗续期、可重入计数。自己用 `SETNX` 写，最容易漏掉的是第 3 条。

---

## 3. 环境准备

### 3.1 安装并启动 Redis

macOS 用 Homebrew：

```bash
brew install redis
brew services start redis
```

或者用 Docker：

```bash
docker run -d --name redis -p 6379:6379 redis:8.2 redis-server --requirepass abc123
```

本模块的配置文件里 Redis 密码是 `abc123`，与本仓库开发环境保持一致。如果用 Homebrew 安装，在 `/opt/homebrew/etc/redis.conf` 里加上：

```
requirepass abc123
```

改完重启 Redis，用下面的命令确认连通：

```bash
redis-cli --no-auth-warning -a abc123 ping
# PONG
```

### 3.2 数据库

复用 `05-mybatis` 建的库 `backend_learning`，本模块自己建一张商品表 `t_goods`，与 `05-mybatis` 的表互不影响。

```sql
CREATE TABLE `t_goods` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '商品名称',
  `category` varchar(32) NOT NULL COMMENT '分类',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `stock` int NOT NULL DEFAULT '0' COMMENT '库存',
  `sales` int NOT NULL DEFAULT '0' COMMENT '销量',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 上架，0 下架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表';
```

> 可以借助 AI 自行生成一些数据

### 3.3 依赖

`06-redis/pom.xml` 一共引了五个依赖：

```xml
<properties>
    <mybatis-plus.version>3.5.17</mybatis-plus.version>
    <redisson.version>4.8.0</redisson.version>
</properties>

<dependencies>
    <!-- Redis 客户端与 Spring Data Redis 抽象，底层默认用 Lettuce -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- Lettuce 的 pool 配置由 commons-pool2 提供，缺了它池参数会静默失效 -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-pool2</artifactId>
    </dependency>

    <!-- 缓存抽象：Boot 4 把缓存自动配置拆成独立的 spring-boot-cache 模块 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>

    <!-- 分布式锁 -->
    <dependency>
        <groupId>org.redisson</groupId>
        <artifactId>redisson-spring-boot-starter</artifactId>
        <version>${redisson.version}</version>
    </dependency>

    <!-- 给缓存提供一个真实的数据库做对照 -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        <version>${mybatis-plus.version}</version>
    </dependency>

    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

三个与 Boot 4 相关的注意点：

**缓存自动配置被拆出去了。** 在 Boot 3 里，`spring-boot-starter-data-redis` 会连带引入缓存自动配置，用 `@Cacheable` 不需要额外加依赖。Boot 4 把缓存自动配置挪到了独立的 `spring-boot-cache` 模块，所以要用 `@Cacheable` 必须显式加 `spring-boot-starter-cache`，否则注解不会报错，但也不会生效。配置属性的前缀仍是 `spring.cache`，没有变。

**Redisson 4.x 自带 Boot 4 的自动配置。** 从 4.x 开始，`redisson-spring-boot-starter` 里带了针对 Boot 4 的自动配置类，会自动读取 `spring.data.redis.*` 建 `RedissonClient`，不需要再手写配置类。适配的 Spring Data Redis 版本是 4.1（`redisson-spring-data-41`）。

**连接池参数需要 `commons-pool2`。** `spring.data.redis.lettuce.pool.*` 这几个参数只有类路径下存在 `commons-pool2` 时才会被采用，否则会被忽略，而且不会有任何提示。

### 3.4 连接与业务参数

`06-redis/src/main/resources/application.yml`：

```yaml
server:
  port: 8006

spring:
  application:
    name: 06-缓存与分布式锁

  # Redis 连接。Redisson 也会读这里的配置来建自己的客户端
  data:
    redis:
      host: localhost
      port: 6379
      password: abc123
      database: 0
      timeout: 3s
      # Lettuce 默认多条命令复用同一个连接，池化后按连接池取用
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2
          max-wait: 1s

  # 缓存抽象。Boot 4 的缓存自动配置在 spring-boot-cache 模块里，属性前缀仍是 spring.cache
  cache:
    type: redis
    redis:
      time-to-live: 10m         # 统一默认过期时间，方法上没写 TTL 时用它
      cache-null-values: true   # 允许缓存 null，配合空值缓存防穿透
      key-prefix: "cache:"      # 缓存 key 统一前缀，便于区分业务 key
      use-key-prefix: true
      enable-statistics: true   # 打开后 Redis 里能读到命中率指标

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/backend_learning?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
    hikari:
      pool-name: redis-demo-pool
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 3000

  # 启动时先建表再灌数据。MySQL 是非内嵌库，必须显式写 always
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
      data-locations: classpath:data.sql
      encoding: UTF-8

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true   # 驼峰映射
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 打印 SQL，方便观察缓存命中时是否还有查询
  global-config:
    banner: false
    db-config:
      id-type: auto
      table-underline: true

# 本模块业务参数，集中放一处便于调整
demo:
  cache:
    ttl: 10m            # 手动缓存的默认存活时间
    null-ttl: 2m        # 空值标记的存活时间，通常比正常值短
    jitter-seconds: 30  # TTL 随机抖动幅度，避免大批 key 同时过期
  lock:
    wait-seconds: 3     # 抢锁最长等待
    lease-seconds: 10   # 持锁自动释放时间
  limit:
    window-seconds: 60  # 限流窗口
    max-requests: 5     # 窗口内允许的请求数
```

`demo.*` 这一段通过 `config/DemoProperties.java` 绑定成对象，比散落在各个类里的常量好调。

---

## 4. 动手实战

### 步骤 1：序列化——不配的话，Redis 里全是乱码

`RedisTemplate` 默认用 JDK 序列化，key 和 value 写进 Redis 之后都是二进制，用 `redis-cli` 完全看不清，跨语言也无法读取。所以第一步先把序列化换掉：

```java
@Configuration
public class RedisConfig {

    /**
     * 缓存值统一用 JSON 序列化。
     */
    private GenericJacksonJsonRedisSerializer jsonSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("top.mqxu.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.math.")
                        .build())
                .enableSpringCacheNullValueSupport()
                .customize(builder -> builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS))
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJacksonJsonRedisSerializer valueSerializer = jsonSerializer();

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        return template;
    }
}
```

这里有三处需要解释。

**为什么序列化器要用 builder 而不是 `new`。** Spring Data Redis 4.1 提供了两个 JSON 序列化器：`GenericJackson2JsonRedisSerializer` 基于 Jackson 2，为了兼容保留；`GenericJacksonJsonRedisSerializer` 基于 Jackson 3，是 Boot 4 环境下的选择。后者没有可用的单参构造器，要通过 `builder()` 创建。

**为什么必须打开 default typing。** `RedisTemplate<String, Object>` 写入时只知道值是个 `Object`，读出来却要还原成原来的类型。类型信息不写进 JSON，反序列化时无从判断，结果只能得到一个 `LinkedHashMap`，强转成 `Goods` 会抛 `ClassCastException`。打开后写进 Redis 的值长这样：

```
{"@class":"top.mqxu.redis.entity.Goods","category":"数码","createTime":"2026-10-14T08:00:29","id":1,"name":"机械键盘 K87","price":["java.math.BigDecimal",399.00],"sales":860,"status":1,"stock":120,"updateTime":"2026-10-14T08:00:29"}
```

`@class` 就是类型信息。附带的安全问题也在这里：类型信息是可以被篡改的，如果反序列化时不设限制，往 Redis 里写一段精心构造的 JSON 就可能让应用加载任意类。所以用 `BasicPolymorphicTypeValidator` 把允许还原的类型限制在几类前缀内：本项目的 `top.mqxu.`，以及 `java.lang.`、`java.util.`、`java.math.` 这些值类型与集合。这里没有用 `allowIfBaseType(Object.class)`，那个写法等价于放行一切，起不到限制作用。

`java.lang.` 这一项不能省。往 `Map<String, Object>` 这类声明类型是 `Object` 的结构里放一个 `Long` 或 `String`，Jackson 会把它们写成 `["java.lang.Long", 1791936436000]` 这种带类型信息的形式，白名单里没有 `java.lang.` 就会在反序列化时抛 `InvalidTypeIdException`，报“subtype class not allowed”。

**为什么要 `enableSpringCacheNullValueSupport()`。** 声明式缓存打开 `cache-null-values` 后，会把 `null` 包装成 `NullValue` 对象存进 Redis。默认的序列化器不认识这个类型，会直接抛异常。这个方法会注册对应的处理。需要留意的是，Spring 写空值用的是 JDK 序列化，不是 JSON，所以缓存里的空值用 `redis-cli` 看到的是一串二进制，这与普通值不同。

时间格式那一行也值得说明。Jackson 3 把日期相关的开关从 `SerializationFeature` 挪到了 `DateTimeFeature`，沿用旧写法会编译不过。

### 步骤 2：Redis 数据类型的操作与接口

五种数据类型在 `RedisController` 里各给了一个接口。这里只用 `StringRedisTemplate`，因为演示的都是字符串形式的简单值，无需序列化配置。

```java
@RestController
@RequestMapping("/redis")
@RequiredArgsConstructor
public class RedisController {

    private final StringRedisTemplate stringRedisTemplate;

    /** String：最基础的类型，文本、数字、序列化后的 JSON 都放它里面 */
    @PostMapping("/string")
    public Map<String, Object> setString(@RequestParam String key,
                                        @RequestParam String value,
                                        @RequestParam(defaultValue = "600") long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("value", value);
        result.put("ttlSeconds", stringRedisTemplate.getExpire(key));
        return result;
    }

    /** Hash：一个 key 下挂多个字段，适合只读写对象的某几个属性 */
    @PostMapping("/hash")
    public Map<Object, Object> putHash(@RequestParam String key, @RequestParam Map<String, String> fields) {
        // RequestParam 收到的 Map 里包含 key 这个参数本身，写进 hash 前先剔掉
        Map<String, String> values = new LinkedHashMap<>(fields);
        values.remove("key");
        stringRedisTemplate.opsForHash().putAll(key, values);
        return stringRedisTemplate.opsForHash().entries(key);
    }

    /** ZSet：元素带分数，写入后天然按分数排序 */
    @GetMapping("/zset/top")
    public List<Map<String, Object>> topZSet(@RequestParam String key,
                                             @RequestParam(defaultValue = "5") int top) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, top - 1L);
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
                .map(tuple -> Map.<String, Object>of("member", tuple.getValue(), "score", tuple.getScore()))
                .toList();
    }

    /** 计数器：INCR 在 Redis 里是单线程原子的，适合统计访问量、做限流 */
    @PostMapping("/counter/incr")
    public Map<String, Object> increment(@RequestParam String key,
                                         @RequestParam(defaultValue = "1") long delta) {
        Long current = stringRedisTemplate.opsForValue().increment(key, delta);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("current", current);
        return result;
    }
}
```

`/redis/hash` 里那句 `values.remove("key")` 不是多余的。`@RequestParam Map<String, String>` 会把该请求的全部参数收进 Map，包括 `key` 本身，不去掉的话 hash 里会多出一个叫 `key` 的字段。

`getExpire` 的返回值有约定：正数是剩余秒数，`-1` 表示没有设置过期时间，`-2` 表示 key 不存在。

### 步骤 3：Cache-Aside 手动维护缓存

先建表和示例数据。`schema.sql`：

```sql
DROP TABLE IF EXISTS t_goods;

CREATE TABLE t_goods
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100)    NOT NULL COMMENT '商品名称',
    category    VARCHAR(32)     NOT NULL COMMENT '分类',
    price       DECIMAL(10, 2)  NOT NULL COMMENT '价格',
    stock       INT             NOT NULL DEFAULT 0 COMMENT '库存',
    sales       INT             NOT NULL DEFAULT 0 COMMENT '销量',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 上架，0 下架',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_category (category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品表';
```

`data.sql` 插入 10 条商品，覆盖数码、家居、图书、运动、食品五个分类，销量各不相同，后面的排行榜直接用它。

缓存逻辑写在 `GoodsCacheService` 里。核心是读的时候先看缓存，未命中再查库并回填：

```java
public CacheResult<Goods> getById(Long id) {
    long start = System.nanoTime();
    String key = KEY_PREFIX + id;

    Object cached = redisTemplate.opsForValue().get(key);
    if (cached != null) {
        return fromCache(cached, start);
    }

    Goods goods = goodsMapper.selectById(id);
    cacheIt(key, goods);
    return new CacheResult<>(goods, "db", costMs(start));
}

private void cacheIt(String key, Goods goods) {
    if (goods == null) {
        // 查不到也写一条标记，避免同一个不存在的 id 反复打库
        redisTemplate.opsForValue().set(key, NULL_PLACEHOLDER, properties.getCache().getNullTtl());
        return;
    }
    redisTemplate.opsForValue().set(key, goods, ttlWithJitter());
}
```

`CacheResult` 里带一个 `source` 字段，取值有 `db`、`cache`、`cache-empty`、`db-fallback` 几种，这是为了在 curl 输出里直接看出这次请求是打库还是命中缓存，不用去数日志。

**空值防穿透**：`cacheIt` 在 `goods == null` 时写入字符串 `"NULL"`，存活 2 分钟。为什么比正常值短？因为商品可能是先创建、后查询，空值标记留太久会挡住新数据。

**TTL 抖动防雪崩**：

```java
private Duration ttlWithJitter() {
    long base = properties.getCache().getTtl().toSeconds();
    long jitter = ThreadLocalRandom.current().nextLong(properties.getCache().getJitterSeconds() + 1L);
    return Duration.ofSeconds(base + jitter);
}
```

**写库后删缓存**：

```java
public boolean updatePrice(Long id, BigDecimal price) {
    Goods goods = new Goods();
    goods.setId(id);
    goods.setPrice(price);
    boolean updated = goodsMapper.updateById(goods) > 0;
    if (updated) {
        evict(id);
    }
    return updated;
}
```

### 步骤 4：互斥锁防击穿

热点 key 刚过期的瞬间，几十个请求会同时发现缓存没有，一起涌向数据库。做法是未命中时先抢一把锁，只有抢到的那个请求回源并回填：

```java
public CacheResult<Goods> getByIdWithMutex(Long id) {
    long start = System.nanoTime();
    String key = KEY_PREFIX + id;

    Object cached = redisTemplate.opsForValue().get(key);
    if (cached != null) {
        return fromCache(cached, start);
    }

    String lockKey = REBUILD_LOCK_PREFIX + id;
    Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
    if (!Boolean.TRUE.equals(locked)) {
        // 没抢到锁，说明有别的请求正在重建，等一小会儿再读缓存
        sleepQuietly(80);
        Object retried = redisTemplate.opsForValue().get(key);
        if (retried != null) {
            return fromCache(retried, start);
        }
        return new CacheResult<>(goodsMapper.selectById(id), "db-fallback", costMs(start));
    }

    try {
        // 拿到锁后复查一次：可能上一个持锁线程刚好写完缓存
        Object checked = redisTemplate.opsForValue().get(key);
        if (checked != null) {
            return fromCache(checked, start);
        }
        Goods goods = goodsMapper.selectById(id);
        cacheIt(key, goods);
        return new CacheResult<>(goods, "db", costMs(start));
    } finally {
        // 这里的锁只服务于重建，简单删除即可；对外的业务锁要求比对唯一值后再删，见步骤 8
        stringRedisTemplate.delete(lockKey);
    }
}
```

三个细节：拿到锁之后要再查一次缓存，因为可能在等锁期间别的线程已经写好了；没抢到锁的请求不要直接失败，短暂等待后重读缓存；这里删锁用的是直接 `delete`，因为锁的值固定是 `"1"`，不区分持有者，只用于重建，无所谓误删。对外提供服务的业务锁必须比对持有者，见步骤 8。

### 步骤 5：Spring 缓存注解

手动写缓存样板代码太多，Spring 的缓存抽象可以把读写缓存的逻辑从业务方法里拿掉。注意方法所在的类必须是 Spring 容器里的 Bean，且从外部调用，注解才生效。

```java
@Service
@RequiredArgsConstructor
public class GoodsSpringCacheService {

    private final GoodsMapper goodsMapper;

    /**
     * 命中缓存时方法体不会执行。
     * 查不到数据会返回 null，配合 spring.cache.redis.cache-null-values=true 把 null 也存进缓存，
     * 不需要额外写空值标记就能起到防穿透的作用。
     */
    @Cacheable(cacheNames = "goods", key = "#id")
    public Goods getById(Long id) {
        return goodsMapper.selectById(id);
    }

    /** 查列表，结果放在 goods 区域下固定的 all 键上 */
    @Cacheable(cacheNames = "goods", key = "'all'")
    public List<Goods> listAll() {
        return goodsMapper.selectList(null);
    }

    /** 更新数据库，并把新值写回缓存 */
    @CachePut(cacheNames = "goods", key = "#id")
    public Goods updatePrice(Long id, BigDecimal price) {
        Goods update = new Goods();
        update.setId(id);
        update.setPrice(price);
        if (goodsMapper.updateById(update) == 0) {
            return null;
        }
        return goodsMapper.selectById(id);
    }

    /** 同时清掉单个商品与列表两处缓存 */
    @Caching(evict = {
            @CacheEvict(cacheNames = "goods", key = "#id"),
            @CacheEvict(cacheNames = "goods", key = "'all'")
    })
    public void evictDetailAndList(Long id) {
        // 改动了商品数据，明细与列表都要失效
    }

    /** 清空整个 goods 缓存区域 */
    @CacheEvict(cacheNames = "goods", allEntries = true)
    public void evictAll() {
        // 只负责清缓存
    }
}
```

三个注解的区别：

| 注解 | 执行时机 | 对缓存的作用 |
| --- | --- | --- |
| `@Cacheable` | 调用前先查缓存，命中就不执行方法 | 未命中时把返回值写入 |
| `@CachePut` | 一定执行方法 | 把返回值覆盖写入 |
| `@CacheEvict` | 方法执行后（默认） | 删除指定 key 或整个区域 |

`@Cacheable` 与 `@CachePut` 容易混淆：前者是“有就不算”，后者是“算完就覆盖”。标注了 `@CachePut` 的方法一定会执行，所以不能拿它做查询。

缓存区域 `goods` 下会有两类 key，前缀由 `spring.cache.redis.key-prefix` 决定：

```
cache:goods::3       # 单个商品，key 是 id
cache:goods::all     # 商品列表，key 是固定字符串 all
```

### 步骤 6：缓存配置与两个必须处理的细节

声明式缓存的值也要序列化成 JSON，并且 TTL、空值策略要从配置文件读到。写一个 `RedisCacheConfiguration` Bean：

```java
@Bean
public RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties) {
    GenericJacksonJsonRedisSerializer valueSerializer = jsonSerializer();

    RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

    CacheProperties.Redis redis = cacheProperties.getRedis();
    if (redis.getTimeToLive() != null) {
        config = config.entryTtl(redis.getTimeToLive());
    }
    if (redis.getKeyPrefix() != null) {
        config = config.prefixCacheNameWith(redis.getKeyPrefix());
    }
    if (!redis.isCacheNullValues()) {
        config = config.disableCachingNullValues();
    }
    return config;
}
```

这里有个反直觉的地方：只要容器里存在这个 Bean，Boot 的自动配置就会整体采用它，不再读 `spring.cache.redis.*` 的默认值。所以配置里写的时间、前缀、空值开关都要手动搬过来，漏一个就不生效，而不会有任何警告。

另一个细节是缓存的写入默认是异步的。Spring Data Redis 4 把缓存的写入和删除交给后台线程，调用方不等命令真正执行完就返回。演示时改完数据紧接着读缓存，结果会变得不确定。所以显式改成同步：

```java
@Bean
public RedisCacheManagerBuilderCustomizer immediateWriteCacheWriter(RedisConnectionFactory connectionFactory) {
    return builder -> builder.cacheWriter(RedisCacheWriter.create(connectionFactory,
            configurer -> configurer.immediateWrites().collectStatistics()));
}
```

如果确认业务能接受异步写入，去掉这个 Bean 即可。

### 步骤 7：Redisson 分布式锁

用锁扣库存：

```java
public boolean deductWithLock(Long id, int quantity) {
    RLock lock = redissonClient.getLock(LOCK_PREFIX + id);
    try {
        // 最多等 waitSeconds，拿到锁后 leaseSeconds 自动释放，进程崩了也不会一直占着
        boolean locked = lock.tryLock(properties.getLock().getWaitSeconds(),
                properties.getLock().getLeaseSeconds(), TimeUnit.SECONDS);
        if (!locked) {
            return false;
        }
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || goods.getStock() < quantity) {
            return false;
        }
        Goods update = new Goods();
        update.setId(id);
        update.setStock(goods.getStock() - quantity);
        return goodsMapper.updateById(update) > 0;
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
    } finally {
        // 只有持锁的线程能释放自己的锁
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

`tryLock` 的两个参数含义不同：第一个是获取锁的最长等待时间，超时返回 `false`；第二个是锁的自动释放时间，单位秒。不传第二个参数时 Redisson 会启用看门狗，每 10 秒给锁续期一次，直到进程退出或主动释放。这里显式传了时间，看门狗不介入，适合锁内逻辑很短的场景。

### 步骤 8：手写 SETNX 锁与 Lua 解锁

为了看清 Redisson 省掉了什么，模块里也保留了一份手写实现：

```java
/** 释放锁时先比对持有者标记，值对得上才删，避免误删别人持有的锁 */
private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);

@PostMapping("/buy/setnx")
public Map<String, Object> buyWithSetNx(@RequestParam Long id,
                                        @RequestParam(defaultValue = "1") int quantity) {
    String lockKey = SETNX_LOCK_PREFIX + id;
    String token = UUID.randomUUID().toString();
    Boolean locked = stringRedisTemplate.opsForValue()
            .setIfAbsent(lockKey, token, Duration.ofSeconds(10));
    if (!Boolean.TRUE.equals(locked)) {
        // 抢不到锁直接返回，不做等待
        return Map.of("strategy", "setnx", "success", false, "reason", "没抢到锁，已有请求在处理");
    }
    try {
        return result(id, stockService.deduct(id, quantity), "setnx");
    } finally {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), token);
    }
}
```

与 Redisson 的差别在实际运行结果里很明显：

| 对比项 | Redisson `tryLock` | 手写 SETNX |
| --- | --- | --- |
| 抢不到锁 | 在等待时间内排队，轮到就执行 | 立即返回失败 |
| 解锁安全 | 框架保证 | 要自己用 Lua 比对持有者 |
| 锁续期 | 看门狗自动续 | 过期时间写死，业务超时就会提前丢锁 |
| 可重入 | 支持 | 不支持 |

`redis.call('get', ...) == ARGV[1]` 的比对与 `del` 在同一个 Lua 脚本里执行，Redis 执行脚本期间不会插入其他命令，所以这两步是原子的。如果拆成先 `get` 再 `del` 两条命令，中间可能正好锁过期、别人拿到新锁，删掉的就是别人的锁。

### 步骤 9：ZSet 排行榜

排行数据放在 ZSet 里，商品 id 当成员，销量当分数。

```java
/** 把数据库里的销量灌进 ZSet，做一次预热 */
@PostMapping("/warmup")
public Map<String, Object> warmUp() {
    List<Goods> goodsList = goodsService.listAll();
    for (Goods goods : goodsList) {
        stringRedisTemplate.opsForZSet()
                .add(SALES_RANK_KEY, String.valueOf(goods.getId()), goods.getSales());
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("warmed", goodsList.size());
    result.put("size", stringRedisTemplate.opsForZSet().size(SALES_RANK_KEY));
    return result;
}

/** 给某个商品加分，ZSet 会自动把它挪到正确位置 */
@PostMapping("/increase")
public Map<String, Object> increase(@RequestParam Long id,
                                    @RequestParam(defaultValue = "1") double delta) {
    Double score = stringRedisTemplate.opsForZSet().incrementScore(SALES_RANK_KEY, String.valueOf(id), delta);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("goodsId", id);
    result.put("score", score);
    result.put("rank", rankOf(id));
    return result;
}
```

`incrementScore` 是原子操作，多个请求同时给同一商品加分不会丢。查名次用 `reverseRank`，返回值从 0 开始，所以要加 1；不在榜上返回 `null`，接口转成 `-1`。

### 步骤 10：计数器限流

固定窗口限流，把时间按窗口长度切成一段，每段一个计数器，key 里带上窗口编号：

```java
@GetMapping("/access")
public Map<String, Object> access(@RequestParam(defaultValue = "guest") String user) {
    int windowSeconds = properties.getLimit().getWindowSeconds();
    int maxRequests = properties.getLimit().getMaxRequests();

    long windowIndex = System.currentTimeMillis() / 1000 / windowSeconds;
    String key = "limit:access:" + user + ":" + windowIndex;

    Long count = stringRedisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
        // 只在计数器刚建出来时设过期，之后每次自增不再刷新，保证窗口按自然时间滚动
        stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
    }
    // ......
    result.put("allowed", count != null && count <= maxRequests);
    return result;
}
```

过期时间只在计数器刚创建时设置，这一点不能省。如果每次自增都刷新过期时间，只要请求不断，计数器就永不过期，限流会退化成“从第一次请求起总共只放行 5 次”。

固定窗口有个固有缺陷：窗口边界处可能通过两倍流量，比如第 59 秒来 5 个、第 61 秒又来 5 个。要避免就得换滑动窗口或令牌桶，`12-test` 之外的课程不再展开。

---

## 5. 完整代码与目录结构

```
06-redis
└── src
    ├── main
    │   ├── java/top/mqxu/redis
    │   │   ├── RedisApplication.java          # @EnableCaching + @EnableConfigurationProperties
    │   │   ├── config
    │   │   │   ├── RedisConfig.java           # 序列化 + 缓存配置 + 同步写入
    │   │   │   └── DemoProperties.java        # demo.* 的绑定
    │   │   ├── controller
    │   │   │   ├── RedisController.java       # 五种数据类型与计数器
    │   │   │   ├── CacheController.java       # 直查库 / 手动缓存 / 注解缓存 对照
    │   │   │   ├── LockController.java        # 三种扣库存写法对照
    │   │   │   ├── RankController.java        # ZSet 排行榜
    │   │   │   └── LimitController.java       # 固定窗口限流
    │   │   ├── service
    │   │   │   ├── GoodsService.java          # 只查库，作为对照
    │   │   │   ├── GoodsCacheService.java     # 手动缓存：空值 / 互斥锁 / TTL 抖动
    │   │   │   ├── GoodsSpringCacheService.java # 注解缓存
    │   │   │   └── StockService.java          # 扣库存：不加锁 / Redisson / 直接扣
    │   │   ├── entity/Goods.java
    │   │   ├── mapper/GoodsMapper.java
    │   │   └── dto/CacheResult.java           # 带 source 与耗时的返回体
    │   └── resources
    │       ├── application.yml                # Redis / 缓存 / 数据源 / demo.* 参数
    │       ├── schema.sql                     # t_goods 建表
    │       └── data.sql                       # 10 条商品
    └── test/java/top/mqxu/redis
        ├── RedisTypeOpsTest.java              # 9 个用例，五种数据类型接口
        ├── GoodsCacheTest.java                # 13 个用例，缓存三种做法
        ├── LockAndLimitTest.java              # 10 个用例，并发扣库存、限流、排行榜
        ├── PracticeTest.java                  # 5 个用例，随堂练习参考答案
        └── HomeworkTest.java                  # 4 个用例，课后练习参考答案
```

---

## 6. 运行与验证

### 6.1 启动

先确认 Redis 与 MySQL 都在跑：

```bash
redis-cli --no-auth-warning -a abc123 ping
# PONG

mysql -h127.0.0.1 -P3306 -uroot -p123456 -e "select count(*) from backend_learning.t_goods;"
```

再启动模块：

```bash
mvn -pl 06-redis spring-boot:run
```

端口 `8006`。启动时会自动执行 `schema.sql` 与 `data.sql`，把 `t_goods` 重置成 10 条商品。脚本里有 `DROP TABLE`，所以 DevTools 热重启同样会重置数据，改完代码发现商品数据和之前不一样就是这个原因。

下面的命令都按 `8006` 写，改过端口就把 `BASE` 一起改。先定义它省点打字：

```bash
BASE=http://127.0.0.1:8006
```

本机 `curl` 走系统代理，命令里的 `--noproxy '*'` 用来绕过它。

6.2 到 6.5 把 34 个接口逐个走一遍，每条都给出命令和实际返回。`costMs`、`ttlSeconds` 这类数值每次执行都会变，看 `source` 与状态字段即可。

### 6.2 数据类型接口

13 个接口都挂在 `/redis` 下，是五种数据结构加一个计数器的直接操作。

**String：`POST /redis/string` 写入，`GET /redis/string` 读取**

```bash
curl -s -X POST "$BASE/redis/string?key=demo:name&value=mqxu&ttlSeconds=120"
# {"key":"demo:name","value":"mqxu","ttlSeconds":120}

curl -s "$BASE/redis/string?key=demo:name"
# mqxu
```

返回里的 `ttlSeconds` 是写入后立刻回查的剩余秒数，参数不写时默认 600 秒。key 不存在时读到的是空响应体。

**查剩余过期时间：`GET /redis/ttl`**

```bash
curl -s "$BASE/redis/ttl?key=demo:name"
# {"key":"demo:name","ttlSeconds":118}
```

`getExpire` 的返回值有三种：正数是剩余秒数，`-1` 表示 key 存在但没设过期时间，`-2` 表示 key 不存在。

**删 key：`DELETE /redis/key`**

```bash
curl -s -X DELETE "$BASE/redis/key?key=demo:name"
# true

curl -s -X DELETE "$BASE/redis/key?key=nobody"
# false

curl -s "$BASE/redis/ttl?key=demo:name"
# {"key":"demo:name","ttlSeconds":-2}
```

`true` 表示删掉了一个 key，`false` 表示 key 本来就不存在。删完再查一次 TTL 变成 `-2`，可以确认真的没了。

**Hash：`POST /redis/hash` 写入，`GET /redis/hash` 读取**

```bash
curl -s -X POST "$BASE/redis/hash?key=demo:user:1&name=tao&role=teacher&city=nanjing"
# {"name":"tao","role":"teacher","city":"nanjing"}

curl -s "$BASE/redis/hash?key=demo:user:1"
# {"name":"tao","role":"teacher","city":"nanjing"}
```

字段名直接写在查询参数里，写几个存几个。返回值里不会出现 `key` 字段，原因见步骤 2。同一个 key 再写一次是合并，原有字段保留：

```bash
curl -s -X POST -G "$BASE/redis/hash" \
  --data-urlencode 'key=demo:user:2' --data-urlencode 'name=陶老师'
# {"name":"陶老师"}

curl -s -X POST -G "$BASE/redis/hash" \
  --data-urlencode 'key=demo:user:2' --data-urlencode 'city=南京'
# {"name":"陶老师","city":"南京"}
```

中文值要先做 URL 编码，否则 Tomcat 会以 400 拒绝，用 `-G` 配合 `--data-urlencode` 最省事。

**List：`POST /redis/list` 写入，`GET /redis/list` 读取**

```bash
curl -s -X POST "$BASE/redis/list?key=demo:news&values=a&values=b&values=c"
# ["a","b","c"]

curl -s -X POST "$BASE/redis/list?key=demo:news&values=d"
# ["a","b","c","d"]

curl -s "$BASE/redis/list?key=demo:news"
# ["a","b","c","d"]
```

`values` 写几次就写入几个元素，代码里用的是 `rightPushAll`，从右端追加，所以第二次 POST 的 `d` 排在末尾。读取用 `range(key, 0, -1)` 取全部。

**Set：`POST /redis/set` 写入，`GET /redis/set/intersect` 求交集**

```bash
curl -s -X POST "$BASE/redis/set?key=demo:u1:like&values=1&values=2&values=3"
# ["1","2","3"]

curl -s -X POST "$BASE/redis/set?key=demo:u2:like&values=2&values=3&values=4"
# ["2","3","4"]

curl -s "$BASE/redis/set/intersect?key1=demo:u1:like&key2=demo:u2:like"
# ["2","3"]
```

同一个值重复写不会增加元素，返回顺序与写入顺序无关，这是 Set 无序不重复的特性。交集接口用来做“共同关注”这类查询。

**ZSet：`POST /redis/zset` 写入，`GET /redis/zset/top` 取前 N**

```bash
curl -s -X POST "$BASE/redis/zset?key=demo:score&member=bob&score=95"
# {"member":"bob","score":95.0,"size":1}

curl -s -X POST "$BASE/redis/zset?key=demo:score&member=alice&score=88"
# {"member":"alice","score":88.0,"size":2}

curl -s "$BASE/redis/zset/top?key=demo:score&top=3"
# [{"member":"bob","score":95.0},{"member":"alice","score":88.0}]
```

`size` 是写入后集合里的成员个数。`top` 取 3 但只有 2 个成员时就返回 2 条，不会补空。同一个 `member` 再写一次是覆盖分数，不是新增成员。分数相同时按成员名排序。

**计数器：`POST /redis/counter/incr`**

```bash
curl -s -X POST "$BASE/redis/counter/incr?key=demo:pv&delta=5"
# {"key":"demo:pv","current":5}

curl -s -X POST "$BASE/redis/counter/incr?key=demo:pv"
# {"key":"demo:pv","current":6}
```

`delta` 不写时默认加 1。key 不存在时按 0 起算，所以第一次就返回 5。自增在 Redis 里是原子的，步骤 10 的限流就是靠它。

这一组对应 `RedisTypeOpsTest`，可以只跑它：

```bash
mvn -pl 06-redis test -Dtest=RedisTypeOpsTest
```

### 6.3 缓存接口

10 个接口挂在 `/cache/goods` 下，返回体统一是 `{"data":...,"source":"...","costMs":...}`。判断缓存有没有生效看 `source` 就够了：

| source | 含义 |
| --- | --- |
| `db` | 这次查了数据库 |
| `cache` | 命中缓存 |
| `cache-empty` | 命中的是空值标记，库里确实没有这条记录 |
| `db-fallback` | 没抢到重建锁、等了一下还是没读到缓存，退回查库 |
| `cache-invalid` | 缓存里的值不是 `Goods`，序列化配置有问题时会看到 |
| `anno（是否命中看日志）` | 注解缓存，命中与否只能看日志 |
| `anno-put` | 注解缓存的写入路径 |

**直查库：`GET /cache/goods/db/{id}`**

```bash
curl -s "$BASE/cache/goods/db/1"
# {"data":{"category":"数码","createTime":"2026-10-14T08:46:33","id":1,"name":"机械键盘 K87","price":399.00,"sales":860,"status":1,"stock":120,"updateTime":"2026-10-14T08:46:33"},"source":"db","costMs":2.922625}

curl -s "$BASE/cache/goods/db/1"
# {...,"source":"db","costMs":2.176459}
```

连查两次都是 `db`，这条路径不碰缓存，作用是给后面的接口留一个耗时对照。

**手动缓存：`GET /cache/goods/manual/{id}`**

先清一次缓存再连查两次：

```bash
curl -s -X DELETE "$BASE/cache/goods/manual/1/cache"
# false

curl -s "$BASE/cache/goods/manual/1"
# {"data":{...},"source":"db","costMs":9.7415}

curl -s "$BASE/cache/goods/manual/1"
# {"data":{...},"source":"cache","costMs":26.199708}
```

第一次返回 `false` 是因为缓存里本来就没有这个 key。第二次 `source` 变成 `cache`，说明回填生效了。`costMs` 不是每次都变小，本地 MySQL 查一条也就几毫秒，缓存的价值要在真实的网络往返下才明显。

**空值防穿透：查一个不存在的 id**

```bash
curl -s "$BASE/cache/goods/manual/999"
# {"data":null,"source":"db","costMs":3.838625}

curl -s "$BASE/cache/goods/manual/999"
# {"data":null,"source":"cache-empty","costMs":3.234875}
```

第二次是 `cache-empty`，说明“库里没有这条记录”也被缓存下来了。空值标记的 TTL 比正常值短，`demo.cache.null-ttl` 是 2 分钟，正常的 `demo.cache.ttl` 是 10 分钟，两者都加了 0 到 30 秒的随机抖动，直接查 Redis 能看到：

```bash
redis-cli --no-auth-warning -a abc123 GET goods:detail:98888
# "NULL"

redis-cli --no-auth-warning -a abc123 TTL goods:detail:98888
# (integer) 120

redis-cli --no-auth-warning -a abc123 TTL goods:detail:2
# (integer) 605
```

**互斥锁防击穿：`GET /cache/goods/manual-mutex/{id}`**

```bash
curl -s -X DELETE "$BASE/cache/goods/manual/1/cache"
# true

curl -s "$BASE/cache/goods/manual-mutex/1"
# {"data":{...},"source":"db","costMs":3.953958}

curl -s "$BASE/cache/goods/manual-mutex/1"
# {"data":{...},"source":"cache","costMs":0.680125}
```

单发请求看不出它和 `manual` 的区别，区别在缓存刚失效、多个请求同时进来的时候。清掉缓存后并发发 10 个请求，数一下控制台日志里 `Parameters: 3(Long)` 出现了几次：

```bash
curl -s -X DELETE "$BASE/cache/goods/manual/3/cache"
seq 1 10 | xargs -P 10 -I{} curl -s --noproxy '*' "$BASE/cache/goods/manual/3" > /tmp/a.txt
grep -c 'Parameters: 3(Long)' 控制台日志
```

`manual` 无锁时 10 个请求有 5 个打到了库里，`manual-mutex` 只有 1 个，其余 9 个在等锁期间重读缓存拿到了值。互斥锁的作用就是把同一时刻压在数据库上的请求从 5 个收成 1 个。

**注解缓存：`GET /cache/goods/anno/{id}` 与 `GET /cache/goods/anno/list`**

```bash
curl -s -X DELETE "$BASE/cache/goods/anno"
# 已清空 goods 缓存区域

curl -s "$BASE/cache/goods/anno/4"
# {"data":{...},"source":"anno（是否命中看日志）","costMs":20.5275}

curl -s "$BASE/cache/goods/anno/4"
# {"data":{...},"source":"anno（是否命中看日志）","costMs":2.830875}
```

注解方式拿不到“这次有没有命中”的标记，只能看两处。一是耗时，第一次 20ms、第二次 2.8ms；二是控制台 SQL 日志，`application.yml` 里开了 `log-impl: StdOutImpl`，命中缓存的那次不会打印 `Preparing: SELECT ...`。连续两次请求一个不存在的 id，日志里只有一次它的查询：

```bash
curl -s "$BASE/cache/goods/anno/77777"
curl -s "$BASE/cache/goods/anno/77777"
grep -c 'Parameters: 77777(Long)' 控制台日志
# 1
```

列表缓存是另一个 key：

```bash
curl -s "$BASE/cache/goods/anno/list"
# {"data":[{"category":"数码",...,"id":1,...},{...},...],"source":"anno（是否命中看日志）","costMs":39.579792}
```

Redis 里能看到这两个 key 和值的形态：

```bash
redis-cli --no-auth-warning -a abc123 --scan --pattern 'cache:goods*'
# "cache:goods::4"
# "cache:goods::all"

redis-cli --no-auth-warning -a abc123 --no-raw GET 'cache:goods::4'
# "{\"@class\":\"top.mqxu.redis.entity.Goods\",\"category\":\"数码\",\"createTime\":\"2026-10-14T08:46:33\",\"id\":4,...}"

redis-cli --no-auth-warning -a abc123 --no-raw GET 'cache:goods::all'
# "[\"java.util.ArrayList\",[{\"@class\":\"top.mqxu.redis.entity.Goods\",...}]"
```

单条是 `Goods` 的 JSON，列表是整个 `ArrayList` 的 JSON，都带 `@class` 类型信息，所以读回来能还原成原来的类型。两个 key 的 TTL 都是 10 分钟，来自 `spring.cache.redis.time-to-live`。

空值走的是另一条路。`spring.cache.redis.cache-null-values` 打开后 Spring 用内部的 `NullValue` 对象做标记，没有经过 JSON 序列化，所以 `redis-cli` 里看到的是二进制：

```bash
curl -s "$BASE/cache/goods/anno/66666"
redis-cli --no-auth-warning -a abc123 GET 'cache:goods::66666'
# 一串乱码，属正常
```

**改价：`PUT /cache/goods/{id}/price` 与 `PUT /cache/goods/{id}/price-anno`**

```bash
curl -s -X PUT "$BASE/cache/goods/5/price?price=199.00"
# {"data":{...,"price":199.00,"updateTime":"2026-10-14T08:47:42"},"source":"db","costMs":4.027292}
```

这条路径先更新数据库、再删缓存，返回的是删完缓存重新读的结果，所以 `source` 又是 `db`。注解版换 `@CachePut`，不删旧值而是把新值写回缓存：

```bash
curl -s -X PUT "$BASE/cache/goods/6/price-anno?price=59.90"
# {"data":{...,"price":59.90,...},"source":"anno-put","costMs":8.634416}
```

**三种清缓存：`DELETE /cache/goods/manual/{id}/cache`、`DELETE /cache/goods/anno/{id}`、`DELETE /cache/goods/anno`**

```bash
curl -s -X DELETE "$BASE/cache/goods/manual/1/cache"
# true          # 删掉了一个 key

curl -s -X DELETE "$BASE/cache/goods/manual/1/cache"
# false         # 缓存本来就不存在

curl -s -X DELETE "$BASE/cache/goods/anno/4"
# 已清掉 goods 区域下 id=4 与 all 两处缓存

curl -s -X DELETE "$BASE/cache/goods/anno"
# 已清空 goods 缓存区域
```

`DELETE /cache/goods/anno/{id}` 会把列表缓存一起清掉，因为列表里也含这个商品。改价后只清单个 key 的话，列表接口返回的还是旧价，这一点容易漏。

### 6.4 锁与库存接口

6 个接口挂在 `/lock` 下，用来做并发扣库存的对照实验。

**重置库存：`POST /lock/reset?id=&stock=`**

```bash
curl -s -X POST "$BASE/lock/reset?id=1&stock=10"
# {"id":1,"stock":10}
```

每次实验前都重置一次，否则上一轮的库存会带进下一轮。

**查库存：`GET /lock/stock/{id}`**

```bash
curl -s "$BASE/lock/stock/1"
# {"category":"数码","createTime":"2026-10-14T08:46:33","id":1,"name":"机械键盘 K87","price":399.00,"sales":860,"status":1,"stock":10,"updateTime":"2026-10-14T08:47:50"}
```

这条绕过所有缓存直接查库，所以看到的库存一定是数据库里的真实值。

**三种购买方式：`POST /lock/buy/no-lock`、`/lock/buy/redisson`、`/lock/buy/setnx`**

参数都是 `id` 和 `quantity`（默认 1）。先各发一个请求，看清楚返回值：

```bash
curl -s -X POST "$BASE/lock/buy/no-lock?id=1&quantity=1"
# {"strategy":"no-lock","success":true,"stockLeft":9}

curl -s -X POST "$BASE/lock/buy/redisson?id=1&quantity=1"
# {"strategy":"redisson","success":true,"stockLeft":8}

curl -s -X POST "$BASE/lock/buy/setnx?id=1&quantity=1"
# {"strategy":"setnx","success":true,"stockLeft":7}
```

库存不够时不做扣减：

```bash
curl -s -X POST "$BASE/lock/reset?id=1&stock=5"
# {"id":1,"stock":5}

curl -s -X POST "$BASE/lock/buy/redisson?id=1&quantity=10"
# {"strategy":"redisson","success":false,"stockLeft":5}
```

`setnx` 版还有第三种返回，没抢到锁时不等，直接把原因返回：

```json
{"strategy":"setnx","success":false,"reason":"没抢到锁，已有请求在处理"}
```

这条要两个请求同时进来才会出现，单发看不到。

**查锁的剩余时间：`GET /lock/info?key=`**

```bash
curl -s "$BASE/lock/info?key=lock:goods:buy:1"
# {"key":"lock:goods:buy:1","ttlSeconds":-2}
```

锁已经释放，所以是 `-2`。手动放一个带过期的 key 进去能看到真实值：

```bash
redis-cli --no-auth-warning -a abc123 SET lock:demo 1 EX 30
curl -s "$BASE/lock/info?key=lock:demo"
# {"key":"lock:demo","ttlSeconds":30}
redis-cli --no-auth-warning -a abc123 DEL lock:demo
```

**并发实验**

单发请求时三种写法看起来都对，差别在并发下才显出来。把库存重置成 10，用 `xargs -P` 同时发 20 个请求，每个请求买 1 件：

```bash
curl -s -X POST "$BASE/lock/reset?id=1&stock=10"
seq 1 20 | xargs -P 20 -I{} curl -s --noproxy '*' -X POST "$BASE/lock/buy/no-lock?id=1&quantity=1" > /tmp/a.txt
grep -c '"success":true' /tmp/a.txt
curl -s "$BASE/lock/stock/1" | grep -o '"stock":[0-9]*'
```

不加锁这一轮：20 个请求全部返回 `success:true`，库存只从 10 减到 8。账面上卖出 20 件，实际只扣掉 2 件，这就是丢失更新。

```bash
curl -s -X POST "$BASE/lock/reset?id=1&stock=10"
seq 1 20 | xargs -P 20 -I{} curl -s --noproxy '*' -X POST "$BASE/lock/buy/redisson?id=1&quantity=1" > /tmp/b.txt
grep -o '"success":true' /tmp/b.txt | wc -l
grep -o '"success":false' /tmp/b.txt | wc -l
curl -s "$BASE/lock/stock/1" | grep -o '"stock":[0-9]*'
```

Redisson 这一轮：10 个成功、10 个失败，库存正好减到 0。失败的是进临界区时库存已经空了的请求。

```bash
curl -s -X POST "$BASE/lock/reset?id=1&stock=10"
seq 1 20 | xargs -P 20 -I{} curl -s --noproxy '*' -X POST "$BASE/lock/buy/setnx?id=1&quantity=1" > /tmp/c.txt
grep -o '"success":true' /tmp/c.txt | wc -l
grep -o '没抢到锁' /tmp/c.txt | wc -l
curl -s "$BASE/lock/stock/1" | grep -o '"stock":[0-9]*'
```

手写 SETNX 这一轮：4 个成功、16 个返回没抢到锁，库存减到 6。

三组放在一起看：不加锁会超卖；Redisson 不超卖，没抢到锁的请求会在等待时间里排队重试；手写 SETNX 不等锁，抢不到立刻放弃，大量购买请求被直接丢掉。

成功数每次执行都会浮动，`no-lock` 那一组尤其明显，线程调度不同结果就不同。要看的是三者的模式：不加锁时成功数等于请求数、库存减不下去；加锁时成功数等于库存数、剩下的一律失败。

### 6.5 排行榜与限流接口

5 个接口，排行榜挂在 `/rank`，限流挂在 `/limit`。

**预热：`POST /rank/warmup`**

```bash
curl -s -X POST "$BASE/rank/warmup"
# {"warmed":10,"size":10}
```

把库里 10 条商品的销量灌进 ZSet。`warmed` 是读到的商品条数，`size` 是写完后 ZSet 的成员数，两个相等说明都写进去了。重新预热不会重复计数，因为 ZSet 里同一个成员是覆盖分数。

**取前 N 名：`GET /rank/top?top=`**

```bash
curl -s "$BASE/rank/top?top=3"
# [{"rank":1,"goodsId":"6","sales":4300.0,"name":"不锈钢保温杯"},{"rank":2,"goodsId":"5","sales":2100.0,"name":"智能手环 5 代"},{"rank":3,"goodsId":"9","sales":1760.0,"name":"手冲咖啡豆 250g"}]
```

`top` 默认 5，分数从高到低。`name` 是拿 ZSet 里存的 `goodsId` 回查数据库补上的，ZSet 本身只存 id 和分数。

**加分：`POST /rank/increase?id=&delta=`**

```bash
curl -s -X POST "$BASE/rank/increase?id=7&delta=3000"
# {"goodsId":7,"score":3275.0,"rank":2}
```

7 号原来 275 分，加 3000 后是 3275 分，返回时顺带给出新名次。用 `incrementScore` 而不是先读后写，并发加分不会互相覆盖。

**查名次：`GET /rank/rank-of?id=`**

```bash
curl -s "$BASE/rank/rank-of?id=7"
# {"goodsId":7,"rank":2,"sales":3275.0}

curl -s "$BASE/rank/rank-of?id=999"
# {"goodsId":999,"rank":-1,"sales":null}
```

名次从 1 开始算。没上过榜的 id 返回 `rank` 为 `-1`、`sales` 为 `null`。

**限流：`GET /limit/access?user=`**

```bash
for i in 1 2 3 4 5 6; do curl -s "$BASE/limit/access?user=tao"; echo; done
# {"user":"tao","count":1,"maxRequests":5,"windowSeconds":60,"allowed":true,"ttlSeconds":60}
# {"user":"tao","count":2,"maxRequests":5,"windowSeconds":60,"allowed":true,"ttlSeconds":60}
# ...
# {"user":"tao","count":5,"maxRequests":5,"windowSeconds":60,"allowed":true,"ttlSeconds":60}
# {"user":"tao","count":6,"maxRequests":5,"windowSeconds":60,"allowed":false,"ttlSeconds":60}
```

窗口 60 秒、上限 5 次来自 `demo.limit` 里的 `window-seconds` 和 `max-requests`。前 5 次 `allowed` 为 true，第 6 次变 false，`count` 仍然继续涨，因为计数器只负责数、不负责拦。业务代码里拿到 `allowed` 为 false 时返回 429 即可。

计数器按用户隔离，换个 `user` 重新从 1 开始：

```bash
curl -s "$BASE/limit/access?user=someone-else"
# {"user":"someone-else","count":1,"maxRequests":5,"windowSeconds":60,"allowed":true,"ttlSeconds":60}
```

几次请求间隔不到一秒，`ttlSeconds` 都停在 60。这个过期时间只在计数器刚创建时设置，后面的自增不再刷新它，所以窗口按自然时间滚动：等这一分钟过去，同一个用户又能拿到 5 次。

### 6.6 全部接口速查

34 个接口集中列在这里，每个接口的命令与预期返回见 6.2 到 6.5。

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

---

## 7. 常见问题与避坑

**`redis-cli` 里看到的 key 和值是乱码。** 没有配置序列化器，`RedisTemplate` 默认用 JDK 序列化。按步骤 1 换成 JSON。

**读缓存时抛 `ClassCastException`，取出来的是 `LinkedHashMap`。** JSON 里没带类型信息。`GenericJacksonJsonRedisSerializer` 的 builder 要显式调 `enableDefaultTyping(...)`。

**读缓存时抛 `InvalidTypeIdException`，提示 subtype class not allowed。** 类型校验器的白名单太窄。往 `Map<String, Object>` 这类声明类型为 `Object` 的结构里放 `Long`、`String`，会被写成 `["java.lang.Long", ...]` 的形式，白名单里要有 `java.lang.`。

**`@Cacheable` 加了但没生效。** 三种可能：类不是 Spring Bean；方法被同一个类内部调用，绕过了代理；缺 `spring-boot-starter-cache` 依赖。第三种在 Boot 4 上最常见，因为缓存自动配置已经从 `spring-boot-starter-data-redis` 里拆走了。

**自定义了 `RedisCacheConfiguration` 之后，配置文件的 TTL 和前缀都不生效了。** 这是预期行为。容器里出现这个 Bean，Boot 就整体采用它，不再读 `spring.cache.redis.*`，需要在代码里手动搬过来。

**改成主键查询后，`@Cacheable` 的 key 出现双冒号。** `RedisCacheConfiguration` 默认的 key 前缀是 `缓存名::`，再叠加 `spring.cache.redis.key-prefix`，最终形如 `cache:goods::3`。不是 bug。

**缓存 null 值时报序列化异常。** 序列化器要调 `enableSpringCacheNullValueSupport()`，否则不认识 Spring 写入的 `NullValue`。

**`@CacheEvict(allEntries = true)` 之后立刻查 key，发现还在。** Spring Data Redis 4 默认异步执行缓存的写入与删除，命令发出去就返回了，隔几十毫秒才真正生效。按步骤 6 配一个 `RedisCacheManagerBuilderCustomizer` 调 `immediateWrites()` 即可改成同步。

**`spring.data.redis.lettuce.pool.*` 配了没反应。** 类路径缺少 `commons-pool2`，池参数会被静默忽略。

**参数里带中文，Tomcat 返回 400。** 请求行里的非 ASCII 字符必须先 URL 编码。用 `curl -G --data-urlencode`，或在 Postman 里让它自动编码。

**DevTools 热重启后数据没了。** `schema.sql` 里有 `DROP TABLE`，每次上下文刷新都会重建表并重灌数据。要保留数据，把建表语句改成 `CREATE TABLE IF NOT EXISTS`，插入改成 `INSERT IGNORE`。

**`@RequestParam Map<String, String>` 收进来的参数里多了个 `key`。** 该 Map 包含请求的全部参数，包括 `key` 本身，写 hash 前要剔除。

**`@Transactional` 加在扣库存方法上，锁却没拦住并发。** 事务的提交发生在方法返回之后，锁在方法返回时就释放了，两者之间有一段窗口。Spring Data Redis 4 提供了 `initializeCacheLocking` 与 `CacheLockingConfigurer` 等机制来处理这类问题，本节场景在锁内直接写库，不受影响。

---

## 8. 练习与参考答案

练习代码都在 `06-redis/src/test/java/top/mqxu/redis/` 下，运行方式见 8.3。

### 8.1 课堂随堂练习

**练习 1：用 Hash 只更新对象的某一个字段。**

题意：商品有三个可改属性 —— 名称、分类、价格。用 Hash 把商品存进 Redis，要求只更新价格时不重写整个对象。

参考答案：

```java
@Test
@DisplayName("练习 1：用 Hash 只改一个字段，其余字段保持不变")
void practice1UpdateSingleHashField() {
    String key = "practice:goods:hash:1";
    stringRedisTemplate.opsForHash().putAll(key, Map.of(
            "name", "机械键盘 K87", "category", "数码", "price", "399.00"));

    stringRedisTemplate.opsForHash().put(key, "price", "359.00");

    Map<Object, Object> fields = stringRedisTemplate.opsForHash().entries(key);
    assertThat(fields).containsEntry("price", "359.00")
            .containsEntry("name", "机械键盘 K87")
            .containsEntry("category", "数码");
    stringRedisTemplate.delete(key);
}
```

要点：`put` 只覆盖指定字段，`putAll` 会覆盖同名字段、保留其余字段；Hash 读写的单位是字段，不是整个值，这也是它相比 String 存 JSON 的优势。

**练习 2：用 Set 求两个用户共同关注的商品。**

参考答案：

```java
@Test
@DisplayName("练习 2：Set 求交集，得到共同关注的商品")
void practice2IntersectLikes() {
    stringRedisTemplate.opsForSet().add("practice:like:u1", "1", "2", "3");
    stringRedisTemplate.opsForSet().add("practice:like:u2", "2", "3", "4");

    Set<String> common = stringRedisTemplate.opsForSet()
            .intersect("practice:like:u1", "practice:like:u2");

    assertThat(common).containsExactlyInAnyOrder("2", "3");
    stringRedisTemplate.delete(List.of("practice:like:u1", "practice:like:u2"));
}
```

进一步可以试 `union`（并集）和 `difference`（差集），对应共同关注、推荐、排除已关注三种业务。

**练习 3：为一段热点数据加上带抖动的过期时间。**

题意：要求过期时间在基础 TTL 之上随机浮动，避免同一批 key 同时失效。

参考答案：

```java
@Test
@DisplayName("练习 3：TTL 抖动后，多个 key 的剩余时间不会完全一致")
void practice3TtlJitter() {
    List<String> keys = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
        String key = "practice:jitter:" + i;
        redisTemplate.opsForValue().set(key, "v", Duration.ofMinutes(10).plusSeconds(ThreadLocalRandom.current().nextInt(31)));
        keys.add(key);
    }

    Set<Long> ttls = keys.stream()
            .map(redisTemplate::getExpire)
            .collect(Collectors.toSet());

    // 10 个 key 的 TTL 至少出现两种取值
    assertThat(ttls).hasSizeGreaterThan(1);
    redisTemplate.delete(keys);
}
```

要点：抖动幅度要远小于基础 TTL，否则等于没设过期；`ThreadLocalRandom` 比 `Random` 在并发下开销更小。

**练习 4：用 `SETNX` 实现一个“同一用户 5 秒内只能提交一次”的接口。**

参考思路：key 用 `practice:submit:<userId>`，`setIfAbsent` 成功则放行并设 5 秒过期，失败则返回“操作过于频繁”。这与 `/limit/access` 的固定窗口不同，属于最简单的去重式限流。

```java
@Test
@DisplayName("练习 4：SETNX 做提交去重，第二次被挡住")
void practice4SubmitOnce() {
    String key = "practice:submit:1001";

    Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(5));
    Boolean second = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(5));

    assertThat(first).isTrue();
    assertThat(second).isFalse();
    assertThat(stringRedisTemplate.getExpire(key)).isPositive();
    stringRedisTemplate.delete(key);
}
```

**练习 5：给排行榜加上“取某人前后各一名”的能力。**

提示：先用 `reverseRank` 拿到名次（从 0 开始），再用 `reverseRangeWithScores(key, rank - 1, rank + 1)` 取区间，注意边界要夹到 `0`。

### 8.2 课后练习

**练习 6：用 Hash 实现购物车。**

要求：一个用户一个 key，商品 id 做字段，数量做值；支持加购、改数量、删除单项、查看全部、清空。用 `HINCRBY` 对应加购，用 `HINCRBY` 传负数对应减购，减到 0 时删字段。

```java
@Test
@DisplayName("练习 6：购物车用 Hash 存，HINCRBY 让加购与减购都是原子操作")
void homework6ShoppingCart() {
    String key = "homework:cart:1001";
    stringRedisTemplate.delete(key);

    stringRedisTemplate.opsForHash().increment(key, "1", 2);    // 商品 1 加 2 件
    stringRedisTemplate.opsForHash().increment(key, "1", 1);    // 再加 1 件
    stringRedisTemplate.opsForHash().increment(key, "2", 1);    // 商品 2 加 1 件
    stringRedisTemplate.opsForHash().increment(key, "1", -3);   // 商品 1 减 3 件

    Map<Object, Object> cart = stringRedisTemplate.opsForHash().entries(key);
    assertThat(cart).containsEntry("1", "0").containsEntry("2", "1");

    // 数量为 0 的条目由业务代码删掉
    stringRedisTemplate.opsForHash().delete(key, "1");
    assertThat(stringRedisTemplate.opsForHash().entries(key)).hasSize(1);
    stringRedisTemplate.delete(key);
}
```

思考题：为什么用 `HINCRBY` 而不是先 `HGET` 再 `HSET`？因为后者在并发加购时会丢失更新，与 6.4 节里不加锁扣库存是同一类问题。

**练习 7：给商品详情加一层“逻辑过期”防击穿。**

要求：缓存里存的值外面包一层，带上逻辑过期时间；读到已逻辑过期的数据时，仍然立刻返回旧值，同时只放一个线程去异步重建缓存。

参考思路：

```java
@Test
@DisplayName("练习 7：逻辑过期——过期后先返回旧值，只放一个线程去重建")
void homework7LogicalExpire() throws InterruptedException {
    String key = "homework:logical:1";
    Map<String, Object> wrapper = new LinkedHashMap<>();
    wrapper.put("data", "旧值");
    wrapper.put("expireAt", System.currentTimeMillis() - 1000);   // 已逻辑过期
    redisTemplate.opsForValue().set(key, wrapper, Duration.ofMinutes(30));  // 物理上不过期

    @SuppressWarnings("unchecked")
    Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(key);
    long expireAt = ((Number) cached.get("expireAt")).longValue();

    String returned = (String) cached.get("data");
    AtomicInteger rebuildCount = new AtomicInteger();

    if (expireAt < System.currentTimeMillis()) {
        // 抢到锁的线程才重建，其余线程直接拿到旧值
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(key + ":rebuild", "1", Duration.ofSeconds(5));
        if (Boolean.TRUE.equals(locked)) {
            rebuildCount.incrementAndGet();
            // 真实场景这里开一个异步线程去查库并回写缓存
        }
    }

    assertThat(returned).isEqualTo("旧值");
    assertThat(rebuildCount.get()).isEqualTo(1);
}
```

读取 `expireAt` 不能直接强转成 `Long`。往 `Map<String, Object>` 里存的时间戳，反序列化后是 `Number` 的具体子类，用 `((Number) x).longValue()` 最稳。

与 8.1 的互斥锁方案对比：互斥锁会让等待的请求阻塞一小会儿，逻辑过期让所有请求都立刻返回，代价是短时间里读到旧数据。两者取舍取决于业务能否容忍短暂旧值。

**练习 8：把限流改成按接口维度统计，并输出剩余可用次数。**

要求：key 里带上接口名，返回值多一个 `remaining` 字段。

参考实现：

```java
@Test
@DisplayName("练习 8：按接口维度限流，并返回剩余可用次数")
void homework8ApiLimitRemaining() {
    String api = "/orders";
    String user = "u1001";
    int windowSeconds = 60;
    int maxRequests = 3;
    long windowIndex = System.currentTimeMillis() / 1000 / windowSeconds;
    String key = "homework:limit:" + api + ":" + user + ":" + windowIndex;

    List<Map<String, Object>> results = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("count", count);
        item.put("remaining", Math.max(0L, maxRequests - count));
        item.put("allowed", count != null && count <= maxRequests);
        results.add(item);
    }

    assertThat(results.get(2)).containsEntry("remaining", 0L).containsEntry("allowed", true);
    assertThat(results.get(3)).containsEntry("remaining", 0L).containsEntry("allowed", false);
}
```

`Math.max(0L, ...)` 里的 `0L` 不能写成 `0`。`maxRequests - count` 是 `long`，传 `int` 进去会被提升成 `long`，结果是 `Long`；断言时写 `0` 是 `Integer`，两者不相等。这类装箱类型的比较在测试里很容易出错。

**练习 9：让“先删缓存再更新库”的不一致稳定复现。**

用两个终端并发跑“读”和“改”很难每次都撞上那个窗口，所以把顺序手工摆出来：先往缓存放旧值，删掉缓存，模拟另一个请求在这个窗口里读到库里的旧值并写回缓存，最后更新数据库但不再删缓存。此时缓存里是旧值，库里是新值。

```java
@Test
@DisplayName("练习 9：先删缓存再更新库，中间进来的一次读会把旧值写回缓存")
void homework9DeleteThenUpdateLeavesStaleCache() {
    String key = "homework:stale:1";
    Goods before = goodsService.getFromDb(1L);
    BigDecimal oldPrice = before.getPrice();

    redisTemplate.opsForValue().set(key, before, Duration.ofMinutes(10));   // 缓存里先放好旧值
    redisTemplate.delete(key);                                             // 先删缓存

    // 另一个请求在删缓存之后、更新库之前读到旧值并写回缓存
    Goods readInWindow = goodsService.getFromDb(1L);
    redisTemplate.opsForValue().set(key, readInWindow, Duration.ofMinutes(10));

    // 再更新库，此时缓存里留下的是旧值
    Goods update = new Goods();
    update.setId(1L);
    update.setPrice(oldPrice.add(new BigDecimal("10.00")));
    goodsMapper.updateById(update);

    Object cached = redisTemplate.opsForValue().get(key);
    assertThat(((Goods) cached).getPrice()).isEqualByComparingTo(oldPrice);
    assertThat(goodsService.getFromDb(1L).getPrice()).isEqualByComparingTo(oldPrice.add(new BigDecimal("10.00")));
}
```

结论：先删缓存会留下一个空窗期，期间任何一次读都可能把旧值重新填回缓存，而且没有后续动作再去纠正它。换成先更新库再删缓存，同样的读请求最多读到旧值但不写回，或者写回的是新值，出现长期不一致的概率小得多。这就是 `GoodsCacheService.updatePrice` 选择后者的原因。

### 8.3 测试过程

五个测试类共 41 个用例，连本机真实的 MySQL 与 Redis。

```bash
mvn -pl 06-redis test
```

预期输出：

```
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in top.mqxu.redis.GoodsCacheTest
Tests run:  9, Failures: 0, Errors: 0, Skipped: 0 -- in top.mqxu.redis.RedisTypeOpsTest
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in top.mqxu.redis.LockAndLimitTest
Tests run:  5, Failures: 0, Errors: 0, Skipped: 0 -- in top.mqxu.redis.PracticeTest
Tests run:  4, Failures: 0, Errors: 0, Skipped: 0 -- in top.mqxu.redis.HomeworkTest
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
```

各测试类的分工：

| 测试类 | 用例数 | 覆盖内容 | 关键断言 |
| --- | --- | --- | --- |
| `RedisTypeOpsTest` | 9 | 五种数据类型接口 | Hash 里不能出现 `key` 字段；Set 自动去重；ZSet 按分数倒序 |
| `GoodsCacheTest` | 13 | 三种缓存做法 | 第二次请求 `source` 变 `cache`；空值有更短的 TTL；缓存里的值能还原成 `Goods` |
| `LockAndLimitTest` | 10 | 并发与限流 | 20 线程不加锁会丢更新；加锁后恰好卖出 10 件；第 6 次限流被拒 |
| `PracticeTest` | 5 | 8.1 节的五道随堂练习 | Hash 单字段更新；Set 交集；TTL 抖动；SETNX 去重；排行榜取邻位 |
| `HomeworkTest` | 4 | 8.2 节的课后练习 | 购物车加减购；逻辑过期只放一个线程重建；限流剩余次数；先删缓存导致脏数据 |

值得单独说两条用例。

**并发用例怎么保证能复现问题。** 单线程顺序执行，不加锁也不会出错，所以必须真的并发。用 `CountDownLatch` 让 20 个线程在同一时刻进入方法：

```java
private int runConcurrently(Supplier<Boolean> action) throws InterruptedException {
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> futures = new ArrayList<>();
    try {
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return action.get();
            }));
        }
        start.countDown();
        // 收集结果
    } finally {
        pool.shutdownNow();
    }
}
```

断言写成“20 次都返回成功，但库存减少量小于 20”而不是写死库存的最终值。因为最终值取决于线程调度，写死会偶发失败，而“卖出 20 件、库存只减了不到 20”在任何调度下都成立，正是要验证的那件事。

**用例之间怎么隔离。** 缓存测试会改动数据库和 Redis，所以每条用例开头都重跑一次建表与初始化脚本，再清掉本模块用到的 key：

```java
@BeforeEach
void reset() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
    }
    deleteKeys("goods:detail:*");
    deleteKeys("cache:goods*");
    deleteKeys("lock:goods:rebuild:*");
}
```

只跑某一个用例：

```bash
mvn -pl 06-redis test -Dtest=LockAndLimitTest#deductWithRedissonLockShouldNotOversell
```

跑测试会重置 `t_goods`，跑完库里是初始化数据，与启动后 `data.sql` 的结果一致，不需要额外恢复。

---

## 9. 小结与知识地图

本节从一次数据库查询出发，依次解决了四个问题：

| 问题 | 手段 | 落点 |
| --- | --- | --- |
| 热点数据反复查库慢 | Redis 缓存 + JSON 序列化 | `RedisConfig`、`GoodsCacheService` |
| 缓存与数据库不一致 | 更新库后删缓存，TTL 兜底 | `GoodsCacheService.updatePrice` |
| 缓存失效时的三类异常 | 空值标记、互斥锁、TTL 抖动 | `cacheIt`、`getByIdWithMutex`、`ttlWithJitter` |
| 多机并发的数据竞争 | Redisson 分布式锁 | `StockService.deductWithLock` |

沿着这条线，本节覆盖的知识点：

- Redis 五种数据结构的适用场景与常用命令
- `RedisTemplate` 的序列化配置，以及类型信息与反序列化安全的取舍
- Cache-Aside 的读写流程，三种更新策略的差异
- 穿透、击穿、雪崩的成因与各自的代码实现
- Spring 缓存抽象 `@Cacheable` / `@CachePut` / `@CacheEvict` / `@Caching`
- 自定义 `RedisCacheConfiguration` 后配置不生效的原因
- Boot 4 缓存自动配置被拆分到 `spring-boot-cache` 这一变化
- Spring Data Redis 4 默认异步写缓存及其关闭方式
- 分布式锁的三个必要条件，`SETNX` + Lua 与 Redisson 的差距
- ZSet 排行榜与计数器限流

下一节 `07-mq` 处理的是另一类问题：不要求立刻拿到结果的耗时操作，通过消息队列异步化。
