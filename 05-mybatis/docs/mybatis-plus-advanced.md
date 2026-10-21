# MyBatis-Plus 进阶：查询、多表与结果映射

> 05-mybatis 补充材料 · MyBatis-Plus 3.5.17 · Spring Boot 4 · MySQL 8

模块现有代码把单表增删改查、条件构造器、分页、自动填充、逻辑删除、乐观锁都跑通了。这份材料补它没覆盖的部分：条件构造器里更复杂的表达、不落实体的查询、多表关联的五种落地方式，以及多表场景下会踩到的问题。代码片段按项目现有的实体和表写，可以直接往上加。

## 现有代码覆盖到哪里

先把边界划清，后面只写没写过的。

| 能力 | 现状 | 位置 |
| --- | --- | --- |
| 通用 CRUD、批量写、批量删 | 已覆盖 | `UserServiceImpl` |
| 条件构造器：`eq / ge / le / like / or / and(嵌套)` | 已覆盖 | `buildCondition` |
| 分页、排序、`last("LIMIT n")` | 已覆盖 | `pageByCondition` |
| 单列查询 `select + listObjs` | 已覆盖 | `listUsernames` |
| 聚合 `getMap / listMaps + groupBy` | 已覆盖 | `statistics` |
| `apply` 拼 JSON 函数、`exists` 判断、`count` | 已覆盖 | `listByTag` |
| 子查询（`inSql / exists / notExists`）、`having` | 未覆盖 | — |
| 投影到 VO、Optional 风格查询、`pageMaps` | 未覆盖 | — |
| 原子加减 `setIncrBy / setSql` | 未覆盖 | — |
| 多表关联、多表分页 | 未覆盖 | — |
| 游标流式查询、防全表更新、多租户、数据权限 | 未覆盖 | — |

## 条件构造器的进阶用法

### 条件开关与动态排序

每个条件方法都有带 `boolean condition` 的重载，条件为 `false` 时整段不拼进 SQL，这是写动态查询最省事的地方。排序字段不要直接把前端传的列名拼进去，用白名单映射成 `SFunction`，拼错就是 SQL 注入。

```java
private static final Map<String, SFunction<User, ?>> SORT_FIELDS = Map.of(
        "points", User::getPoints,
        "age", User::getAge,
        "createTime", User::getCreateTime);

public List<User> listSorted(String sortBy, boolean asc, Integer minAge) {
    SFunction<User, ?> column = SORT_FIELDS.getOrDefault(sortBy, User::getId);
    return list(Wrappers.<User>lambdaQuery()
            .ge(minAge != null, User::getAge, minAge)
            .orderBy(true, asc, column)
            .orderByAsc(User::getId));   // 补一列唯一值，分页才不会漏行或重行
}
```

### and / or 的括号

`or()` 是平铺的，不加括号会把前面的条件一起放开。项目里的 `searchByKeyword` 已经用过这个写法，这里把反例也写出来对照。

```java
// 正确：状态正常，并且（金牌 或 积分大于 5000）
list(Wrappers.<User>lambdaQuery()
        .eq(User::getStatus, 1)
        .and(w -> w.eq(User::getMemberLevel, "GOLD").or().gt(User::getPoints, 5000)));
// WHERE status = 1 AND (member_level = ? OR points > ?)

// 错误：没有括号，or 之后的条件自成一支，前面的 status 条件失效
list(Wrappers.<User>lambdaQuery()
        .eq(User::getStatus, 1)
        .eq(User::getMemberLevel, "GOLD")
        .or()
        .gt(User::getPoints, 5000));
// WHERE status = 1 AND member_level = ? OR points > ?
```

括号层数多了，`and(consumer)` 嵌套会很难读，可以用 `nested(consumer)` 显式开一个括号，语义一样，但意图更清楚。

### 子查询：inSql / exists / notExists

`inSql` 和 `exists` 收到的是一段原始 SQL，原样拼进最终语句，不参与逻辑删除、租户、自动填充这些注入，也不会自动参数化。里面的表名、`deleted = 0` 都得自己写。

