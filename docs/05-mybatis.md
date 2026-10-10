# 05 · 数据访问 —— 用 MyBatis-Plus 持久化数据

> - 难度：进阶
> - 前置模块：04-web
> - 预计时长：约 90 分钟
> - 对应代码：[05-mybatis](https://github.com/mqxu/backend-learning/tree/main/05-mybatis)

## 1. 本节导读

学完本节，你将能：

1. 建一张覆盖 MySQL 常用数据类型的表，并把每种类型正确映射到 Java；
2. 用 `BaseMapper` / `IService` 完成单表增删改查，不写一条 SQL；
3. 用 `MetaObjectHandler` 做字段自动填充，用逻辑删除代替物理删除，用乐观锁处理并发更新；
4. 用 `LambdaQueryWrapper` / `LambdaUpdateWrapper` 写常见的条件查询与更新；
5. 理解并落地 Service 分层与 `@Transactional` 事务；
6. 配好连接池参数，知道池大小、超时和 MySQL 连接上限之间的关系，会用 Druid 监控页看池状态。

**前置知识**：会 `04-web`；懂一点 SQL 与关系型数据库。

---

## 2. 概念铺垫

### 2.1 为什么要 ORM

操作数据库要写 SQL、把结果一行行塞进 Java 对象，非常繁琐。ORM（对象关系映射）帮我们做「对象 ↔ 表」的自动映射：操作对象就像操作数据。

MyBatis-Plus 是在 MyBatis 基础上的增强：提供 `BaseMapper` 通用 CRUD，连 SQL 都能省掉大半。

### 2.2 三层结构：Mapper / Service / Controller

| 层 | 职责 | 对应 |
| --- | --- | --- |
| Controller | 接收参数、调用 Service、返回结果 | `UserController` |
| Service | 业务逻辑、事务 | `UserService` / `UserServiceImpl` |
| Mapper | 数据访问、SQL | `UserMapper` |

规则：上层只能调用紧邻的下层，不能跨层（Controller 不能直接碰 Mapper）。

### 2.3 什么是事务

一次业务可能改多张表（下单 = 减库存 + 生成订单 + 扣款），要么全成功、要么全回滚，这就是事务。`@Transactional` 让它自动提交或回滚。

### 2.4 连接池解决什么问题

`DriverManager.getConnection()` 每次都要走 TCP 握手、身份认证、会话初始化，本机 MySQL 8 上一次大约 30~50 ms，跨网络更久。接口 QPS 上来后，这部分开销会超过 SQL 本身。

连接池的做法是启动时先建好一批连接，用完归还池子而不是真正关闭，请求来了直接取用，省掉建连开销。

有三条约束要记住：

- 数据库能承受的连接数有上限（MySQL `max_connections` 默认 151）。所有应用实例的池上限之和不能超过它，否则报 `Too many connections`。
- 池不是越大越好。连接数远超 MySQL 的处理能力时，请求只是排进更长的队列，单条 SQL 延迟反而变高。HikariCP 官方给的起点是 `CPU 核数 * 2 + 磁盘数`。
- 池被占满时的表现由 `connection-timeout` 决定：默认 30 秒内取不到连接就抛 `SQLTransientConnectionException: Connection is not available`。

Spring Boot 默认的连接池是 HikariCP，随 `spring-boot-starter-jdbc` 一起引入，不需要额外依赖。

### 2.5 MySQL 常用数据类型与 Java 映射

先按「数据长什么样」选类型，再按类型选 Java 类。

| 用在什么字段上 | MySQL 类型 | 取值范围 / 说明 | Java 类型 |
| --- | --- | --- | --- |
| 自增主键 | `BIGINT` | 大整数，可加 `UNSIGNED` 只留非负 | `Long` |
| 用户名、标题 | `VARCHAR(n)` | 变长，n 是最大字符数，按需给 | `String` |
| 手机号、身份证 | `CHAR(n)` | 定长，长度固定且查询频繁时比 `VARCHAR` 略快 | `String` |
| 状态、性别、是否会员 | `TINYINT` | -128~127；`UNSIGNED` 时 0~255 | `Integer` / `Boolean` |
| 年龄、积分 | `INT` / `SMALLINT` | 常规计数 | `Integer` |
| 金额 | `DECIMAL(m,d)` | 定点数，精确，`DECIMAL(12,2)` 表示最多 12 位、其中 2 位小数 | `BigDecimal` |
| 评分、比例 | `FLOAT` / `DOUBLE` | 浮点数，近似值，不适合存钱 | `Float` / `Double` |
| 取值固定的枚举 | `ENUM('A','B')` | 只能取列出的值，插入其他值会被拒绝 | `String` |
| 生日、入职日期 | `DATE` | `yyyy-MM-dd` | `LocalDate` |
| 上下午班时间 | `TIME` | `HH:mm:ss` | `LocalTime` |
| 创建时间、最后登录 | `DATETIME` | `yyyy-MM-dd HH:mm:ss`，不做时区换算 | `LocalDateTime` |
| 跨时区的时间点 | `TIMESTAMP` | 存 UTC、取出按会话时区换算，范围只到 2038 年 | `LocalDateTime` |
| 个人简介、文章正文 | `TEXT` | 最大 64 KB，超长用 `MEDIUMTEXT` | `String` |
| 标签、扩展参数 | `JSON` | 结构不固定又想在库里查 | `List` / `Map`，配 type handler |
| 头像、附件内容 | `BLOB` / `VARBINARY` | 二进制。生产更常见的做法是存文件服务地址，而不是把文件塞进库 | `byte[]` |

两个容易踩的点：

- 金额和任何参与精确计算的数用 `DECIMAL`，不要用 `FLOAT` / `DOUBLE`，浮点的舍入误差累加后会算错账。
- `UNSIGNED` 不是「更省空间」，而是「不允许负数」。给年龄、积分这类字段加 `UNSIGNED` 之后，写 `-1` 会被数据库直接拒绝并报 `Out of range value`，业务层的校验要提前拦住这类值。

### 2.6 MyBatis-Plus 帮你做了哪些事

| 能力 | 用法 | 省掉了什么 |
| --- | --- | --- |
| 通用 CRUD | `BaseMapper` / `IService` | 单表增删改查的 SQL 与 XML |
| 条件构造器 | `LambdaQueryWrapper` / `LambdaUpdateWrapper` | 手拼 `WHERE`，且字段名靠方法引用，改字段不会漏改字符串 |
| 分页 | `PaginationInnerInterceptor` + `Page` | 自己写 `LIMIT` 与 `COUNT` |
| 字段自动填充 | `MetaObjectHandler` + `@TableField(fill = ...)` | 每个 Service 方法里手写 `setCreateTime(new Date())` |
| 逻辑删除 | `@TableLogic` | 手写 `UPDATE ... SET deleted = 1` 与每条查询补 `deleted = 0` |
| 乐观锁 | `@Version` + `OptimisticLockerInnerInterceptor` | 手拼版本号条件 |

---

## 3. 环境准备

1. 安装并启动 MySQL（本教程用 `backend_learning` 库）；
2. 建库与建表：

```sql
CREATE DATABASE IF NOT EXISTS backend_learning DEFAULT CHARSET utf8mb4;

USE backend_learning;

CREATE TABLE t_user
(
    id              BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    username        VARCHAR(50)  NOT NULL COMMENT '登录名',
    password        CHAR(64)     NOT NULL COMMENT '密码，SHA-256 摘要定长 64 位',
    nickname        VARCHAR(50) COMMENT '昵称',
    gender          TINYINT      NOT NULL DEFAULT 0 COMMENT '性别，0 未设置 / 1 男 / 2 女',
    age             TINYINT UNSIGNED COMMENT '年龄',
    member_level    ENUM ('BRONZE','SILVER','GOLD') NOT NULL DEFAULT 'BRONZE' COMMENT '会员等级',
    points          INT          NOT NULL DEFAULT 0 COMMENT '积分',
    balance         DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '账户余额，金额用定点数',
    score           FLOAT COMMENT '推荐评分，近似小数用浮点',
    phone           VARCHAR(20) COMMENT '手机号',
    email           VARCHAR(100) COMMENT '邮箱',
    avatar          VARCHAR(255) COMMENT '头像地址',
    intro           TEXT COMMENT '个人简介，长文本',
    tags            JSON COMMENT '标签数组，如 ["Java","篮球"]',
    birthday        DATE COMMENT '生日',
    work_time       TIME COMMENT '每日上班时间',
    vip             TINYINT      NOT NULL DEFAULT 0 COMMENT '是否会员，0 否 / 1 是',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态，1 正常 / 0 禁用',
    last_login_time DATETIME COMMENT '最后登录时间',
    create_time     DATETIME     NOT NULL COMMENT '创建时间，MyBatis-Plus 自动填充',
    update_time     DATETIME     NOT NULL COMMENT '更新时间，MyBatis-Plus 自动填充',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除 / 1 已删除',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_status_gender (status, gender)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';
```

建表脚本放在 `src/main/resources/schema.sql`，初始化数据放在 `data.sql`，配合 `spring.sql.init` 在应用启动时自动执行。

两个脚本都在开头写了 `DROP TABLE IF EXISTS`，也就是**每次启动都把表删掉重建并重写示例数据**。这样改了字段不用手工 `ALTER`，表结构永远和实体类一致；代价是数据不持久。真实项目用 Flyway / Liquibase 做版本化迁移，不会这么干。

> 用命令行手动执行脚本时记得先选中库：`mysql -uroot -p -D backend_learning < schema.sql`。

依赖（Spring Boot 4.1.1 下实测可用）：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
    <version>3.5.17</version>
</dependency>
<!-- 分页插件依赖 JSqlParser，3.5.9 起拆成独立构件，需单独引入 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
    <version>3.5.17</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

> 版本要点：Spring Boot 4 用 `mybatis-plus-spring-boot4-starter`（Spring Boot 3 对应 `-spring-boot3-starter`）；`mysql-connector-j` 的版本由 `spring-boot-dependencies` 管理，无需手写。

> 连接池用 Spring Boot 默认的 HikariCP（无需额外依赖），参数配置与监控见步骤 10。要换 Druid 需引入 `druid-spring-boot-4-starter`（Spring Boot 4 对应版本，Spring Boot 3 用 `druid-spring-boot-3-starter`）。

---

## 4. 动手实战

### 步骤 1：配置数据源与初始化脚本

`application.yml`：

```yaml
server:
  port: 8005

spring:
  application:
    name: 05-数据访问
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/backend_learning?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456            # 按本机 MySQL 实际密码修改
    hikari:                     # 连接池参数，不写就用默认值
      pool-name: backend-learning-pool
      maximum-pool-size: 10     # 池上限
      minimum-idle: 5           # 空闲保底连接数
      connection-timeout: 3000  # 取连接超时，毫秒
      max-lifetime: 1800000     # 连接最长存活，毫秒
  # 启动时先执行 schema.sql 建表，再执行 data.sql 写入示例数据
  # MySQL 属于非内嵌库，必须显式写 always 才会执行
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
      data-locations: classpath:data.sql
      encoding: UTF-8

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true   # 下划线转驼峰
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 打印 SQL，开发期用
  global-config:
    banner: false
    db-config:
      id-type: auto                      # 主键自增
      table-underline: true              # 实体驼峰转表名下划线
      # 逻辑删除：delete 变成 update deleted=1，查询自动追加 deleted=0
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

`spring.sql.init.mode` 的默认值是 `embedded`，只对内嵌库（H2、HSQLDB）生效。连 MySQL 这类外部库必须显式改成 `always`，否则脚本不会执行。

数据源与连接池的完整说明见步骤 10。

### 步骤 2：实体类

实体字段和表字段一一对应，靠 `map-underscore-to-camel-case` 把 `create_time` 映射到 `createTime`。

```java
package top.mqxu.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@TableName(value = "t_user", autoResultMap = true)
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 只接收不返回，避免接口把密码摘要吐出去 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String nickname;

    private Integer gender;

    private Integer age;

    private String memberLevel;

    private Integer points;

    private BigDecimal balance;

    private Float score;

    private String phone;

    private String email;

    private String avatar;

    private String intro;

    /** JSON 列，autoResultMap = true 才会走 typeHandler 反序列化 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> tags;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime workTime;

    private Boolean vip;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
```

讲解：

- `@TableName("t_user")` 指定表名；`@TableId(type = IdType.AUTO)` 自增主键；
- `autoResultMap = true` 是 JSON 字段能查出来的前提，漏了它 `tags` 会是 null；
- `@JsonProperty(access = WRITE_ONLY)` 让 `password` 可以随请求体传进来，但不出现在响应里；
- `@JsonFormat` 解决时间格式：Jackson 3 默认只认带 `T` 的 ISO 格式（`2008-03-25T01:58:41`），加上注解后 `2008-03-25 01:58:41` 这种 SQL 风格的字符串也能正常接收和返回；
- `Boolean vip` 对应 `TINYINT`，JDBC 读出来非 0 即 true。

### 步骤 3：Mapper

```java
package top.mqxu.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.mqxu.mybatis.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

接口体是空的，方法都由 `BaseMapper` 提供：

| 方法 | 作用 |
| --- | --- |
| `insert(entity)` | 新增，主键回填到实体 |
| `deleteById(id)` / `deleteByIds(ids)` | 按主键删除（配 `@TableLogic` 时是逻辑删除） |
| `delete(Wrapper)` / `deleteByMap(map)` | 按条件删除 |
| `updateById(entity)` | 按主键更新，只更新非 null 字段 |
| `update(entity, Wrapper)` | 按条件更新 |
| `selectById(id)` | 按主键查询 |
| `selectBatchIds(ids)` | 按主键批量查询 |
| `selectList(Wrapper)` / `selectOne(Wrapper)` | 条件查询 |
| `selectCount(Wrapper)` | 计数 |
| `selectPage(page, Wrapper)` | 分页 |
| `selectObjs(Wrapper)` | 只取第一列的值 |
| `selectMaps(Wrapper)` | 返回 `Map` 列表，做分组统计时用 |

### 步骤 4：Service 层把基础 CRUD 写全

`IService` 已经提供了完整的单表操作，直接继承即可用。下面只补充带业务语义的封装和各类条件查询。

```java
public interface UserService extends IService<User> {

    /* 增 */
    boolean createUser(User user);
    boolean createUserBatch(List<User> users);

    /* 删 */
    boolean removeUser(Long id);
    boolean removeUserBatch(List<Long> ids);
    boolean removeUserByStatus(Integer status);

    /* 改 */
    boolean modifyUser(User user);
    boolean modifyUserStatus(Long id, Integer status);
    boolean recharge(Long id, BigDecimal amount);

    /* 查（基础） */
    User getUser(Long id);
    List<User> listUsers();
    Page<User> pageUsers(long current, long size);

    /* 查（Lambda 条件） */
    List<User> listByCondition(UserQuery query);
    Page<User> pageByCondition(UserQuery query, long current, long size);
    List<User> searchByKeyword(String keyword);
    List<User> listByAgeRange(Integer minAge, Integer maxAge);
    List<User> listTop(Integer limit);
    List<String> listUsernames();
    List<User> listByTag(String tag);
    boolean existsUsername(String username);
    long countByGender(Integer gender);
    Map<String, Object> statistics();
}
```

`IService` 自带的方法和上面这些封装的对应关系：

| 操作 | `IService` / `IRepository` 自带 | 本模块封装 |
| --- | --- | --- |
| 新增 | `save(entity)` | `createUser`（补默认值 + 校验 + 事务） |
| 批量新增 | `saveBatch(collection, batchSize)` | `createUserBatch` |
| 按主键更新 | `updateById(entity)` | `modifyUser`（先确认记录存在） |
| 批量更新 | `updateBatchById(collection, batchSize)` | — |
| 按主键删除 | `removeById(id)` | `removeUser` |
| 批量删除 | `removeByIds(collection)` | `removeUserBatch` |
| 条件删除 | `remove(Wrapper)` | `removeUserByStatus` |
| 批量查询 | `listByIds(collection)` | — |
| 按主键查询 | `getById(id)` | `getUser` |
| 查询全部 | `list()` | `listUsers` |
| 条件列表 | `list(Wrapper)` | `listByCondition` |
| 分页 | `page(Page)` / `page(Page, Wrapper)` | `pageUsers` / `pageByCondition` |
| 单条 | `getOne(Wrapper)` | — |
| 计数 | `count()` / `count(Wrapper)` | `countByGender` |
| 存在判断 | `exists(Wrapper)` | `existsUsername` |

实现类里几个有代表性的方法：

```java
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String DEFAULT_PASSWORD = "123456";
    private static final int BATCH_SIZE = 100;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean createUser(User user) {
        applyDefaults(user);

        // 第一步：先写库
        save(user);

        // 第二步：后续业务校验失败，抛异常观察第一步是否被回滚
        // age 列是 TINYINT UNSIGNED，-1 这类负数在数据库层就会被拒绝，这里校验的是业务上不合理的取值
        if (user.getAge() != null && (user.getAge() < 0 || user.getAge() > 150)) {
            throw new RuntimeException("年龄超出合理范围，回滚");
        }
        return true;
    }

    @Override
    public boolean modifyUser(User user) {
        if (user.getId() == null) {
            throw new IllegalArgumentException("更新时必须携带主键 id");
        }
        if (getById(user.getId()) == null) {
            throw new IllegalArgumentException("用户不存在：" + user.getId());
        }
        return updateById(user);
    }

    @Override
    public boolean modifyUserStatus(Long id, Integer status) {
        return update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getStatus, status));
    }

    @Override
    public boolean recharge(Long id, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("充值金额必须大于 0");
        }
        User user = getById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在：" + id);
        }
        user.setBalance(user.getBalance().add(amount));
        // 实体带 version，updateById 会拼上 AND version = ? 并把版本加一
        return updateById(user);
    }
}
```

`applyDefaults` 负责给未传的字段补上默认值，避免 `NOT NULL` 列报错，同时把明文密码转成 SHA-256 摘要（64 位十六进制，刚好对上表里的 `CHAR(64)`）：

```java
private void applyDefaults(User user) {
    if (!StringUtils.hasText(user.getUsername())) {
        throw new IllegalArgumentException("用户名不能为空");
    }
    if (!StringUtils.hasText(user.getPassword())) {
        user.setPassword(sha256(DEFAULT_PASSWORD));
    }
    if (user.getGender() == null) {
        user.setGender(0);
    }
    if (user.getMemberLevel() == null) {
        user.setMemberLevel("BRONZE");
    }
    if (user.getPoints() == null) {
        user.setPoints(0);
    }
    if (user.getBalance() == null) {
        user.setBalance(BigDecimal.ZERO);
    }
    if (user.getVip() == null) {
        user.setVip(false);
    }
    if (user.getStatus() == null) {
        user.setStatus(1);
    }
}

