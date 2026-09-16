# 05-mybatis · 数据访问

> 状态：🚧 骨架已创建，核心案例待开发

打通数据库持久化。

## 学习目标

- MyBatis-Plus 增删改查、分页
- 连接池与事务管理

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| MyBatis-Plus | 通用 CRUD、Wrapper、分页插件 |
| 连接池 | Druid 监控与参数 |
| 事务 | `@Transactional` 传播行为 |
| 代码生成 | 逆向生成 entity / mapper |

## 目录结构

```
05-mybatis
└── src/main
    ├── java/top/mqxu/mybatis
    │   ├── MybatisApplication.java
    │   └── controller/MybatisController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 05-mybatis -am spring-boot:run
```

端口：`8005`

## 待引入依赖

- `mybatis-plus-spring-boot-starter`
- `mysql-connector-j`
- `druid-spring-boot-starter`
