# 05-mybatis · 数据访问
> 📖 详细教程：[docs/05-mybatis.md](../docs/05-mybatis.md)

> 状态：✅ 已实现（数据类型建表 / 数据初始化 / 通用 CRUD / 自动填充 / 逻辑删除 / 乐观锁 / Lambda 条件构造器 / 分页 / 连接池 / 事务 / 练习参考答案）

打通数据库持久化，并用一张用户表把 MySQL 常用数据类型、MyBatis-Plus 的常用特性完整走一遍。第 8 节的随堂练习与课后练习在本模块内都有参考实现和配套测试。

## 学习目标

- 用一张表覆盖 MySQL 常用数据类型，并把每种类型正确映射到 Java
- MyBatis-Plus 单表增删改查、批量操作、条件分页
- 字段自动填充、逻辑删除、乐观锁
- `LambdaQueryWrapper` / `LambdaUpdateWrapper` 写常见条件查询与更新
- Service 分层与事务管理、连接池参数配置与状态监控

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| 数据类型映射 | `BIGINT`→`Long`、`DECIMAL`→`BigDecimal`、`DATE/TIME/DATETIME`→`LocalDate/LocalTime/LocalDateTime`、`JSON`→`List`（配 `Jackson3TypeHandler`） |
| MyBatis-Plus | 通用 CRUD（`BaseMapper` / `IService`）、`LambdaQueryWrapper`、分页插件 |
| 自动填充 | `MetaObjectHandler` 写 `create_time` / `update_time` / `deleted` / `version` |
| 逻辑删除 | `@TableLogic`，delete 变 update `deleted = 1`，查询自动追加 `deleted = 0` |
| 乐观锁 | `@Version` + `OptimisticLockerInnerInterceptor`，更新拼 `AND version = ?` |
| 连接池 | 默认 HikariCP，池上限 / 超时 / 存活时间的取舍 |
| 连接池监控 | Druid `stat-view-servlet` 监控页，配套 `stat` 慢 SQL 记录 |
| 事务 | `@Transactional(rollbackFor = Exception.class)` |
| 分层 | Controller → Service → Mapper，不跨层 |

## 目录结构

```
05-mybatis
└── src
    ├── main
    │   ├── java/top/mqxu/mybatis
    │   │   ├── MybatisApplication.java
    │   │   ├── controller
    │   │   │   ├── UserController.java
    │   │   │   ├── CourseController.java           # 练习：批量操作用
    │   │   │   ├── ProductController.java          # 练习：库存
    │   │   │   ├── OrderController.java            # 练习：下单事务
    │   │   │   └── DiagnosticsController.java      # 连接池实验，仅 pool1 profile
    │   │   ├── service
    │   │   │   ├── UserService.java
    │   │   │   ├── CourseService.java
    │   │   │   ├── ProductService.java
    │   │   │   ├── OrderService.java
    │   │   │   └── impl/                           # 四个实现类
    │   │   ├── mapper/                             # UserMapper / CourseMapper / ProductMapper / OrderMapper
    │   │   ├── entity/                             # User / Course / Product / Order
    │   │   ├── dto/UserQuery.java
    │   │   ├── config/MybatisPlusConfig.java       # 分页插件 + 乐观锁插件
    │   │   └── config/MyMetaObjectHandler.java     # 字段自动填充
    │   └── resources
    │       ├── application.yml           # 数据源 + HikariCP 参数 + MP 全局配置
    │       ├── application-druid.yml     # Druid 版数据源（--spring.profiles.active=druid）
    │       ├── application-pool1.yml     # 池压到 1 个连接，做耗尽实验用
    │       ├── schema.sql                # 建表脚本，启动时自动执行
    │       └── data.sql                  # 初始化数据，启动时自动执行
    └── test/java/top/mqxu/mybatis
        ├── UserServiceTest.java          # 增删改查、自动填充、逻辑删除、乐观锁、事务
        ├── UserLambdaQueryTest.java      # Lambda 条件查询
        ├── UserApiTest.java              # 接口层
        ├── PracticeTest.java             # 随堂练习五题
        ├── HomeworkTest.java             # 课后练习四题
        └── OrderApiTest.java             # 商品与订单接口
```