private String sha256(String raw) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
    }
}
```

跑起来之后，MP 生成的 SQL 长这样：

```sql
-- save(user)
INSERT INTO t_user ( username, password, nickname, gender, age, member_level, points, balance,
                     tags, birthday, work_time, vip, status, create_time, update_time, deleted, version )
VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )

-- updateById(user)，实体带 version
UPDATE t_user SET username=?, ..., version=?
WHERE id=? AND version=? AND deleted=0

-- removeById(id)，逻辑删除
UPDATE t_user SET update_time=?, deleted=1 WHERE id=? AND deleted=0
```

### 步骤 5：字段自动填充

`create_time` / `update_time` / `deleted` / `version` 这几列不需要业务代码关心，交给 `MetaObjectHandler`：

```java
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
        strictInsertFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        strictInsertFill(metaObject, "deleted", () -> 0, Integer.class);
        strictInsertFill(metaObject, "version", () -> 0, Integer.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 这里不能用 strictUpdateFill：严格填充只在字段为 null 时才写，
        // 而从库里查出来的实体 updateTime 已有旧值，会被原样写回去，等于没刷新
        setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}
```

要点：

- 填充只对实体上标了 `@TableField(fill = ...)` 的字段生效。`FieldFill.INSERT` 只在新增时填，`FieldFill.INSERT_UPDATE` 新增和更新都填；
- 新增用 `strictInsertFill` 是安全的：只在传入值为 null 时才补默认值，调用方显式传了 `createTime` 就尊重传入值；
- 更新不能用 `strictUpdateFill`。它的判断和新增一样是「字段为 null 才填」，而更新时实体通常是从库里查出来的，`updateTime` 已经有旧值，结果就是时间永远不变。用 `setFieldValByName` 直接覆盖。

### 步骤 6：逻辑删除与乐观锁

两个都是「加注解 + 注册插件」就生效。

逻辑删除：实体字段加 `@TableLogic`，或者像本模块这样在 `application.yml` 里配置 `logic-delete-field` 全局生效。

```yaml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