```java
// 下过单的用户：IN 子查询
list(Wrappers.<User>lambdaQuery()
        .inSql(User::getId, "SELECT user_id FROM t_order WHERE status = 1 AND deleted = 0"));

// 有未取消订单的用户：EXISTS 相关子查询，注意关联的是 t_user.id
list(Wrappers.<User>lambdaQuery()
        .exists("SELECT 1 FROM t_order o WHERE o.user_id = t_user.id AND o.status = 1 AND o.deleted = 0"));

// 一单没下过的用户
list(Wrappers.<User>lambdaQuery()
        .notExists("SELECT 1 FROM t_order o WHERE o.user_id = t_user.id AND o.deleted = 0"));
```

> 别把参数交给 inSql
>
> `inSql(column, sql)` 没有可变参数，拼进去就是字符串。需要传值时改用 `apply` 的占位符形式：`.apply("id IN (SELECT user_id FROM t_order WHERE amount > {0})", 1000)`，`{0}` 会被替换成 `#{...}` 参数。

### 分组与 having

项目的 `statistics` 只做到 `groupBy`，没用到 `having`。`having` 收的也是原始 SQL 片段，MyBatis-Plus 不校验内容。聚合条件直接写 `COUNT(*)` 最稳，虽然 MySQL 允许在 `HAVING` 里引用 `select` 的别名，换个库就不一定行。

```java
List<Map<String, Object>> rows = listMaps(new QueryWrapper<User>()
        .select("member_level AS level", "COUNT(*) AS cnt", "ROUND(AVG(age), 1) AS avgAge")
        .groupBy("member_level")
        .having("COUNT(*) > 1")
        .orderByDesc("cnt"));
```

```sql
SELECT member_level AS level, COUNT(*) AS cnt, ROUND(AVG(age), 1) AS avgAge
FROM t_user
WHERE deleted = 0
GROUP BY member_level
HAVING COUNT(*) > 1
ORDER BY cnt DESC
```

### 只取需要的列

用户表有 `intro`（TEXT）、`tags`（JSON）、`password`，列表接口全查出来既费带宽又危险。两种写法：

```java
// Lambda：列出要的列
LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery()
        .select(User::getId, User::getUsername, User::getNickname, User::getPoints);

// 表驱动：按实体的字段信息排除，适合“除了几个大字段，其余都要”
new QueryWrapper<User>().select(User.class,
        info -> !"intro".equals(info.getColumn())
                && !"password".equals(info.getColumn())
                && !"tags".equals(info.getColumn()));
```

> 注意
>
> 用 `select` 只取部分列时，没查的字段在实体里是 `null`。如果后面接着 `updateById`，这些 `null` 不会覆盖库里的值，看着没事；但只要某个字段配了 `updateStrategy = FieldStrategy.ALWAYS`，就会被写成 `NULL`。只查部分列的实体，别直接拿去更新。

### wrapper 复用

同一个 `wrapper` 可以连查多次（`count` 一次、`list` 一次），但要改条件时用 `clone()`，否则改动会留在原对象上，下一次查询悄悄变了条件。

```java
LambdaQueryWrapper<User> base = Wrappers.<User>lambdaQuery().eq(User::getStatus, 1);

long total = count(base);          // SELECT COUNT(*) FROM t_user WHERE deleted = 0 AND status = 1
List<User> top = list(base.clone() // 换个排序再查一次，base 本身不受影响
        .orderByDesc(User::getPoints)
        .last("LIMIT 10"));
```

## 查询结果不落实体

### Optional 风格

3.5.x 补了一批 `Optional` 和 `*Opt` 结尾的方法，把“查不到返回 null”改成显式返回。`getOne` 默认在结果多于一条时抛 `TooManyResultsException`，不想抛就传 `false`。

```java
Optional<User> admin = getOneOpt(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
Optional<User> user = getOptById(1L);

String nickname = getObj(Wrappers.<User>lambdaQuery()
        .select(User::getNickname)
        .eq(User::getId, 2L), Object::toString);
```

### listMaps / pageMaps / listObjs

不需要实体、只要几列的时候，`Map` 直接拿走，省掉一次对象映射。`pageMaps` 是分页版，返回 `Page<Map<String, Object>>`，做报表接口很方便。

```java
List<Map<String, Object>> rows = listMaps(Wrappers.<User>lambdaQuery()
        .select(User::getMemberLevel, User::getPoints)
        .orderByDesc(User::getPoints));

Page<Map<String, Object>> page = pageMaps(new Page<>(1, 5), Wrappers.<User>lambdaQuery()
        .select(User::getId, User::getUsername));
```