## 运行方式

先建库（`backend_learning`），表和示例数据由 `schema.sql`、`data.sql` 在启动时自动创建：

```bash
mvn -pl 05-mybatis spring-boot:run
```

端口：`8005`

启动后库里有 6 条用户记录（其中 1 条 `deleted = 1`、1 条 `status = 0`）、3 条课程、2 件商品。

`schema.sql` 每次启动都会先 `DROP TABLE` 再建表，所以重启会重置数据，DevTools 热重启同样会重置。要保留数据，把 `schema.sql` 改成 `CREATE TABLE IF NOT EXISTS`、`data.sql` 改成 `INSERT IGNORE`。

主要接口：

| 操作 | 请求 |
| --- | --- |
| 新增 | `POST /users`，body `{"username":"zhouba","age":28,"tags":["Java"]}` |
| 批量新增 | `POST /users/batch`，body 为数组 |
| 详情 | `GET /users/{id}` |
| 条件列表 | `GET /users?gender=1&minAge=20&maxAge=30` |
| 条件分页 | `GET /users/page?current=1&size=5&status=1` |
| 模糊查询 | `GET /users/keyword?keyword=张` |
| 区间查询 | `GET /users/age-range?minAge=20&maxAge=26` |
| 取前 N | `GET /users/top?limit=3` |
| 单列 | `GET /users/usernames` |
| JSON 标签 | `GET /users/tag?tag=Java` |
| 是否存在 | `GET /users/exists?username=admin` |
| 计数 | `GET /users/count?gender=1` |
| 汇总统计 | `GET /users/statistics` |
| 更新 | `PUT /users/{id}` |
| 改邮箱 | `PUT /users/email?username=zhangsan&email=zhangsan@mqxu.top` |
| 改状态 | `PUT /users/{id}/status?status=0` |
| 充值 | `POST /users/{id}/recharge?amount=100` |
| 删除 | `DELETE /users/{id}` |
| 批量删除 | `DELETE /users/batch?ids=1,2` |
| 按状态删除 | `DELETE /users/status/{status}` |
| 课程列表 | `GET /courses` |
| 课程批量新增 | `POST /courses/batch`，body 为数组 |
| 课程批量删除 | `DELETE /courses/batch?ids=4,5` |
| 商品列表 | `GET /products` |
| 下单 | `POST /orders?userId=1&productId=1&quantity=2` |
| 我的订单 | `GET /orders?userId=1` |
| 取消订单 | `PUT /orders/{id}/cancel` |
| 删除订单 | `DELETE /orders/{id}` |

换成 Druid 连接池并打开监控页 `http://localhost:8005/druid/`：

```bash
mvn -pl 05-mybatis -Pdruid spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=druid"
```

## 测试

```bash
mvn -pl 05-mybatis test
```

57 个用例，连本地 MySQL（`application.yml` 里的数据源），每条用例前清空自己涉及的表，跑之前确保 MySQL 已启动。跑完库里是空的，恢复示例数据：

```bash
mysql -uroot -p123456 -D backend_learning --default-character-set=utf8mb4 < src/main/resources/schema.sql
mysql -uroot -p123456 -D backend_learning --default-character-set=utf8mb4 < src/main/resources/data.sql
```

只跑某一题：

```bash
mvn -pl 05-mybatis test -Dtest=PracticeTest#practice2AgeGreaterThan18AndVip
```

## 练习

随堂练习五题与课后练习四题的参考答案、验证用例、运行命令和预期输出，在 [docs/05-mybatis.md](../docs/05-mybatis.md) 第 8 节。连接池耗尽实验需要手工起服务，步骤在 8.3。

## 依赖

- `mybatis-plus-spring-boot4-starter`（Spring Boot 4 专用 starter）
- `mybatis-plus-jsqlparser`（分页插件 `PaginationInnerInterceptor` 所需）
- `mysql-connector-j`（runtime，版本由 `spring-boot-dependencies` 管理）
- `druid-spring-boot-4-starter`（仅 `druid` profile，默认不引入）