效果是 `DELETE` 语句被改写成 `UPDATE`，所有查询自动追加条件：

```sql
-- removeById(7)
UPDATE t_user SET update_time=?, deleted=1 WHERE id=? AND deleted=0

-- selectById(7)，查不到已删除的行
SELECT ... FROM t_user WHERE id=? AND deleted=0

-- selectList 带条件，用户条件会被括号包住，不会被 OR 漏出去
SELECT ... FROM t_user WHERE deleted=0 AND ((username LIKE ? OR nickname LIKE ?))
```

乐观锁：`@Version` 注解 + 在配置类里注册插件。

```java
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 顺序有讲究：分页在前，乐观锁在后
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
```

更新时 MP 会拼上版本条件并把版本加一，`UPDATE` 影响行数为 0 就说明有人先改过：

```sql
UPDATE t_user SET nickname=?, version=1 WHERE id=? AND version=0 AND deleted=0
```

```java
User first = userService.getUser(id);
User stale = userService.getUser(id);

first.setNickname("第一次改");
userService.modifyUser(first);      // 成功，version 0 -> 1

stale.setNickname("第二次改");
userService.modifyUser(stale);      // 返回 false，WHERE version=0 已经不匹配
```

注意版本号是实体上的值，只有实体带着 `version` 时才会拼条件。接口收到 `{"nickname":"x"}` 这种不含 `version` 的请求体时，更新不做版本校验——要不要让客户端回传版本号，取决于业务对并发冲突的容忍度。