Map 有两个坑。一是键名跟 SQL 里的列名或别名一致，不会自动转驼峰，`member_level` 取出来就是 `member_level`。二是值的类型由 JDBC 决定：`COUNT(*)` 是 `Long`，`SUM(balance)` 是 `BigDecimal`，`SUM` 在全 `NULL` 时返回 `null`。统一用 `((Number) v).longValue()` 取值，别直接强转。

### 投影到 VO

字段对不上实体、又不值得为它建表映射时，用别名加 VO 接。

```java
public record UserCard(Long id, String username, String nickname, int points) {}

List<UserCard> cards = listMaps(new QueryWrapper<User>()
                .select("id", "username", "nickname", "points")
                .eq("status", 1))
        .stream()
        .map(m -> new UserCard(
                ((Number) m.get("id")).longValue(),
                (String) m.get("username"),
                (String) m.get("nickname"),
                ((Number) m.get("points")).intValue()))
        .toList();
```

列多、或者要复用到多个接口时，别在 Service 里手写 `Map` 转换，直接把 SQL 交给 Mapper，让 MyBatis 按属性名映射到 VO —— 见下一节的注解方式。

## 与查询配套的更新写法

这三个和查询常一起出现，现有代码里没有，补一下。

### 原子加减 setIncrBy / setDecrBy

项目里的充值走的是“先查再改再 `updateById`”，靠 `@Version` 兜住并发。单纯加一个数的场景，一条 SQL 就够，不用乐观锁。

```java
update(Wrappers.<User>lambdaUpdate()
        .setIncrBy(User::getPoints, 100)
        .setDecrBy(User::getBalance, new BigDecimal("9.90"))
        .eq(User::getId, id));
```

```sql
UPDATE t_user SET points = points + 100, balance = balance - 9.90 WHERE deleted = 0 AND id = ?
```

### setSql 做条件扣减

`OrderServiceImpl.createOrder` 是先读库存、减完再 `updateById`，库存不足靠抛异常回滚。换成一条带条件的更新，数据库层面就把“够才扣”判掉，不需要先查一次。

```java
boolean ok = update(Wrappers.<Product>lambdaUpdate()
        .setSql("stock = stock - 1, update_time = NOW()")
        .eq(Product::getId, productId)
        .ge(Product::getStock, 1));
if (!ok) {
    throw new IllegalStateException("库存不足：" + productId);
}
```

> Wrapper 更新会绕开两个机制
>
> 一是自动填充：走 `update(wrapper)` 不经过实体，`updateTime` 的 `INSERT_UPDATE` 填充不触发，要像上面那样手写进 `setSql`。二是乐观锁：`@Version` 只对 `updateById` 生效，`setIncrBy` 这类写法本身不带版本条件。选了原子加减，就别再指望版本号兜底。

### 把字段更新成 null

`updateById` 默认跳过 `null` 字段，所以“清空头像”这种需求会失效。两个办法：字段上放宽策略，或者显式 `set`。

```java
// 办法一：只对这个字段放宽
@TableField(updateStrategy = FieldStrategy.ALWAYS)
private String avatar;

// 办法二：不改实体，更新时显式指定
update(Wrappers.<User>lambdaUpdate()
        .set(User::getAvatar, null)
        .eq(User::getId, id));
```

## 多表查询的五种方式

MyBatis-Plus 的 `BaseMapper` 是按 `@TableName` 绑到一张表的，`selectList`、`updateById` 这些通用方法生成的 SQL 只认一个表。它没打算做 ORM 的关联映射，所以多表要么手写 SQL，要么分两次单表查再拼。

关系大致是这样：一个用户多条订单，一条订单对一个商品。

```text
t_user 1 ──── n t_order n ──── 1 t_product
        user_id        product_id
```

### 方式一：分步查单表，内存里拼

最稳的做法，也是实际项目里用得最多的。两次单表查询都走 MyBatis-Plus 生成的方法，逻辑删除、租户、自动填充全部生效，没有 join，也就没有“join 之后逻辑删除还管不管用”的问题。

