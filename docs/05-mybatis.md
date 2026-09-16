# 05 · 数据访问 —— 用 MyBatis-Plus 持久化数据

> - 难度：进阶
> - 前置模块：04-web
> - 预计时长：约 60 分钟
> - 对应代码：[05-mybatis](https://github.com/mqxu/backend-learning/tree/main/05-mybatis)

## 1. 本节导读

学完本节，你将能：

1. 配置数据源，连接 MySQL；
2. 用 MyBatis-Plus 的 `BaseMapper` 免写 SQL 完成增删改查；
3. 理解并落地 Service 分层与 `@Transactional` 事务；
4. 用分页插件做分页查询。

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

规则：**上层只能调用紧邻的下层**，不能跨层（Controller 不能直接碰 Mapper）。

### 2.3 什么是事务

一次业务可能改多张表（下单 = 减库存 + 生成订单 + 扣款），要么全成功、要么全回滚，这就是事务。`@Transactional` 让它自动提交或回滚。

---

## 3. 环境准备

1. 安装并启动 MySQL（本教程用 `backend_learning` 库）；
2. 建表：

```sql
CREATE DATABASE IF NOT EXISTS backend_learning DEFAULT CHARSET utf8mb4;

CREATE TABLE t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    age INT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

依赖（版本以官方最新、兼容当前 Spring Boot 为准）：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

> 连接池先用 Spring Boot 默认的 HikariCP（无需额外依赖）；如需 Druid 监控可自行替换为 `druid-spring-boot-starter`。

---

## 4. 动手实战

> 本模块当前是骨架，下面带你逐步补全持久化能力。

### 步骤 1：配置数据源

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
    password: root

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true   # 下划线转驼峰
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 打印 SQL，开发期用
```

### 步骤 2：实体类 + Mapper

实体映射表：

```java
package top.mqxu.mybatis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 *
 * @author mqxu
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private Integer age;

    private LocalDateTime createTime;
}
```

Mapper 接口：

```java
package top.mqxu.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.mqxu.mybatis.entity.User;

/**
 * 用户 Mapper
 *
 * @author mqxu
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

讲解：

- `@TableName("t_user")` 指定表名；`@TableId(type = IdType.AUTO)` 自增主键；
- `BaseMapper<User>` 提供了 `insert`、`deleteById`、`updateById`、`selectById`、`selectList` 等通用方法，**不用写任何 SQL**；
- `@Mapper` 标注为 Mapper 接口（或启动类加 `@MapperScan`）。

### 步骤 3：Service 分层

Service 接口：

```java
package top.mqxu.mybatis.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.mqxu.mybatis.entity.User;

/**
 * 用户服务
 *
 * @author mqxu
 */
public interface UserService extends IService<User> {
}
```

实现类：

```java
package top.mqxu.mybatis.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.mqxu.mybatis.entity.User;
import top.mqxu.mybatis.mapper.UserMapper;
import top.mqxu.mybatis.service.UserService;

/**
 * 用户服务实现
 *
 * @author mqxu
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
```

讲解：

- `IService<User>` 继承后自带更多方法（`save`、`list`、`page` 等）；
- `ServiceImpl<Mapper, Entity>` 提供默认实现，空实现类就有完整能力。

### 步骤 4：控制器与分页

分页需要先注册插件：

```java
package top.mqxu.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * @author mqxu
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

控制器：

```java
package top.mqxu.mybatis.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.mqxu.mybatis.entity.User;
import top.mqxu.mybatis.service.UserService;

import java.util.List;

/**
 * 用户接口
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public User create(@RequestBody User user) {
        userService.save(user);
        return user;
    }

    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping
    public List<User> list() {
        return userService.list();
    }

    @GetMapping("/page")
    public Page<User> page(@RequestParam(defaultValue = "1") long current,
                           @RequestParam(defaultValue = "10") long size) {
        return userService.page(new Page<>(current, size));
    }
}
```

### 步骤 5：事务

目标：多个写操作要么都成功、要么都回滚。

```java
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean save(User user) {
        // 第一个操作成功
        super.save(user);
        // 第二个操作故意抛错，观察是否回滚
        if (user.getAge() != null && user.getAge() < 0) {
            throw new RuntimeException("年龄非法，回滚");
        }
        return true;
    }
}
```

讲解：

- `@Transactional(rollbackFor = Exception.class)` 表示**遇到任何异常都回滚**（默认只对 `RuntimeException`/`Error` 回滚，显式指定更明确）；
- 事务方法内抛异常，前面的写操作会被回滚。

---

## 5. 完整代码与目录结构

```
05-mybatis
└── src/main
    ├── java/top/mqxu/mybatis
    │   ├── MybatisApplication.java
    │   ├── controller/UserController.java
    │   ├── service/UserService.java
    │   ├── service/impl/UserServiceImpl.java
    │   ├── mapper/UserMapper.java
    │   ├── entity/User.java
    │   └── config/MybatisPlusConfig.java
    └── resources/application.yml
```

---

## 6. 运行与验证

```bash
mvn -pl 05-mybatis -am spring-boot:run
```

| 操作 | 请求 | 期望 |
| --- | --- | --- |
| 新增 | `POST /users` body `{"username":"张三","age":20}` | 返回带 id 的用户 |
| 查询 | `GET /users/1` | 返回 id=1 的用户 |
| 列表 | `GET /users` | 返回 JSON 数组 |
| 分页 | `GET /users/page?current=1&size=5` | 返回 `records/total/current` |

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 连接失败 | url / 账号密码错，或 MySQL 未启动 | 核对 `datasource` 配置 |
| 表找不到 | 实体与表名不一致 | 用 `@TableName` 指定，或统一命名 |
| 字段映射为 null | 下划线未转驼峰 | 开 `map-underscore-to-camel-case` |
| 分页返回全量数据 | 没注册 `PaginationInnerInterceptor` | 加 `MybatisPlusInterceptor` Bean |
| 事务没回滚 | 异常类型不在回滚范围，或方法是自调用 | 用 `rollbackFor = Exception.class`；别在同类内部自调用 |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 用 `selectById` 查询一个不存在的 id | 返回 null，不报错 |
| 2 | 用 `LambdaQueryWrapper` 按 `age > 18` 查询 | 返回符合条件的用户 |
| 3 | 把 `age` 设成 -1 调新增接口 | 数据未插入（事务回滚） |

### 8.2 课后练习（课后完成并提交）

- **基础**：给 `t_user` 加 `email` 字段，同步实体类并用 `updateById` 更新一条记录。
- **进阶**：用 `LambdaQueryWrapper` 实现按 username 模糊查询 + 分页的接口。
- **挑战**：新增一张 `t_order` 表，演示「创建订单 + 扣库存」两个写操作在一个事务里的原子性。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| 数据源 | `spring.datasource` 配 MySQL，默认 HikariCP |
| `BaseMapper` | 免写 SQL 的通用 CRUD |
| Service 分层 | Controller → Service → Mapper，不跨层 |
| `@Transactional` | 写操作原子性，显式 `rollbackFor` |
| 分页插件 | `PaginationInnerInterceptor` + `Page` |

下一节 [06-redis](06-redis.md) 会在数据访问之上加缓存，解决热点数据的高频查询。

> 本节遵循的规范（官方 + 阿里手册）：三层分层职责单一、不跨层；实体类属性用包装类型；事务方法显式 `rollbackFor`；Mapper 层不写业务逻辑。