### 步骤 7：Lambda 条件构造器

`LambdaQueryWrapper` 用方法引用指定字段（`User::getAge` 而不是 `"age"` 字符串），改字段名时编译器会报错，比手写字符串安全。

常用写法：

| 需求 | 写法 |
| --- | --- |
| 等于 / 不等于 | `.eq(User::getGender, 1)` / `.ne(...)` |
| 模糊 | `.like(User::getUsername, "张")`，还有 `likeLeft` / `likeRight` |
| 大小比较 | `.gt` / `.ge` / `.lt` / `.le` |
| 区间 | `.between(User::getAge, 20, 30)` |
| 集合 | `.in(User::getId, ids)` / `.notIn(...)` |
| 空值判断 | `.isNull` / `.isNotNull` |
| 排序 | `.orderByAsc(User::getId)` / `.orderByDesc(User::getPoints)` |
| 或 | `.or()` |
| 拼原生 SQL 片段 | `.apply("JSON_CONTAINS(tags, JSON_QUOTE({0}))", tag)` |
| 追加到末尾 | `.last("LIMIT 3")` |
| 只查某几列 | `.select(User::getUsername)` |
| 分组 | `QueryWrapper.select("gender", "COUNT(*) AS cnt").groupBy("gender")` |
| 条件可选 | 第一个参数传布尔值，`.eq(条件, 字段, 值)`，条件为 false 时该分支不进 SQL |