```java
public List<OrderVO> listOrderVO(Long userId) {
    List<Order> orders = orderMapper.selectList(
            Wrappers.<Order>lambdaQuery()
                    .eq(Order::getUserId, userId)
                    .orderByDesc(Order::getId));
    if (orders.isEmpty()) {
        return List.of();
    }

    // 一次把用到的商品全查回来，避免在循环里查（那就是 N+1）
    Set<Long> productIds = orders.stream()
            .map(Order::getProductId)
            .collect(Collectors.toSet());
    Map<Long, Product> productMap = productMapper.selectByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

    return orders.stream().map(o -> {
        Product p = productMap.get(o.getProductId());
        return new OrderVO(o.getId(), o.getOrderNo(),
                p == null ? null : p.getName(),
                o.getQuantity(), o.getAmount());
    }).toList();
}
```

| 优点 | 代价 |
| --- | --- |
| 不写 SQL，单表方法全部复用，逻辑删除、多租户、字段自动填充照常生效 | SQL 次数固定为 2 次，但网络往返比一条 join 多 |
| 分页只需对主表分页，总数天然正确 | 关联表数据量大时，第二次查询要用 `IN` 控制集合大小 |
| 拼接逻辑在 Java 里，好调试 | 排序字段在关联表上时做不了（要先查关联表再在内存排） |

`selectByIds` 是 3.5.17 的方法名，`selectBatchIds` 还留着但已标记废弃，两者行为一样。

### 方式二：注解 SQL 直接映射到 VO

只要几列、字段名还能对上 VO 的属性，注解写一条 join 最省事。MyBatis 会按属性名填值，开启驼峰映射后列名自动转驼峰。

```java
public interface OrderMapper extends BaseMapper<Order> {

    @Select("""
            SELECT o.id, o.order_no, o.quantity, o.amount, o.status,
                   p.name AS productName, p.price AS productPrice
            FROM t_order o
            JOIN t_product p ON p.id = o.product_id AND p.deleted = 0
            WHERE o.deleted = 0 AND o.user_id = #{userId}
            ORDER BY o.id DESC
            """)
    List<OrderVO> selectOrderList(@Param("userId") Long userId);
}
```

> 手写 SQL 之后，逻辑删除不再自动加
>
> `@TableLogic` 是在 MyBatis-Plus 生成 SQL 时注入 `deleted = 0` 的，手写的语句它管不到。上面 `o.deleted = 0` 和 `p.deleted = 0` 必须自己写，漏一个就会把已删数据带出来。同理，多租户、自动填充也不会作用到这段 SQL。

需要“一条订单带一个商品对象”这种嵌套结构，用 `@Results` 加 `@One`，子查询直接引 `BaseMapper` 的方法：

```java
@Select("SELECT * FROM t_order WHERE id = #{id} AND deleted = 0")
@Results(id = "orderDetail", value = {
        @Result(column = "id", property = "id", id = true),
        @Result(column = "order_no", property = "orderNo"),
        @Result(column = "product_id", property = "product",
                one = @One(select = "top.mqxu.mybatis.mapper.ProductMapper.selectById"))
})
OrderDetailVO selectOrderDetail(@Param("id") Long id);
```

`@One` 的 `select` 指向 `ProductMapper.selectById`，传进去的是 `product_id` 这一列的值。这个子查询是 MyBatis-Plus 生成的，逻辑删除照常生效，比分步手写省事的地方就在这里。代价是每条主记录额外一次查询，列表查询就是典型的 N+1，只适合详情页。

一对多用 `@Many`，需要多个参数时用 `column = "{orderId=id}"` 的写法，子查询方法上要加 `@Param`。

### 方式三：XML 的 resultMap

结构复杂、SQL 要拼接条件、还要复用片段时，XML 比注解好维护。项目里没有 `mapper` 目录，新建后在默认扫描路径下即可生效。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="top.mqxu.mybatis.mapper.OrderMapper">

    <resultMap id="orderDetailMap" type="top.mqxu.mybatis.vo.OrderDetailVO">
        <id column="id" property="id"/>
        <result column="order_no" property="orderNo"/>
        <result column="quantity" property="quantity"/>
        <result column="amount" property="amount"/>
        <association property="product"
                     javaType="top.mqxu.mybatis.entity.Product"
                     column="product_id"
                     select="top.mqxu.mybatis.mapper.ProductMapper.selectById"/>
    </resultMap>

    <select id="selectDetail" resultMap="orderDetailMap">
        SELECT id, order_no, product_id, quantity, amount
        FROM t_order
        WHERE id = #{id} AND deleted = 0
    </select>