几个实际用到的例子：

```java
/** 动态条件：条件为空的分支不参与 SQL */
private LambdaQueryWrapper<User> buildCondition(UserQuery query) {
    UserQuery condition = query == null ? new UserQuery() : query;
    return new LambdaQueryWrapper<User>()
            .eq(condition.getGender() != null, User::getGender, condition.getGender())
            .eq(condition.getStatus() != null, User::getStatus, condition.getStatus())
            .eq(condition.getVip() != null, User::getVip, condition.getVip())
            .eq(StringUtils.hasText(condition.getMemberLevel()),
                    User::getMemberLevel, condition.getMemberLevel())
            .ge(condition.getMinAge() != null, User::getAge, condition.getMinAge())
            .le(condition.getMaxAge() != null, User::getAge, condition.getMaxAge())
            .and(StringUtils.hasText(condition.getKeyword()),
                    w -> w.like(User::getUsername, condition.getKeyword())
                            .or()
                            .like(User::getNickname, condition.getKeyword()))
            .orderByDesc(User::getId);
}
```

```java
/** 模糊查询：and 包一层括号，避免 OR 把其他条件一起放开 */
public List<User> searchByKeyword(String keyword) {
    return list(new LambdaQueryWrapper<User>()
            .and(StringUtils.hasText(keyword),
                    w -> w.like(User::getUsername, keyword)
                            .or()
                            .like(User::getNickname, keyword))
            .orderByAsc(User::getId));
}
```

```java
/** 取前 N 条：last 会原样拼到 SQL 末尾，只能拼自己算过的数字，不能拼用户输入 */
public List<User> listTop(Integer limit) {
    int size = limit == null || limit <= 0 ? 3 : Math.min(limit, 50);
    return list(new LambdaQueryWrapper<User>()
            .orderByDesc(User::getPoints)
            .last("LIMIT " + size));
}
```

```java
/** 只查一列：select 指定列 + listObjs 取值，不把整行查出来 */
public List<String> listUsernames() {
    return listObjs(new LambdaQueryWrapper<User>()
            .select(User::getUsername)
            .orderByAsc(User::getId), Object::toString);
}
```

```java
/** JSON 列查询：{0} 会被替换成参数占位符，拼的是值不是 SQL */
public List<User> listByTag(String tag) {
    return list(new LambdaQueryWrapper<User>()
            .apply("JSON_CONTAINS(tags, JSON_QUOTE({0}))", tag)
            .orderByAsc(User::getId));
}
```

```java
/** 分组统计：getMap 取单行聚合，listMaps 取多行分组结果 */
public Map<String, Object> statistics() {
    Map<String, Object> result = new LinkedHashMap<>();

    Map<String, Object> aggregate = getMap(new QueryWrapper<User>()
            .select("COUNT(*) AS totalCount",
                    "IFNULL(SUM(balance), 0) AS totalBalance",
                    "IFNULL(MAX(age), 0) AS maxAge"));
    if (aggregate != null) {
        result.putAll(aggregate);
    }

    result.put("genderCount", listMaps(new QueryWrapper<User>()
            .select("gender", "COUNT(*) AS cnt")
            .groupBy("gender")
            .orderByAsc("gender")));
    return result;
}
```

更新条件用 `LambdaUpdateWrapper`，`set` 指定的字段会出现在 `SET` 子句里：

```java
public boolean modifyUserStatus(Long id, Integer status) {
    return update(new LambdaUpdateWrapper<User>()
            .eq(User::getId, id)
            .set(User::getStatus, status));
}
```

### 步骤 8：控制器

```java
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /* 增 */
    @PostMapping
    public User create(@RequestBody User user) {
        userService.createUser(user);
        return user;
    }

    @PostMapping("/batch")
    public int createBatch(@RequestBody List<User> users) {
        userService.createUserBatch(users);
        return users.size();
    }

    /* 查 */
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.getUser(id);
    }

    @GetMapping
    public List<User> list(UserQuery query) {
        return userService.listByCondition(query);
    }

    @GetMapping("/page")
    public Page<User> page(UserQuery query,
                           @RequestParam(defaultValue = "1") long current,
                           @RequestParam(defaultValue = "10") long size) {
        return userService.pageByCondition(query, current, size);
    }

    @GetMapping("/keyword")
    public List<User> keyword(@RequestParam String keyword) {
        return userService.searchByKeyword(keyword);
    }

    @GetMapping("/age-range")
    public List<User> ageRange(@RequestParam(required = false) Integer minAge,
                               @RequestParam(required = false) Integer maxAge) {
        return userService.listByAgeRange(minAge, maxAge);
    }

    @GetMapping("/top")
    public List<User> top(@RequestParam(defaultValue = "3") Integer limit) {
        return userService.listTop(limit);
    }

    @GetMapping("/usernames")
    public List<String> usernames() {
        return userService.listUsernames();
    }

    @GetMapping("/tag")
    public List<User> byTag(@RequestParam String tag) {
        return userService.listByTag(tag);
    }

    @GetMapping("/exists")
    public boolean exists(@RequestParam String username) {
        return userService.existsUsername(username);
    }

    @GetMapping("/count")
    public long count(@RequestParam(required = false) Integer gender) {
        return userService.countByGender(gender);
    }

    @GetMapping("/statistics")
    public Map<String, Object> statistics() {
        return userService.statistics();
    }

    /* 改 */
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        userService.modifyUser(user);
        return userService.getUser(id);
    }

    @PutMapping("/{id}/status")
    public boolean updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return userService.modifyUserStatus(id, status);
    }

    @PostMapping("/{id}/recharge")
    public User recharge(@PathVariable Long id, @RequestParam BigDecimal amount) {
        userService.recharge(id, amount);
        return userService.getUser(id);
    }

    /* 删 */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return userService.removeUser(id);
    }

    @DeleteMapping("/batch")
    public boolean deleteBatch(@RequestParam List<Long> ids) {
        return userService.removeUserBatch(ids);
    }

    @DeleteMapping("/status/{status}")
    public boolean deleteByStatus(@PathVariable Integer status) {
        return userService.removeUserByStatus(status);
    }
}
```

`UserQuery` 是查询条件对象，字段全为空时等价于查全部：

```java
@Data
public class UserQuery {
    private String keyword;        // 匹配 username 或 nickname
    private Integer gender;
    private String memberLevel;
    private Boolean vip;
    private Integer status;
    private Integer minAge;
    private Integer maxAge;
}
```

### 步骤 9：事务

目标：多个写操作要么都成功、要么都回滚。

```java
@Transactional(rollbackFor = Exception.class)
@Override
public boolean createUser(User user) {
    applyDefaults(user);
    save(user);                        // 第一步先写库
    if (user.getAge() != null && (user.getAge() < 0 || user.getAge() > 150)) {
        throw new RuntimeException("年龄超出合理范围，回滚");   // 第二步失败
    }
    return true;
}
```

讲解：

- `@Transactional(rollbackFor = Exception.class)` 表示遇到任何异常都回滚（默认只对 `RuntimeException` / `Error` 回滚，显式指定更明确）；
- 事务方法内抛异常，前面的写操作会被回滚；
- 事务靠 Spring 的代理生效，同类内部方法自调用（`this.xxx()`）不走代理，`@Transactional` 会失效。要么把方法拆到另一个 Bean，要么从外部调用。

---

### 步骤 10：连接池参数与监控

#### 10.1 HikariCP 常用参数

配置前缀 `spring.datasource.hikari`。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `maximum-pool-size` | 10 | 池上限，同时也是能同时访问数据库的连接数上限 |
| `minimum-idle` | 等于 `maximum-pool-size` | 空闲保底连接数。设小可以省连接，代价是流量回升时要现建连接 |
| `connection-timeout` | 30000 ms | 从池里取连接的等待上限，超时抛 `SQLTransientConnectionException` |
| `idle-timeout` | 600000 ms | 空闲连接回收时间，只在 `minimum-idle` 小于 `maximum-pool-size` 时生效 |
| `max-lifetime` | 1800000 ms | 连接最长存活时间，必须小于 MySQL 的 `wait_timeout`（默认 28800 秒），否则会拿到已被服务端断开的连接 |
| `keepalive-time` | 0（关闭） | 定期给空闲连接发探活，避免被防火墙/中间件掐断 |
| `pool-name` | `HikariPool-1` | 池名字，多数据源时用来区分日志 |

#### 10.2 池大小怎么定

- 起点取 `CPU 核数 * 2 + 磁盘数`，这是 HikariCP 官方给的经验值，不是越大越好；
- 上限受 MySQL 约束：所有应用实例的 `maximum-pool-size` 之和要小于 `max_connections`（默认 151），还要给运维连接和其他服务留出余量；
- 判断当前连接数：MySQL 里执行 `SHOW STATUS LIKE 'Threads_connected'`，或 `SHOW PROCESSLIST` 看连接都在干什么。

池打满时有两种典型现象：应用侧日志出现 `Connection is not available, request timed out after 3000ms`；MySQL 侧连接数顶到上限后新连接报 `Too many connections`。两种情况的排查方向不同，前者多半是慢 SQL 或长事务占着连接不放，后者是池总量配得太大。

#### 10.3 怎么看连接池状态

启动日志里能直接看到池的建立与告警：

```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
```

运行期想看得更细，用 Druid 的监控页（见 10.4）。

#### 10.4 换成 Druid 并打开监控页

Druid 参数与 HikariCP 的对应关系：