</mapper>
```

```yaml
mybatis-plus:
  # 默认值就是它，写出来是为了让约定可见
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    lazy-loading-enabled: true
    aggressive-lazy-loading: false
```

`association` 用 `select` 属性属于嵌套查询，默认懒加载，配合上面两项配置，只在真正访问 `getProduct()` 时才发第二条 SQL。另一种写法是把两表字段写进同一条 join，在 `association` 里用 `result` 直接填，一次查询出结果，代价是 SQL 里要手写列别名防重名。

### 方式四：一条 join 直接分页

分页插件对自定义 SQL 同样生效，约定是把 `IPage` 放在方法第一个参数，返回 `IPage`。

```java
IPage<OrderVO> selectOrderPage(IPage<OrderVO> page, @Param("userId") Long userId);
```

```java
IPage<OrderVO> page = orderMapper.selectOrderPage(new Page<>(current, size), userId);
return page.getRecords();
```

插件会自动再发一条 count 语句。SQL 里带 `GROUP BY`、`DISTINCT`、或者一对多 join 导致行数放大时，自动 count 容易和真实业务口径对不上。这时把自动 count 关掉，自己算：

```java
Page<OrderVO> page = new Page<>(current, size, false);   // 第三个参数 searchCount = false，不查总数
List<OrderVO> rows = orderMapper.selectOrderPage(page, userId).getRecords();
page.setTotal(orderMapper.countOrder(userId));           // 自己写一条口径明确的 count
page.setRecords(rows);
```

### 方式五：mybatis-plus-join

社区扩展，给 Mapper 加一个自定义父接口，就能用 Lambda 写 join，写法接近单表。它不在 MyBatis-Plus 主仓，版本和 MP 本体是耦合的：`mybatis-plus-join` 目前最新 1.5.9，配套的是 `mybatis-plus-boot-starter`，没有针对 Spring Boot 4 的构件。本项目用的是 `mybatis-plus-spring-boot4-starter` 3.5.17，两者能不能搭起来需要自己验证，别默认能用。

### 方式对比

| 方式 | 写 SQL | 逻辑删除 / 租户 | 分页总数 | 适合场景 |
| --- | --- | --- | --- | --- |
| 分步查单表 | 不用 | 自动生效 | 天然正确 | 列表页、需要复用的常规业务 |
| 注解 SQL → VO | 一条 SQL | 要手写 | 可用自动 count | 字段少、结构平的列表 |
| @One / @Many | 主查询要 | 子查询生效 | 不适用 | 单条详情、嵌套结构 |
| XML resultMap | 要 | 要手写 | 可用自动 count | SQL 复杂、要复用片段 |
| join 直接分页 | 要 | 要手写 | 需注意口径 | 关联表参与筛选和排序 |

## 多表场景的问题

### 逻辑删除只对单表方法生效

这点值得单独说：`@TableLogic` 不是拦截器，是 MyBatis-Plus 在建 SQL 时往语句里拼条件，`@InterceptorIgnore` 也绕不过它。所以两个方向都会踩：

- 手写 join 忘了写 `deleted = 0`，把已删订单、已删商品查了出来；
- 想查已删数据做数据核对时，`getById`、`selectList` 永远查不到，只能自己写 SQL。

### join 之后分页的粒度和你想的不一样

一单只对一个商品时，join 行数等于订单数，没问题；一旦一对多（一单多个明细），分页插件按结果行切，第 1 页 10 条可能只包含 4 单。这种场景的主表分页要走方式一：先对主表分页，再用 `IN` 补关联数据。

### 深分页

`LIMIT 100000, 10` 这种写法会把前面 10 万行扫一遍再丢掉。数据量大时两种处理：

```java
// 键集分页：用上一页最后一条的 id 定位，不依赖 offset
long lastId = 0;
while (true) {
    List<User> batch = list(Wrappers.<User>lambdaQuery()
            .gt(User::getId, lastId)
            .orderByAsc(User::getId)
            .last("LIMIT 500"));
    if (batch.isEmpty()) {
        break;
    }
    batch.forEach(this::handle);
    lastId = batch.get(batch.size() - 1).getId();
}
```

```java
// 游标：一次只把一行放在内存里，适合导出
@Transactional(readOnly = true)
public void exportAll(Consumer<User> sink) {
    try (Cursor<User> cursor = baseMapper.selectWithCursor(
            Wrappers.<User>lambdaQuery().orderByAsc(User::getId))) {
        cursor.forEach(sink);
    }
}
```

> 方法名和事务
>
> 3.5.17 的游标方法是 `selectWithCursor`，老资料里的 `selectCursor` 在这个版本已经没有了。加了 `@Transactional` 才能用，否则 `SqlSession` 提前关闭，遍历到一半报错。

### wrapper 里混用表别名

`apply`、`exists`、`last` 里可以写别的表，但这只是“允许你塞一段 SQL 进去”，并不代表 wrapper 变成了 join 构造器。一旦这么写，主表列名要靠 Lambda 生成、副表列名靠手写字符串，两种风格混在一处容易出错，条件多了建议直接写完整 SQL。

## 顺手能用的插件

项目 pom 已经引了 `mybatis-plus-jsqlparser`，下面这些不用再加依赖，注册到已有的 `MybatisPlusInterceptor` 里即可。

| 插件 | 作用 | 典型用途 |
| --- | --- | --- |
| `BlockAttackInnerInterceptor` | 拦截没有 where 条件的 update / delete | 防止误写全表更新 |
| `TenantLineInnerInterceptor` | 自动给 SQL 追加租户条件 | 多租户 SaaS |
| `DataPermissionInterceptor` | 按规则拼接数据范围条件 | 只能看本部门数据的后台 |
| `DynamicTableNameInnerInterceptor` | 按规则替换表名 | 按月份分表 |
| `PaginationInnerInterceptor` / `OptimisticLockerInnerInterceptor` | 分页、乐观锁 | 项目已用 |

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    // 顺序：多租户、动态表名这类改 SQL 结构的放前面，分页在中间，防全表放最后
    interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    return interceptor;
}
```