| 语义 | HikariCP | Druid |
| --- | --- | --- |
| 池上限 | `maximum-pool-size` | `max-active` |
| 最小空闲 | `minimum-idle` | `min-idle` |
| 取连接超时 | `connection-timeout` | `max-wait` |
| 空闲回收 | `idle-timeout` | `min-evictable-idle-time-millis` |

本模块把 Druid 放在 Maven profile 里，默认构建仍是 HikariCP，需要时这样启动：

```bash
mvn -pl 05-mybatis -Pdruid spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=druid"
```

配置见 `src/main/resources/application-druid.yml`，监控页地址 `http://localhost:8005/druid/`，账号密码在 `stat-view-servlet` 里配置。登录后 Datasource 页能看到当前连接数、活跃连接数、执行过的 SQL 与慢 SQL 列表。

> 注意：Druid 依赖只在 `druid` profile 下引入。如果把它写进常驻依赖，Druid 的自动配置会顶掉 HikariCP，步骤 1 里的 `hikari` 参数就全部失效了。

---

## 5. 完整代码与目录结构

```
05-mybatis
└── src
    ├── main
    │   ├── java/top/mqxu/mybatis
    │   │   ├── MybatisApplication.java
    │   │   ├── controller/UserController.java
    │   │   ├── service/UserService.java
    │   │   ├── service/impl/UserServiceImpl.java
    │   │   ├── mapper/UserMapper.java
    │   │   ├── entity/User.java
    │   │   ├── dto/UserQuery.java
    │   │   ├── config/MybatisPlusConfig.java
    │   │   └── config/MyMetaObjectHandler.java
    │   └── resources
    │       ├── application.yml
    │       ├── application-druid.yml
    │       ├── schema.sql
    │       └── data.sql
    └── test/java/top/mqxu/mybatis
        ├── UserServiceTest.java
        ├── UserLambdaQueryTest.java
        └── UserApiTest.java
```

> `IService` 与 `ServiceImpl` 在 3.5.9 之后的构件里移到了 `com.baomidou.mybatisplus.spring.service` 包下，IDE 提示旧路径 `extension.service` 时直接改成新路径。

---

## 6. 运行与验证

```bash
mvn -pl 05-mybatis spring-boot:run
```

> 不加 `-am`：加上 `-am` 会把父工程一并放进 reactor，`spring-boot:run` 在父工程（pom 打包、无主类）上会直接失败。

启动后 `t_user` 里有 4 个可查到的示例用户，另有一条 `deleted = 1` 的记录。逐个接口验证：

| 操作 | 请求 | 期望 |
| --- | --- | --- |
| 新增 | `POST /users` body `{"username":"zhouba","age":28,"tags":["Java"]}` | 返回带 id、`createTime`、`version=0` 的用户，`password` 不出现 |
| 批量新增 | `POST /users/batch` body 为数组 | 返回成功条数 |
| 详情 | `GET /users/1` | 返回完整用户，含 `tags` 数组 |
| 条件列表 | `GET /users?gender=1&minAge=20&maxAge=30` | 只返回符合条件的用户 |
| 条件分页 | `GET /users/page?current=1&size=3` | 返回 `records/total/current/pages` |
| 模糊查询 | `GET /users/keyword?keyword=张` | 命中 nickname 为张三的记录 |
| 区间查询 | `GET /users/age-range?minAge=20&maxAge=25` | 按年龄升序返回 |
| 取前 N | `GET /users/top?limit=2` | 按积分倒序的前两条 |
| 单列 | `GET /users/usernames` | 字符串数组 |
| JSON 标签 | `GET /users/tag?tag=Java` | 命中标签含 Java 的用户 |
| 是否存在 | `GET /users/exists?username=admin` | `true` / `false` |
| 计数 | `GET /users/count?gender=1` | 数字 |
| 汇总统计 | `GET /users/statistics` | `totalCount` / `totalBalance` / `maxAge` / `genderCount` |
| 更新 | `PUT /users/2` body `{"nickname":"张三改名","age":21}` | 返回更新后的用户，`updateTime` 已刷新 |
| 改状态 | `PUT /users/2/status?status=0` | `true` |
| 充值 | `POST /users/3/recharge?amount=100.25` | 余额累加，`version` 加一 |
| 删除 | `DELETE /users/{id}` | `true`，再查返回 null，库里那行 `deleted=1` 仍在 |
| 批量删除 | `DELETE /users/batch?ids=8,9` | `true` |
| 按状态删除 | `DELETE /users/status/0` | `true` |
| 事务回滚 | `POST /users` body `{"username":"x","age":200}` | 返回 500「年龄超出合理范围，回滚」，库里不落数据 |

自动化测试连本地 MySQL（与 `application.yml` 用同一套数据源），每条用例前清空 `t_user`，跑之前先启动 MySQL：

```bash
mvn -pl 05-mybatis test
```

三个测试类共 42 个用例：`UserServiceTest` 覆盖增删改查、自动填充、逻辑删除、乐观锁与事务；`UserLambdaQueryTest` 覆盖各类条件查询；`UserApiTest` 逐个验证上面的接口表。

> 注意：`schema.sql` 每次启动都会重建表，DevTools 热重启同样会触发，数据会被重置成 `data.sql` 里的示例数据。

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 连接失败 | url / 账号密码错，或 MySQL 未启动 | 核对 `datasource` 配置 |
| 启动时 `schema.sql` 没执行 | `spring.sql.init.mode` 默认只对内嵌库生效 | 显式设成 `always` |
| 表找不到 | 实体与表名不一致 | 用 `@TableName` 指定，或统一命名 |
| 字段映射为 null | 下划线未转驼峰 | 开 `map-underscore-to-camel-case` |
| JSON 字段查出来是 null | 少了 `autoResultMap = true` | `@TableName(value = "t_user", autoResultMap = true)` |
| 编译找不到分页插件 | 没引入 JSqlParser 构件 | 加依赖 `mybatis-plus-jsqlparser` |
| 编译找不到 `IService` / `ServiceImpl` | 3.5.9 起类已换包 | 用 `com.baomidou.mybatisplus.spring.service` 下的同名类 |
| 分页返回全量数据 | 没注册 `PaginationInnerInterceptor` | 加 `MybatisPlusInterceptor` Bean |
| 插入 `age = -1` 报 `Out of range value for column 'age'` | 列是 `TINYINT UNSIGNED`，不接受负数 | 业务层先校验；确实要存负数就把列改成有符号类型 |
| 更新后 `update_time` 没变 | `strictUpdateFill` 只在字段为 null 时才填，查出来的实体已有旧值 | `updateFill` 里改用 `setFieldValByName` |
| `deleteById` 变成 `UPDATE`，物理行还在 | `@TableLogic` 逻辑删除生效 | 符合预期；要物理删除用原生 SQL 或另建 Mapper 方法 |
| `selectById` 查不到刚删的数据 | 逻辑删除后查询自动追加 `deleted = 0` | 符合预期；确实要看已删数据就绕过逻辑删除条件 |
| 乐观锁没生效 | 实体上的 `version` 为 null，MP 不拼版本条件 | 先 `getById` 拿到带版本的实体再改，或让客户端回传版本号 |
| 日志报 `Connection is not available, request timed out` | 池被占满 | 查慢 SQL 与长事务；确属高并发再调大 `maximum-pool-size` |
| MySQL 报 `Too many connections` | 各实例池上限之和超过 `max_connections` | 减小 `maximum-pool-size`，或调大 MySQL 的 `max_connections` |
| 用 Druid 时 Hikari 参数不生效 | Druid 自动配置顶掉了 HikariCP | 两者选一个，删掉另一套配置 |
| 事务没回滚 | 异常类型不在回滚范围，或方法是自调用 | 用 `rollbackFor = Exception.class`；别在同类内部自调用 |

---

## 8. 练习

### 8.1 课堂随堂练习

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 用 `getById` 查询一个不存在的 id | 返回 null，不报错 |
| 2 | 用 `LambdaQueryWrapper` 按 `age > 18` 且 `vip = true` 查询 | 两个条件都生效 |
| 3 | 新增一条 `age = 200` 的记录 | 接口返回 500，`count` 不变（事务回滚） |
| 4 | 逻辑删除一条记录后 `selectById` | 返回 null，直接查库能看到 `deleted = 1` |
| 5 | 用同一个实体对象连续调两次 `updateById` | 第二次返回 false，被乐观锁拦下 |

### 8.2 课后练习

- **基础**：给 `t_user` 加 `email` 字段，改用 `LambdaUpdateWrapper` 按 username 更新邮箱。
- **基础**：给 `score` 之外的字段补上 `@JsonFormat`，验证接口输入输出格式。
- **进阶**：新增一张 `t_course` 表（`id` / `name` / `teacher` / `credit`），练习 `saveBatch` 与 `removeByIds`，字段类型自行对照 2.5 的表来选。
- **进阶**：把 `maximum-pool-size` 调成 1，用并发请求压同一个接口，复现 `Connection is not available`，再调回默认值，对照 Druid 监控页上的活跃连接数。
- **挑战**：新增一张 `t_order` 表，演示「创建订单 + 扣库存」两个写操作在一个事务里的原子性，并给 `t_order` 加上逻辑删除与自动填充。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| 数据类型 | 按数据形态选类型：金额用 `DECIMAL`、枚举用 `ENUM`、结构化数据用 `JSON`、时间用 `DATE` / `TIME` / `DATETIME` |
| 类型映射 | 时间对应 `LocalDate` / `LocalTime` / `LocalDateTime`，`TINYINT` 可映射 `Boolean` |
| 数据源 | `spring.datasource` 配 MySQL，默认 HikariCP |
| 连接池 | 池上限之和要小于 MySQL `max_connections`；超时与回收靠 `connection-timeout`、`max-lifetime` 控制 |
| 连接池监控 | HikariCP 看启动日志，Druid 用 `/druid/` 监控页 |
| `BaseMapper` | 免写 SQL 的通用 CRUD |
| `IService` | 在 Mapper 之上再包一层，批量、分页、计数都有现成方法 |
| Service 分层 | Controller → Service → Mapper，不跨层 |
| 自动填充 | `MetaObjectHandler` 管审计字段，更新时不要用 `strictUpdateFill` |
| 逻辑删除 | `@TableLogic`，删除变更新，查询自动过滤 |
| 乐观锁 | `@Version` + 插件，靠影响行数判断有没有人抢先改 |
| 条件构造器 | `LambdaQueryWrapper` 选条件，`LambdaUpdateWrapper` 改数据，`and(w -> ...)` 记得包括号 |
| `@Transactional` | 写操作原子性，显式 `rollbackFor`，注意自调用失效 |
| 分页插件 | `PaginationInnerInterceptor` + `Page` |

下一节 [06-redis](06-redis.md) 会在数据访问之上加缓存，解决热点数据的高频查询。

> 本节遵循的规范（官方 + 阿里手册）：三层分层职责单一、不跨层；实体类属性用包装类型；金额用 `DECIMAL` 不用浮点；事务方法显式 `rollbackFor`；Mapper 层不写业务逻辑。