分页插件的两个参数值得配：`setMaxLimit(500L)` 限制单页上限，`setOverflow(false)` 让页码越界时返回空列表而不是回到第一页。单个方法需要跳过插件时，在 Mapper 方法上标 `@InterceptorIgnore`：

```java
@InterceptorIgnore(tenantLine = "1", blockAttack = "1")
@Select("DELETE FROM t_order WHERE create_time < #{deadline}")
int purgeHistory(@Param("deadline") LocalDateTime deadline);
```

## 落到本项目上的改动清单

把上面的内容变成可运行的代码，按这个顺序加，每一步都能单独跑测试验证。

| 步骤 | 文件 | 内容 |
| --- | --- | --- |
| 1 | `vo/OrderVO.java`、`vo/OrderDetailVO.java` | 订单列表与详情的返回结构，`OrderDetailVO` 里带一个 `Product` 属性 |
| 2 | `mapper/OrderMapper.java` | 加 `selectOrderList`（注解 join → VO）、`selectOrderPage`（IPage 参数） |
| 3 | `service/OrderService` 及实现类 | 加 `listOrderVO`（分步查询）、`pageOrderVO`（join 分页）两个方法，两种方式并存好对比 |
| 4 | `resources/mapper/OrderMapper.xml` | 把 `association` 的版本单独做一份，验证懒加载 |
| 5 | `service/impl/UserServiceImpl` | 补 `setIncrBy` 加积分、`inSql` 查下过单的用户、`having` 分组 |
| 6 | `config/MybatisPlusConfig` | 加 `BlockAttackInnerInterceptor` 与分页的 `maxLimit` |
| 7 | `test/.../JoinQueryTest.java` | 两种多表方式的返回结果对齐断言，顺带验证 `deleted = 0` 是否漏写 |

---

API 名称按 MyBatis-Plus 3.5.17 的源码逐个核对过：`BaseMapper`、`AbstractWrapper`、`Update`、`IRepository`、`PaginationInnerInterceptor`。`selectWithCursor`、`setIncrBy`、`selectByIds`、`getOptById` 这几个名字与 3.5.7 前后差异较大，照抄旧教程容易报“找不到方法”。多表分页的 count 口径、逻辑删除不生效这两条，是这套写法里最容易出问题的地方，改代码时先看这两处。
