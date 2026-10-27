# 从零搭建 09-security：前后端分离的认证与授权

这份文档按顺序做完，会得到一个能跑的前后端分离鉴权模块：学生交任务、教师批改、管理员管账号，登录用 JWT，权限分到权限点，越权返回真实的 403。

它不是讲解稿。每一步都给完整文件内容，照着建、照着贴，中间不跳步。所有代码都是从仓库里这份可运行的实现里取的，不是示意。

唯一的例外是第一节的框架介绍：先花十分钟把 Spring Security 的过滤器链和几个名词看清楚，后面每一步才知道自己在填哪个坑。

## 开始之前

| 需要 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 父 POM 里 `maven.compiler.source=21` |
| Maven | 3.9 以上 | 命令都带 `-o` 离线参数，首次执行前先联网跑一次 `mvn -pl 09-security -am dependency:go-offline` |
| MySQL | 8.0 以上 | 库名 `backend_learning`，账号密码改 `application.yml` 或设环境变量 `MYSQL_PASSWORD` |
| Node | 20 以上 | Vite 8 要求 |
| 浏览器 | Chrome / Edge 新版 | 无特殊要求 |

后端依赖版本由父 POM 的 Spring Boot 4.1.1 管住，模块里只写三个：MyBatis-Plus 3.5.17、jjwt 0.13.0、MySQL 驱动。前端是 Vue 3.5.44、vue-router 4.6.4、axios 1.21.0、Vite 8.3.5、TypeScript 5.9.3、vue-tsc 3.3.13。

## 先认识 Spring Security

在动手之前花十分钟把框架的骨架看清楚，后面每一步才知道自己在填哪个坑。

### 它到底是什么

一句话：Spring Security 是一条挂在 `DispatcherServlet` 前面的过滤器链，加上一套认证与授权的抽象。

请求进 Tomcat 之后，先经过这条链，再进 Spring MVC。链上的每个过滤器各管一件事：把令牌换成身份、按 URL 判断权限、处理跨域、写 401 和 403 的响应。框架把这些顺序编排好了，我们只需要往几个固定的坑位里填东西。

为什么不用自己写 `HandlerInterceptor`：拦截器只能拦到进 Controller 的请求，静态资源和错误页拦不到；会话管理、密码编码、方法级权限注解、异常出口、CSRF 与 CORS，全都要自己造一遍，顺序还容易错。Security 把这一整套做完了，代价是要先弄懂它的名词。

### 六个名词

| 名词 | 是什么 | 本模块对应的类 |
| --- | --- | --- |
| `SecurityContextHolder` | 存放当前请求身份的地方，默认用 ThreadLocal | 过滤器校验通过后把 `SecurityUser` 放进去 |
| `Authentication` | 一次认证的结果，含身份、凭据、权限 | `UsernamePasswordAuthenticationToken` |
| `AuthenticationManager` | 认证的总入口，把请求交给合适的 Provider | `SecurityConfig` 里构造的 `ProviderManager` |
| `UserDetailsService` | 按用户名取用户的接口，由我们实现 | `AccountUserDetailsService` |
| `UserDetails` | 框架认识的用户形状 | `SecurityUser` |
| `GrantedAuthority` | 一条权限字符串，`ROLE_ADMIN` 或 `task:review` | `Role.authorities()` 摊平出来的 |

另外三个是出口，出错时才用到：

| 名词 | 什么时候被调用 | 本模块对应的类 |
| --- | --- | --- |
| `AuthenticationEntryPoint` | 没有身份就来访问受保护资源 | `RestAuthenticationEntryPoint`，回 401 |
| `AccessDeniedHandler` | 有身份但权限不够 | `RestAccessDeniedHandler`，回 403 |
| `SecurityFilterChain` | 不是出口，是整条链的配置 | `SecurityConfig#securityFilterChain` |

### 一次请求穿过什么

```text
请求
  ↓
FilterChainProxy（Security 的入口，本身就是一个 Servlet Filter）
  ├─ JwtAuthenticationFilter    我们写的：解析 Bearer 令牌，查库，放进上下文
  ├─ AuthorizationFilter        按 URL 规则判断，比如 /admin/** 要 ADMIN
  │    ├─ 没有身份 → AuthenticationEntryPoint → 401
  │    └─ 权限不够 → AccessDeniedHandler → 403
  ↓
DispatcherServlet → Controller
  └─ @PreAuthorize              方法调用前判断
       └─ 不通过 → AccessDeniedException → 全局异常处理器 → 403
```

这张图解释了这个模块里两处容易混的地方：

- 401 和 403 之所以要写两个类，是因为它们发生在两个不同位置，一个是链上的出口，一个是注解抛异常后的兜底。
- `@PreAuthorize` 的拒绝不经过 `AccessDeniedHandler`，它抛的是 `AccessDeniedException`，由 `@RestControllerAdvice` 接住。

### 认证和授权是两件事

| | 认证 Authentication | 授权 Authorization |
| --- | --- | --- |
| 回答的问题 | 你是谁 | 你能做什么 |
| 失败返回 | 401 | 403 |
| 前端该做什么 | 清登录态，跳登录页 | 只提示，不动登录态 |
| 本模块哪里做 | 登录接口 + 令牌过滤器 | URL 规则 + `@PreAuthorize` |
| 出口 | `RestAuthenticationEntryPoint` | `RestAccessDeniedHandler` 与全局异常处理器 |

把这两件事混在一起，最典型的后果是前端把所有失败都当“登录过期”，学生点了一个没权限的按钮就被踢下线。

### 默认配置为什么不能直接用

只把 `spring-boot-starter-security` 加进依赖、什么都不配，Boot 会自动做六件事。这些默认值是为传统服务端渲染页面准备的，前后端分离下每一条都要改。

| 默认行为 | 对 API 的影响 | 本模块怎么改 |
| --- | --- | --- |
| 生成随机密码并打印在启动日志 | 每个请求都要 Basic 认证 | 换成自己的 `UserDetailsService` 与 `AuthenticationManager` |
| 开启表单登录，未认证时 302 跳 `/login` | 前端拿到一段 HTML，不是 JSON | `formLogin` 关掉，配 `AuthenticationEntryPoint` |
| 开启 CSRF 校验 | 所有 POST 返回 403 | 纯 Bearer 令牌、不用 cookie，`csrf` 关掉 |
| 每个请求创建 HttpSession | 与无状态令牌冲突，还会占内存 | `SessionCreationPolicy.STATELESS` |
| 所有请求都要求认证 | 登录接口也被拦，形成死循环 | `permitAll` 放行登录与 `/public/**` |
| 默认 403 错误页 | 前端解析不了 | `AccessDeniedHandler` 输出统一 JSON |

### 这个模块用到的扩展点

搭的时候会发现，要写的类不多，而且每个都对应表里一行。先看清这张表，后面 20 步就是在填它。

| 想改什么 | 扩展什么 | 本模块的类 |
| --- | --- | --- |
| 从哪查用户 | 实现 `UserDetailsService` | `AccountUserDetailsService` |
| 密码怎么存、怎么比 | 提供 `PasswordEncoder` | `BCryptPasswordEncoder` |
| 哪些路径放行、哪些要角色 | 配置 `SecurityFilterChain` | `SecurityConfig` |
| 请求里的令牌怎么变成身份 | 写一个 `OncePerRequestFilter` | `JwtAuthenticationFilter` |
| 未认证时回什么 | 实现 `AuthenticationEntryPoint` | `RestAuthenticationEntryPoint` |
| 权限不足时回什么 | 实现 `AccessDeniedHandler` | `RestAccessDeniedHandler` |
| 方法上限制谁能调 | `@EnableMethodSecurity` 加 `@PreAuthorize` | `TaskController`、`UserAdminController` |
| 登录时校验账号密码 | 用 `AuthenticationManager` | `AuthService` |

框架负责流程，我们只负责两件事：数据从哪来，规则是什么。

## 每一步的套路

每个步骤都是四段：

- **这一步做什么**：目标，以及为什么排在这个位置
- **代码**：完整文件，直接覆盖
- **为什么这么写**：几条关键决策，都是踩过坑之后定下来的
- **验证**：一条命令，加预期结果

中途出现编译错误是正常的。后端的第 01 到 12 步逐个文件建，前端的第 13 到 20 步会因为页面还没建而报“找不到模块”。按顺序做完，最后一次验证全过。

如果本机的 curl 走了系统代理，请求 `127.0.0.1` 会连不上，命令里加 `--noproxy '*'`（macOS 上写成 `curl --noproxy '*' ...`）。

## 顺序总览

| 阶段 | 步骤 | 做完能干什么 |
| --- | --- | --- |
| 准备 | 认识 Spring Security | 知道框架在做什么，后面每一步能对上号 |
| 骨架 | 01 至 02 | 模块能启动，连上 MySQL，表和数据都在 |
| 地基 | 03 至 05 | 有统一响应、有 Mapper、账号能装成 UserDetails |
| 安全链 | 06 至 08 | 能签发令牌、能解析令牌、未登录返回 401 |
| 接口 | 09 至 11 | 登录换令牌、任务五接口、账号三接口 |
| 测试 | 12 | 22 个用例全绿 |
| 前端 | 13 至 20 | 四个页面跑起来，与后端联调通过 |


## 步骤 01 · 建模块，先把依赖版本定死

**这一步做什么**

先建一个空的 Maven 模块，只放依赖和启动类。这一步不写业务，目的是让后面每一步都有东西可以编译。

在仓库根目录的 `pom.xml` 的 `<modules>` 里加一行 `<module>09-security</module>`，父 POM 已经用 Spring Boot 4.1.1 的 `spring-boot-starter-parent` 管住了大部分版本，子模块只写差异。

**文件**：`pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>top.mqxu</groupId>
        <artifactId>backend-learning</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>09-security</artifactId>

    <properties>
        <jjwt.version>0.13.0</jjwt.version>
        <mybatis-plus.version>3.5.17</mybatis-plus.version>
    </properties>

    <dependencies>
        <!-- MyBatis-Plus：通用 CRUD + Lambda 条件构造器（Spring Boot 4 专用 starter） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- MySQL 驱动，版本由 spring-boot-dependencies 管理 -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- 认证与授权：SecurityFilterChain、BCrypt、方法级鉴权 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- 登录入参校验 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- JWT 签发与解析；impl/jackson 只需运行期存在 -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

</project>
```

**为什么这么写**

- Spring Boot 4 的自动配置换了包名，MyBatis-Plus 必须用 `mybatis-plus-spring-boot4-starter`。用 Boot 3 那个 starter 能编过，启动时数据源和 Mapper 不会生效，报错信息还看不出原因。
- jjwt 拆成三件：`jjwt-api` 编译期用，`jjwt-impl` 与 `jjwt-jackson` 只在运行期需要。少任何一个，编译能过，运行到签发令牌时抛 `NoClassDefFoundError`。
- `spring-boot-starter-webmvc`、Lombok、测试依赖由父 POM 统一给，子模块里不重复声明。
- 父 POM 里已经设了 `maven.compiler.source=21`，所以这里不写 `<java.version>`。

**验证**

```bash
cd /Users/moqi/Desktop/backend-learning
mvn -o -pl 09-security compile
```

预期：BUILD SUCCESS。此时只有启动类，没有任何业务代码，编译通过就说明依赖能解析。

## 步骤 02 · 数据源、建表脚本与种子数据

**这一步做什么**

四张表：角色、账号、账号角色关联、任务提交。账号与任务都在 MySQL 里，不用内存集合，因为下一步要讲的分页、事务、联表查询都得有真库才成立。

与仓库里 05-mybatis 共用 `backend_learning` 库，本模块的表统一加 `sec_` 前缀，两个模块的表不会打架。

**文件**：`src/main/resources/application.yml`

```yaml
server:
  port: 8009

spring:
  application:
    name: 09-认证与授权

  # 与 05-mybatis 共用一个库，本模块的表统一 sec_ 前缀
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/backend_learning?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_PASSWORD:123456}
    hikari:
      pool-name: security-demo-pool
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 3000

  # 启动时先建表再灌演示数据。MySQL 不是内嵌库，必须显式写 always
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
      data-locations: classpath:data.sql
      encoding: UTF-8

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true      # 下划线列名转驼峰属性
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 开发期打印 SQL，上线关掉
  global-config:
    banner: false
    db-config:
      id-type: auto                          # 主键自增
      table-underline: true                  # 实体类名驼峰转表名下划线

app:
  jwt:
    # 生产环境用环境变量 JWT_SECRET 覆盖，不要提交真实密钥
    # HS256 要求密钥不短于 256 bit，即 32 个字节
    secret: ${JWT_SECRET:backend-learning-2026-demo-jwt-secret-key-please-change}
    issuer: backend-learning
    expire-minutes: 120

logging:
  level:
    top.mqxu.security: INFO
    org.springframework.security: INFO
    # 想看 MyBatis 的 SQL 就打开这行，输出比 log-impl 更整齐
    # top.mqxu.security.mapper: DEBUG
```

**文件**：`src/main/resources/schema.sql`

```sql
-- 09-security 用的是同一个库 backend_learning，表名统一加 sec_ 前缀，
-- 避免与 05-mybatis 的 t_user 等表冲突，十个模块可以在一个库里共存。
CREATE DATABASE IF NOT EXISTS backend_learning DEFAULT CHARSET utf8mb4;
USE backend_learning;

-- 先删有外键指向的表，再删被指向的表
DROP TABLE IF EXISTS sec_task;
DROP TABLE IF EXISTS sec_user_role;
DROP TABLE IF EXISTS sec_user;
DROP TABLE IF EXISTS sec_role;

-- 角色表：只存角色本身，权限点留在 Role 枚举里，理由见 README 的取舍说明
CREATE TABLE sec_role
(
    id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(20)     NOT NULL COMMENT '角色编码，与 Role 枚举同名：STUDENT / TEACHER / ADMIN',
    name VARCHAR(50)     NOT NULL COMMENT '角色名称，给页面显示',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE = InnoDB COMMENT '角色';

-- 用户表：password 存 BCrypt 密文，定长 60，留 100 以便将来换更长的算法
CREATE TABLE sec_user
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(50)     NOT NULL COMMENT '登录名',
    password   VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密文，不可逆',
    nickname   VARCHAR(50)     NOT NULL COMMENT '姓名，页面上显示',
    enabled    TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用，停用账号登录会被拒',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB COMMENT '账号';

-- 用户角色关联表：一个用户可以有多个角色，主键就是这两列的组合
CREATE TABLE sec_user_role
(
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 id',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色 id',
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id)
) ENGINE = InnoDB COMMENT '用户与角色的关联';

-- 任务表：归属用 owner_id 指向 sec_user，不冗余存用户名
CREATE TABLE sec_task
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    title        VARCHAR(100)    NOT NULL COMMENT '任务标题',
    content      VARCHAR(2000)   NOT NULL COMMENT '完成情况说明',
    owner_id     BIGINT UNSIGNED NOT NULL COMMENT '提交人，指向 sec_user.id',
    status       VARCHAR(20)     NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED 待批改 / REVIEWED 已批改',
    score        TINYINT UNSIGNED         DEFAULT NULL COMMENT '分数 0 到 100，未批改时为空',
    comment      VARCHAR(500)             DEFAULT NULL COMMENT '教师评语',
    submitted_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    reviewed_at  DATETIME                 DEFAULT NULL COMMENT '批改时间',
    PRIMARY KEY (id),
    KEY idx_task_owner (owner_id),
    KEY idx_task_status (status)
) ENGINE = InnoDB COMMENT '实训任务提交记录';
```

**文件**：`src/main/resources/data.sql`

```sql
-- 演示数据。主键写死，测试里引用任务 id 才稳定，讲义里的示例也按这几个 id 走。
-- 密码是 BCrypt 密文，明文见 README 的账号表；换密码要重新算哈希，不要手改这一列。
-- 同一个角色的账号共用一份密文（BCrypt 每次加盐结果不同，但 verify 时都认）。
-- classpath:BOOT-INF 下没有子目录，写成一条 INSERT 或分开写都行，这里按表分四条。
USE backend_learning;

INSERT INTO sec_role (id, code, name)
VALUES (1, 'STUDENT', '学生'),
       (2, 'TEACHER', '教师'),
       (3, 'ADMIN', '管理员');

-- 14 个账号：1 个管理员、2 个教师、10 个学生、1 个停用账号
INSERT INTO sec_user (id, username, password, nickname, enabled)
VALUES (1, 'admin', '$2a$10$9s3TX0V3ynwtfUc2eSRKOeQhW5qZbqbELGu7hYP8TGkArhgW0CDFi', '系统管理员', 1),
       (2, 'teacher01', '$2a$10$dMq3yX/ibVFO2DNsAa6iRusGwIXd6vFq8zi2Kij.TeX7D69shvoEu', '陶老师', 1),
       (3, 'student01', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '朱彦清', 1),
       (4, 'student02', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '李思远', 1),
       (5, 'guest', '$2a$10$D5DZotFstAji9cba0cQTke2W0MWz1wIzDD2n9dYQNNc4DfwRLF88K', '往届旁听生', 0),
       (6, 'teacher02', '$2a$10$dMq3yX/ibVFO2DNsAa6iRusGwIXd6vFq8zi2Kij.TeX7D69shvoEu', '沈老师', 1),
       (7, 'student03', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '王雨桐', 1),
       (8, 'student04', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '陈子豪', 1),
       (9, 'student05', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '周欣怡', 1),
       (10, 'student06', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '吴俊杰', 1),
       (11, 'student07', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '徐嘉宁', 1),
       (12, 'student08', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '孙浩然', 1),
       (13, 'student09', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '郑雅琪', 1),
       (14, 'student10', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '黄泽宇', 1);

INSERT INTO sec_user_role (user_id, role_id)
VALUES (1, 3),
       (2, 2),
       (6, 2),
       (3, 1),
       (4, 1),
       (5, 1),
       (7, 1),
       (8, 1),
       (9, 1),
       (10, 1),
       (11, 1),
       (12, 1),
       (13, 1),
       (14, 1);

-- 28 条提交：11 条待批改、17 条已批改。时间相对当前，每次重启都是新鲜的。
-- id=1 故意留成待批改，课堂上教师登录后直接批改这条，学生那边刷新就能看到分数。
INSERT INTO sec_task (id, title, content, owner_id, status, score, comment, submitted_at, reviewed_at)
VALUES (1, '实训一：用户登录接口',
        '登录接口写完了，密码用 BCrypt 存，登录成功返回 JWT，有效期 2 小时。Postman 里测了正确密码、错误密码、停用账号三种情况，只有第一种能拿到令牌。',
        3, 'SUBMITTED', NULL, NULL, NOW() - INTERVAL 2 HOUR, NULL),
       (2, '实训二：接口鉴权',
        '按角色分了 STUDENT / TEACHER / ADMIN，学生查列表时只返回自己的记录。越权访问别人的 id 时返回 403，不是 200 里包一个错误码。',
        4, 'REVIEWED', 92, '归属判断写对了。令牌过期要返回 401，别返回 200 再提示。', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 20 HOUR),
       (3, '实训一：用户登录接口',
        '接口通了，密码没加盐，直接存了明文，老师说这样不行，我改成 BCrypt 了。返回体里没有密码字段。',
        7, 'REVIEWED', 85, '改得及时。注意登录失败也要返回统一的 30001，不能提示“用户名不存在”，否则能被人枚举账号。', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY),
       (4, '实训二：接口鉴权',
        '用 @PreAuthorize 卡住了批改接口，学生调会 403。但是“只看自己的”这一条用的是前端传的 username，好像不对，还不确定该怎么改。',
        8, 'SUBMITTED', NULL, NULL, NOW() - INTERVAL 5 HOUR, NULL),
       (5, '实训二：接口鉴权',
        '归属从令牌里取，请求体里没有 owner 字段，前端塞别人的用户名也不生效。另外写了两个测试覆盖越权查看和越权批改。',
        9, 'REVIEWED', 96, '两个越权测试思路很清楚。可以再补一个“令牌被篡改”的用例。', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY),
       (6, '实训三：MyBatis-Plus 分页查询',
        '任务列表改成分页查，Page 对象传页码和每页条数，返回 total 和 records。但是现在查出来每条都带上了表里的全部字段，包括密码。',
        10, 'SUBMITTED', NULL, NULL, NOW() - INTERVAL 8 HOUR, NULL),
       (7, '实训一：用户登录接口',
        '按讲义走了一遍，登录、取当前用户、登出都做了。登出是把令牌从前端删掉，后端其实没拦住，这个算不算问题？',
        11, 'REVIEWED', 78, '算。JWT 无状态，删前端只是让浏览器不带它，令牌本身在过期前仍然有效。想真失效要加黑名单，这条算进阶。', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 3 DAY),
       (8, '实训四：全局异常处理',
        '加了 @RestControllerAdvice，业务异常返回 200 加错误码，参数校验失败返回 400，越权返回 403。现在接口出错前端能拿到统一格式了。',
        12, 'SUBMITTED', NULL, NULL, NOW() - INTERVAL 3 HOUR, NULL),
       (9, '实训四：全局异常处理',
        '异常处理器写好了，但是把 Exception 全捕获成了 200，老师说这样监控看不到真实错误，我改成了业务异常 200、其余 500。',
        13, 'REVIEWED', 68, '知道改说明想明白了。还有一处：MethodArgumentNotValidException 要取第一条字段错误，不能把整个异常信息丢给前端。', NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY),
       (10, '实训三：MyBatis-Plus 分页查询',
        '分页用 Page 做了，联表查提交人姓名用 @Select 手写 SQL，返回一个投影对象，不让实体直接出 service。',
        14, 'REVIEWED', 90, '分层意识很好。注意 count 语句别写成 select *，大表上会拖慢。', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 4 DAY),
       (11, '实训三：MyBatis-Plus 分页查询',
        '分页接口做完了，页码从 1 开始，每页 10 条。前端传 0 会报错，我在 Controller 上加了 @Min(1)。',
        3, 'REVIEWED', 88, '边界处理到位。下一步可以试试按状态筛选，参数为空时不能拼进 where。', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 4 DAY),
       (12, '实训四：全局异常处理',
        '统一返回体改成 Result，code 用枚举，前端按 code 判断。异常日志里打印了请求路径，排查方便一些。',
        4, 'REVIEWED', 89, '日志里别打请求体，登录接口会把密码写进日志。', NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 6 DAY),
       (13, '实训二：接口鉴权',
        '角色写在数据库里，用一个关联表存用户和角色。权限点直接拼在代码里，还没想好要不要也建表。',
        7, 'REVIEWED', 74, '角色是运行期数据，建表合理；权限点跟着接口走，留在代码里更好评审。这个取舍讲义里有。', NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY),
       (14, '实训一：用户登录接口',
        '登录用 Spring Security 的 AuthenticationManager，没有自己比对密码，密码编码器用 BCrypt。',
        8, 'REVIEWED', 82, '用框架的认证链路是对的，后面接验证码、第三方登录都能挂上来。', NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 7 DAY),
       (15, '实训一：用户登录接口',
        '接口和测试都写完了，另外补了三个异常分支的用例：密码错、账号停用、令牌过期。',
        9, 'REVIEWED', 95, '用例覆盖得比要求多，可以拿去当范例。', NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 8 DAY),
       (16, '实训一：用户登录接口',
        '登录接口能通，令牌能拿到，但是刷新页面就要重新登录，不知道令牌该存哪。',
        10, 'SUBMITTED', NULL, NULL, NOW() - INTERVAL 45 MINUTE, NULL),
       (17, '实训二：接口鉴权',
        '鉴权做了，但是把权限判断写在了 Controller 里，每个方法开头都 if 一下角色，重复得厉害。',
        11, 'REVIEWED', 61, '重复说明位置不对。挪到方法注解或拦截器里，Controller 只留业务。', NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 7 DAY),
       (18, '实训一：用户登录接口',
        '登录写完了，密码用 MD5 加密的，可以吗？',
        12, 'SUBMITTED', NULL, NULL, NOW() - INTERVAL 12 HOUR, NULL),
       (19, '实训一：用户登录接口',
        '登录接口 + 令牌校验都通了，过滤器里解析令牌后把身份放进 SecurityContext，后面的接口能直接拿到当前用户。',
        13, 'REVIEWED', 87, '过滤器只解析、不拦截，路径放行交给过滤链，这个分工是对的。', NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 6 DAY),
       (20, '实训二：接口鉴权',
        '学生只能看自己的、教师能看全部、管理员能管账号，三条规则都测过了。越权返回 403，未登录返回 401，两种分得清。',
        14, 'REVIEWED', 93, '401 和 403 分清楚是这一节的重点，做得好。', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY),
       (21, '实训五：文件上传与下载',
        '上传接口做了，文件存到本地磁盘，文件名用 UUID 重命名。下载的时候发现谁都能下别人的附件，还没处理。',
        3, 'SUBMITTED', NULL, NULL, NOW() - INTERVAL 30 MINUTE, NULL),
       (22, '实训一：用户登录接口',
        '本人已旁听，接口照着写了一遍。（账号已停用，这条是历史记录）',
        5, 'REVIEWED', 70, '流程走通了，建议补上密码错误时的提示文案。', NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 19 DAY),
       (23, '实训三：MyBatis-Plus 分页查询',
        '分页能查出来，但是 total 一直是 0，单独执行 count 语句又是对的，卡住了。',
        7, 'SUBMITTED', NULL, NULL, NOW() - INTERVAL 1 HOUR, NULL),
       (24, '实训三：MyBatis-Plus 分页查询',
        '分页和条件查询都做了，条件是动态拼的，参数为空时不进 where。',
        9, 'SUBMITTED', NULL, NULL, NOW() - INTERVAL 15 HOUR, NULL),
       (25, '实训二：接口鉴权',
        '把权限点从字符串改成了常量，写错编译就过不了。另外给“只能看自己的”加了一个测试。',
        13, 'SUBMITTED', NULL, NULL, NOW() - INTERVAL 4 HOUR, NULL),
       (26, '实训五：文件上传与下载',
        '上传做了大小限制和类型白名单，超过 2MB 直接拒。下载还没做权限判断。',
        4, 'SUBMITTED', NULL, NULL, NOW() - INTERVAL 6 HOUR, NULL),
       (27, '实训三：MyBatis-Plus 分页查询',
        '分页返回了一个 PageResult，里面有 total、pages、records。前端拿这个直接渲染分页条。',
        8, 'REVIEWED', 89, '返回结构清晰。pages 是算出来的，别存库。', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 4 DAY),
       (28, '实训四：全局异常处理',
        '异常处理器按异常类型分支，业务异常带错误码，其余统一 500 并且不返回堆栈。',
        12, 'REVIEWED', 81, '不返回堆栈是对的，堆栈只进日志。', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 9 DAY);
```

**为什么这么写**

- `spring.sql.init.mode` 必须显式写 `always`。这个开关对内嵌数据库默认是 `embedded`，MySQL 不是内嵌库，不写就一条建表语句都不执行，启动后第一句查询报 `Table 'sec_user' doesn't exist`。
- `schema.sql` 每次启动先 `DROP` 再 `CREATE`。演示项目要的是可重复的初始状态，改表结构不用手工 ALTER；真实项目应换成 Flyway。
- `data.sql` 的主键写死。测试里要引用具体任务 id（比如验证越权看 id=2 那条），自增主键在不同机器上不保证是同一个值。
- 提交时间用 `NOW() - INTERVAL n HOUR` 写，每次重启都相对当前时刻，页面上的“提交于 2 小时前”不会变成几个月前。
- 任务归属存 `owner_id` 而不是用户名：用户名将来可以改，id 不会变。
- 密码列存 BCrypt 密文，长度留到 100，将来换更长的算法不用改表。
- `service 名` 用了中文（`09-认证与授权`），只影响日志前缀，不影响功能。

**验证**

```bash
mvn -o -pl 09-security spring-boot:run
```

预期：日志出现 Started SecurityApplication。再执行 `mysql -uroot -p backend_learning -e "SELECT COUNT(*) FROM sec_user; SELECT COUNT(*) FROM sec_task;"`，得到 14 与 28。

## 步骤 03 · 统一响应体与错误码

**这一步做什么**

前后端分离的第一条契约：所有响应用同一个壳子 `{code, message, data}`。约定 200 是成功，4xx/5xx 与 HTTP 状态码对齐，3xxxx 是业务错误。

异常处理器只兜进入 Controller 之后的异常。过滤链上的认证失败发生在 Controller 之前，`@RestControllerAdvice` 根本接不到，下一节要单独写。

**文件**：`src/main/java/top/mqxu/security/common/ErrorCode.java`

```java
package top.mqxu.security.common;

import lombok.Getter;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 错误码枚举：4xx/5xx 与 HTTP 状态码对齐，3xxxx 为业务错误
 **/
@Getter
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证或令牌已失效"),
    FORBIDDEN(403, "无权访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试"),

    // 认证与账号相关
    BAD_CREDENTIALS(30001, "用户名或密码错误"),
    ACCOUNT_DISABLED(30002, "账号已被禁用"),
    USER_NOT_FOUND(30003, "用户不存在"),
    USERNAME_EXISTS(30004, "用户名已存在"),
    CANNOT_DELETE_SELF(30005, "不能删除当前登录账号"),

    // 实训任务相关
    TASK_NOT_FOUND(30010, "任务不存在"),
    TASK_DUPLICATED(30011, "同一任务已提交，等待批改"),
    TASK_ALREADY_REVIEWED(30012, "该任务已批改，不能重复批改");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

**文件**：`src/main/java/top/mqxu/security/common/Result.java`

```java
package top.mqxu.security.common;

import lombok.Data;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 统一响应体
 **/
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
```

**文件**：`src/main/java/top/mqxu/security/common/BusinessException.java`

```java
package top.mqxu.security.common;

import lombok.Getter;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 业务异常，由全局异常处理器统一转成 Result
 **/
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
```

**文件**：`src/main/java/top/mqxu/security/common/GlobalExceptionHandler.java`

```java
package top.mqxu.security.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 全局异常处理
 * <p>
 * 过滤器链上的认证/授权失败走 RestAuthenticationEntryPoint 与 RestAccessDeniedHandler，
 * 不经过这里；本类只兜底进了 Controller 之后抛出的异常，例如 @PreAuthorize 拒绝。
 **/
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常的状态码分两类：越权给真实的 403，其余沿用本仓库约定返回 200 + code。
     * <p>
     * 前端拦截器靠状态码决定要不要跳登录、要不要提示无权限。
     * 把越权藏在 200 里，前端会把“你不能看”当成成功响应处理。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        HttpStatus status = switch (e.getErrorCode()) {
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(Result.fail(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配：{}", e.getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "参数类型错误");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    /**
     * 方法级鉴权失败。响应体沿用 Result 结构，但 HTTP 状态码必须是 403，
     * 否则前端拦截器会把拒绝当成成功响应处理。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("方法级鉴权拒绝：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}
```

**为什么这么写**

- 业务异常默认返回 `200 + code`，但 `FORBIDDEN` 与 `UNAUTHORIZED` 必须给真实的 403、401。前端拦截器靠 HTTP 状态码决定要不要跳登录，把越权藏在 200 里，前端会把“你不能看”当成功处理。
- `@PreAuthorize` 拒绝抛的是 `AccessDeniedException`，不走 `BusinessException`，所以单独有一个处理方法，同样返回真 403。
- 参数校验失败只取第一条字段错误。把整个异常信息丢给前端，页面上会出现一长串英文类名。
- `HttpMessageNotReadableException` 单独处理：请求体 JSON 写错时如果不拦，会落到 `Exception` 分支变成 500，前端看到的提示和真实原因对不上。
- 兜底的 `Exception` 只回“系统繁忙”，堆栈进日志。回堆栈等于把内部结构告诉调用方。

**验证**

```bash
mvn -o -pl 09-security compile
```

预期：BUILD SUCCESS。这一步还没有接口，验不了响应体，下一节起接口会用到。

## 步骤 04 · 表映射与 Mapper

**这一步做什么**

实体只映射表结构，负责增删改查；联表查询另外用投影对象接，不让实体顺着接口漏出去。

启动类上已经加了 `@MapperScan(basePackages = "top.mqxu.security.mapper")`，接口不需要 `@Mapper` 注解。

**文件**：`src/main/java/top/mqxu/security/entity/User.java`

```java
package top.mqxu.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号实体，对应 sec_user 表
 * <p>
 * 实体只在 service 与 mapper 之间传递，不作为接口出参。
 * 出参统一用 UserProfile，避免 password 顺着实体漏出去。
 **/
@Data
@TableName("sec_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /**
     * BCrypt 密文。任何接口都不返回这一列
     */
    private String password;

    private String nickname;

    /**
     * 1 启用 / 0 停用。停用账号密码对也登不进来
     */
    private Boolean enabled;

    private LocalDateTime createdAt;
}
```

**文件**：`src/main/java/top/mqxu/security/entity/Task.java`

```java
package top.mqxu.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.mqxu.security.model.TaskStatus;

import java.time.LocalDateTime;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 实训任务实体，对应 sec_task 表
 * <p>
 * 归属存 owner_id 而不是用户名：用户名将来可以改，id 不会变。
 * 页面要显示提交人时再 join sec_user 取，见 TaskMapper 的联表查询。
 **/
@Data
@TableName("sec_task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /**
     * 提交人 id，指向 sec_user.id
     */
    private Long ownerId;

    /**
     * 库里的列是 VARCHAR，MyBatis 默认按枚举名 SUBMITTED / REVIEWED 读写
     */
    private TaskStatus status;

    private Integer score;

    private String comment;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    /**
     * 批改后状态、分数、评语、时间一起变，避免出现“已批改但没分数”的中间态
     */
    public void markReviewed(int score, String comment, LocalDateTime reviewedAt) {
        this.status = TaskStatus.REVIEWED;
        this.score = score;
        this.comment = comment;
        this.reviewedAt = reviewedAt;
    }
}
```

**文件**：`src/main/java/top/mqxu/security/model/TaskStatus.java`

```java
package top.mqxu.security.model;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 任务状态。文本给前端直接展示，避免前端再写一遍中文映射
 **/
public enum TaskStatus {

    SUBMITTED("待批改"),
    REVIEWED("已批改");

    private final String text;

    TaskStatus(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
```

**文件**：`src/main/java/top/mqxu/security/model/TaskRow.java`

```java
package top.mqxu.security.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 任务与提交人的联表投影
 * <p>
 * 只用来接 Mapper 的查询结果，不外传。
 * 对外统一转成 TaskView，页面需要的字段和数据库列不必一一对应。
 **/
@Data
public class TaskRow {

    private Long id;

    private String title;

    private String content;

    private Long ownerId;

    /**
     * 提交人登录名，来自 sec_user.username
     */
    private String owner;

    /**
     * 提交人姓名，来自 sec_user.nickname
     */
    private String ownerNickname;

    /**
     * SUBMITTED / REVIEWED
     */
    private String status;

    private Integer score;

    private String comment;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;
}
```

**文件**：`src/main/java/top/mqxu/security/model/UserRoleEntry.java`

```java
package top.mqxu.security.model;

import lombok.Data;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 用户与角色关联的查询结果
 * <p>
 * 账号列表一次查出所有人的角色，避免每个用户查一次库（N+1）。
 **/
@Data
public class UserRoleEntry {

    private Long userId;

    private String roleCode;
}
```

**文件**：`src/main/java/top/mqxu/security/mapper/UserMapper.java`

```java
package top.mqxu.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.mqxu.security.entity.User;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号 Mapper。单表增删改查全部由 BaseMapper 提供，不用写 SQL
 **/
public interface UserMapper extends BaseMapper<User> {
}
```

**文件**：`src/main/java/top/mqxu/security/mapper/TaskMapper.java`

```java
package top.mqxu.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.mqxu.security.entity.Task;
import top.mqxu.security.model.TaskRow;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 任务 Mapper
 * <p>
 * 单表操作继承 BaseMapper，联表查询用注解写 SQL。
 * MyBatis-Plus 的条件构造器只覆盖单表，涉及 join 时老老实实写 SQL 更清楚。
 **/
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 联表要查的列。下划线列名靠 map-underscore-to-camel-case 转成驼峰属性
     */
    String SELECT_COLUMNS = """
            t.id, t.title, t.content, t.owner_id, t.status, t.score, t.comment,
            t.submitted_at, t.reviewed_at,
            u.username AS owner, u.nickname AS owner_nickname
            """;

    String FROM_JOIN = " FROM sec_task t JOIN sec_user u ON u.id = t.owner_id ";

    /**
     * 全部提交，教师批改用
     */
    @Select("SELECT " + SELECT_COLUMNS + FROM_JOIN + "ORDER BY t.submitted_at DESC")
    List<TaskRow> selectAllRows();

    /**
     * 按提交人过滤。参数是登录时令牌里的用户 id，不是前端传的用户名
     */
    @Select("SELECT " + SELECT_COLUMNS + FROM_JOIN + "WHERE t.owner_id = #{ownerId} ORDER BY t.submitted_at DESC")
    List<TaskRow> selectRowsByOwnerId(@Param("ownerId") Long ownerId);

    @Select("SELECT " + SELECT_COLUMNS + FROM_JOIN + "WHERE t.id = #{id}")
    TaskRow selectRowById(@Param("id") Long id);
}
```

**文件**：`src/main/java/top/mqxu/security/mapper/RoleMapper.java`

```java
package top.mqxu.security.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 角色 Mapper。角色是配置数据，没有增删需求，不继承 BaseMapper
 **/
public interface RoleMapper {

    /**
     * 角色编码转 id，新增用户建关联时用
     */
    @Select("SELECT id FROM sec_role WHERE code = #{code}")
    Long selectIdByCode(@Param("code") String code);

    @Select("SELECT code FROM sec_role ORDER BY id")
    List<String> selectAllCodes();
}
```

**文件**：`src/main/java/top/mqxu/security/mapper/UserRoleMapper.java`

```java
package top.mqxu.security.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.mqxu.security.model.UserRoleEntry;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 用户角色关联 Mapper
 * <p>
 * 关联表没有自己的主键（主键是 user_id + role_id），写不进 BaseMapper 那一套，
 * 直接用注解 SQL 更直白。
 **/
public interface UserRoleMapper {

    /**
     * 取一个用户的角色编码，登录时用
     */
    @Select("""
            SELECT r.code
            FROM sec_user_role ur
            JOIN sec_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 一次取回全部关联，账号列表用，避免逐个用户查一遍
     */
    @Select("""
            SELECT ur.user_id AS user_id, r.code AS role_code
            FROM sec_user_role ur
            JOIN sec_role r ON r.id = ur.role_id
            ORDER BY ur.user_id, r.id
            """)
    List<UserRoleEntry> selectAllEntries();

    @Insert("INSERT INTO sec_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sec_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
```

**为什么这么写**

- 单表用 `BaseMapper`：`selectById`、`insert`、`updateById`、`selectCount` 都是现成的，条件用 `LambdaQueryWrapper` 拼，字段名写错编译就报。
- 联表老老实实写 `@Select`。条件构造器只覆盖单表，硬拼子查询比 SQL 更难读，也没有类型检查。
- `TaskRow` 是联表投影，只用来接查询结果。对外统一转成 `TaskView`，页面要的字段和数据库列不必一一对应。
- 关联表主键是 `(user_id, role_id)` 组合，没有单列主键，用不了 `BaseMapper` 那一套，直接写注解 SQL。
- `Task.status` 是枚举，库里存 `VARCHAR`，MyBatis 默认按枚举名 `SUBMITTED` / `REVIEWED` 读写，不用额外配 TypeHandler。
- `Task.markReviewed` 把状态、分数、评语、时间放在一个方法里改，避免出现“已批改但没分数”的中间态。

**验证**

```bash
mvn -o -pl 09-security spring-boot:run
```

预期：启动成功，日志里能看到 Mapper 已注册。此时还没有自己的安全配置，Spring Security 会启用默认过滤链，所有请求都要求登录，属正常现象，第 08 步会把默认配置换掉。

## 步骤 05 · 角色、账号与 UserDetails 适配

**这一步做什么**

Spring Security 不认识我们的表结构，它只认 `UserDetails`。这一节做的事就是：把“账号 + 角色”装成一个 `UserDetails`。

`Account` 是中间的领域对象，把两张表的内容合成一个概念；`SecurityUser` 是适配层，把角色摊平成权限字符串。

**文件**：`src/main/java/top/mqxu/security/model/Role.java`

```java
package top.mqxu.security.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 三个角色与各自的权限点，权限点命名约定“资源:动作”
 * <p>
 * 角色给人看，权限点给接口用。接口只认权限点，将来多一类人（比如教务）时，
 * 加个角色、配好权限点就行，接口代码一行都不用改。
 **/
public enum Role {

    STUDENT(Set.of("task:submit", "task:read")),
    TEACHER(Set.of("task:review", "task:read")),
    ADMIN(Set.of("user:read", "user:write", "task:read", "task:review"));

    private final Set<String> permissions;

    Role(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<String> permissions() {
        return permissions;
    }

    /**
     * hasRole("ADMIN") 实际比对的是 ROLE_ADMIN，前缀由 Security 补，别手动写进配置
     */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * 把角色摊平成权限字符串：角色名 + 该角色的全部权限点
     */
    public static List<String> authorities(Collection<Role> roles) {
        return roles.stream()
                .flatMap(role -> Stream.concat(Stream.of(role.authority()), role.permissions().stream()))
                .distinct()
                .toList();
    }
}
```

**文件**：`src/main/java/top/mqxu/security/model/Account.java`

```java
package top.mqxu.security.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号的领域对象：安全层只认它，不认数据库实体
 * <p>
 * 为什么要多这一层：UserDetailsService 需要“用户 + 角色”，
 * 而实体 User 只有一张表的内容，角色在另一张表。
 * 用 Account 把两者合起来，SecurityUser 就不用知道表结构。
 **/
public record Account(

        Long id,
        String username,
        String password,
        String nickname,
        boolean enabled,
        Set<Role> roles
) {

    /**
     * 角色名，给登录响应与前端菜单用
     */
    public Set<String> roleNames() {
        Set<String> names = new LinkedHashSet<>();
        roles.forEach(role -> names.add(role.name()));
        return names;
    }
}
```

**文件**：`src/main/java/top/mqxu/security/security/SecurityUser.java`

```java
package top.mqxu.security.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import top.mqxu.security.model.Account;
import top.mqxu.security.model.Role;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description UserDetails 实现，把账号的角色摊平成权限集合
 * <p>
 * Spring Security 不认识我们的 Account，只认 UserDetails，
 * 这个类就是两者之间的适配层。换存储时它不用改。
 **/
public class SecurityUser implements UserDetails {

    private final Account account;

    private final List<GrantedAuthority> authorities;

    public SecurityUser(Account account) {
        this.account = account;
        this.authorities = Role.authorities(account.roles()).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public Account account() {
        return account;
    }

    /**
     * 用户 id。做数据归属判断时用它，别拿用户名当外键
     */
    public Long id() {
        return account.id();
    }

    /**
     * 只取角色名，供登录响应与页面菜单使用
     */
    public Set<String> roleNames() {
        return account.roleNames();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return account.password();
    }

    @Override
    public String getUsername() {
        return account.username();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return account.enabled();
    }
}
```

**文件**：`src/main/java/top/mqxu/security/service/UserService.java`

```java
package top.mqxu.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.mqxu.security.common.BusinessException;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.dto.CreateUserRequest;
import top.mqxu.security.entity.User;
import top.mqxu.security.mapper.RoleMapper;
import top.mqxu.security.mapper.UserMapper;
import top.mqxu.security.mapper.UserRoleMapper;
import top.mqxu.security.model.Account;
import top.mqxu.security.model.Role;
import top.mqxu.security.model.UserRoleEntry;
import top.mqxu.security.vo.UserProfile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号的读取与维护：登录要读、管理接口要写
 * <p>
 * 认证与账号管理共用同一个读方法，避免两处各写一份“按用户名查人 + 查角色”。
 **/
@Slf4j
@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, UserRoleMapper userRoleMapper,
                       RoleMapper roleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 按用户名取账号与角色，登录与每个请求的令牌校验都走这里
     */
    public Optional<Account> findByUsername(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return Optional.empty();
        }
        Set<Role> roles = userRoleMapper.selectRoleCodesByUserId(user.getId()).stream()
                .map(Role::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Optional.of(new Account(user.getId(), user.getUsername(), user.getPassword(),
                user.getNickname(), Boolean.TRUE.equals(user.getEnabled()), roles));
    }

    /**
     * 账号列表。两次查询：一次取人，一次取全部角色关联，在内存里分组
     */
    public List<UserProfile> listProfiles() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByAsc(User::getUsername));

        Map<Long, List<String>> rolesByUser = userRoleMapper.selectAllEntries().stream()
                .collect(Collectors.groupingBy(UserRoleEntry::getUserId,
                        Collectors.mapping(UserRoleEntry::getRoleCode, Collectors.toList())));

        return users.stream()
                .map(user -> new UserProfile(user.getUsername(), user.getNickname(),
                        new LinkedHashSet<>(rolesByUser.getOrDefault(user.getId(), List.of())),
                        Role.authorities(parseRoles(rolesByUser.get(user.getId()))),
                        Boolean.TRUE.equals(user.getEnabled())))
                .toList();
    }

    /**
     * 新增账号。写两张表，任一步失败整体回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public UserProfile create(CreateUserRequest request) {
        if (findByUsername(request.username()).isPresent()) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(request.username());
        // 明文只在这里出现一次，落库前就换成密文
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setEnabled(true);
        userMapper.insert(user);

        for (Role role : request.roles()) {
            Long roleId = roleMapper.selectIdByCode(role.name());
            if (roleId == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
            userRoleMapper.insert(user.getId(), roleId);
        }

        log.info("新增账号 {}，角色 {}", user.getUsername(), request.roles());
        return findByUsername(user.getUsername()).map(UserProfile::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    /**
     * @param operator 当前登录用户名，禁止删自己，避免把最后一个管理员删掉后无从登录
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String username, String operator) {
        if (username.equals(operator)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_SELF);
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 先删关联再删人，不然留下指向不存在用户的脏关联
        userRoleMapper.deleteByUserId(user.getId());
        userMapper.deleteById(user.getId());
        log.info("账号 {} 被 {} 删除", username, operator);
    }

    private Set<Role> parseRoles(List<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        return codes.stream().map(Role::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
```

**文件**：`src/main/java/top/mqxu/security/service/AccountUserDetailsService.java`

```java
package top.mqxu.security.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import top.mqxu.security.security.SecurityUser;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 把账号装成 Spring Security 认识的 UserDetails
 * <p>
 * 换存储只改 UserService，这个类不动。
 **/
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserService userService;

    public AccountUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userService.findByUsername(username)
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + username));
    }
}
```

**为什么这么写**

- 角色和权限点分两层：`ROLE_STUDENT` 给 `hasRole` 用，`task:submit` 给 `hasAuthority` 用。接口只认权限点，将来加一类人（比如教务），加角色、配权限点就行，接口代码一行不改。
- `hasRole("ADMIN")` 实际比对的是 `ROLE_ADMIN`，前缀由 Security 补，配置里别手写。
- `Account` 这层不能省：`UserDetailsService` 需要“用户 + 角色”，实体 `User` 只有一张表的内容，角色在另一张表。合成 `Account` 之后，`SecurityUser` 不用知道表结构，换存储也不用改它。
- `UserService.findByUsername` 被两处复用：登录校验，以及每个请求的令牌校验。写两份必然有一天改一份漏一份。
- 明文密码只在 `create` 方法里出现一次，落库前就 `encode`。这一行是整个模块唯一能接触到明文的地方。
- 新增账号写两张表，加 `@Transactional`。中途失败留下“有账号没角色”的记录，登录时权限是空的，很难查。
- 删除先删关联再删人，否则留下指向不存在用户的脏关联。禁止删自己，避免把最后一个管理员删掉后再也登不进来。
- 账号列表用两次查询：一次取人，一次取全部角色关联，在内存里分组。逐个用户查角色是典型的 N+1。

**验证**

```bash
mvn -o -pl 09-security compile
```

预期：BUILD SUCCESS。此时 `UserDetailsService` 已经能被 Spring 找到，但还没有过滤链，下一节接上。

## 步骤 06 · JWT 的签发与解析

**这一步做什么**

令牌里只放身份（`sub` 是用户名）和用于展示的角色名，不放权限点。权限每次请求回库查，改角色能立刻生效。

密钥走配置，`application.yml` 里用 `${JWT_SECRET:...}` 给一个默认可用的值，生产环境用环境变量覆盖。

**文件**：`src/main/java/top/mqxu/security/config/JwtProperties.java`

```java
package top.mqxu.security.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description JWT 配置项。密钥走配置与环境变量，不写在代码里
 * <p>
 * HS256 的签名密钥最短 256 bit，即 32 个字节，这里用 @Size 在校验阶段就拦住，
 * 而不是等到签发第一个令牌时抛 WeakKeyException。
 **/
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank
        @Size(min = 32, message = "JWT 密钥至少 32 个字符（HS256 要求 256 bit）")
        String secret,

        @NotBlank
        String issuer,

        @Min(value = 1, message = "JWT 有效期至少 1 分钟")
        long expireMinutes
) {
}
```

**文件**：`src/main/java/top/mqxu/security/security/JwtTokenService.java`

```java
package top.mqxu.security.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import top.mqxu.security.config.JwtProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description JWT 签发与解析
 * <p>
 * 令牌里只放身份（sub）与用于展示的角色名。权限一律在过滤器中按用户名回查，
 * 这样改角色能立刻生效，代价是每次请求多一次用户查询。
 **/
@Service
public class JwtTokenService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issue(UserDetails user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.expireMinutes(), ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("roles", rolesOf(user))
                // 固定 HS256。signWith(key) 单参数版本会按密钥长度自动升级算法：
                // 密钥超过 48 字节时签出来的是 HS384，令牌长度与文档示例对不上
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * 校验签名、签发方与过期时间，返回令牌所属用户名
     *
     * @throws io.jsonwebtoken.JwtException 签名不对、格式损坏或已过期
     */
    public String parseUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    private List<String> rolesOf(UserDetails user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
    }

    /**
     * @param token     待下发给客户端的令牌
     * @param expiresAt 过期时间，随响应一起返回，前端可据此提前刷新
     */
    public record IssuedToken(String token, Instant expiresAt) {
    }
}
```

**为什么这么写**

- `JwtProperties` 用 record 绑定，配 `@Validated` 与 `@Size(min = 32)`。HS256 要求密钥不短于 256 bit，启动时就拦住，比签发第一个令牌时抛 `WeakKeyException` 好定位。
- `signWith(key, Jwts.SIG.HS256)` 必须指定算法。单参数版本 `signWith(key)` 会按密钥长度自动升级：密钥超过 48 字节时签出来的是 HS384，令牌长度和文档示例对不上，排查时容易怀疑到自己写的过滤器上。
- 解析时 `verifyWith` 加 `requireIssuer`，签名、签发方、过期时间一起校验。只验签名的话，别的系统用同一个密钥签的令牌也能进。
- 令牌里塞权限点会带来一个错觉：改完角色以为生效了，其实要等令牌过期。这个模块选择牺牲一次查库换即时生效，代价写在 README 的已知边界里。

**验证**

```bash
mvn -o -pl 09-security compile
```

预期：BUILD SUCCESS。签发结果要到第 09 步登录接口才能看到。

## 步骤 07 · 令牌过滤器与 401、403 的输出

**这一步做什么**

过滤器只做一件事：有 `Authorization: Bearer` 头就解析，解析成功把认证信息放进 `SecurityContext`，然后放行。

401 与 403 的响应不能交给 `@RestControllerAdvice`，因为这两类失败发生在进入 Controller 之前，只能在这两个 Handler 里手写 JSON。

**文件**：`src/main/java/top/mqxu/security/security/RestErrorWriter.java`

```java
package top.mqxu.security.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import top.mqxu.security.common.Result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 在过滤器层写 JSON 响应。认证与授权失败发生在进 Controller 之前，
 * 全局异常处理器接不到，只能在这里手动拼响应体
 **/
@Component
public class RestErrorWriter {

    private final ObjectMapper objectMapper;

    public RestErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, Result<Void> body) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
```

**文件**：`src/main/java/top/mqxu/security/security/RestAuthenticationEntryPoint.java`

```java
package top.mqxu.security.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.common.Result;

import java.io.IOException;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 未认证访问受保护资源时返回 401。默认行为是重定向到登录页，纯 API 场景要改掉
 **/
@Slf4j
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final RestErrorWriter errorWriter;

    public RestAuthenticationEntryPoint(RestErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("未认证访问 {}：{}", request.getRequestURI(), authException.getMessage());
        errorWriter.write(response, HttpStatus.UNAUTHORIZED, Result.fail(ErrorCode.UNAUTHORIZED));
    }
}
```

**文件**：`src/main/java/top/mqxu/security/security/RestAccessDeniedHandler.java`

```java
package top.mqxu.security.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.common.Result;

import java.io.IOException;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 已认证但角色/权限不足时返回 403
 **/
@Slf4j
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final RestErrorWriter errorWriter;

    public RestAccessDeniedHandler(RestErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("权限不足访问 {}：{}", request.getRequestURI(), accessDeniedException.getMessage());
        errorWriter.write(response, HttpStatus.FORBIDDEN, Result.fail(ErrorCode.FORBIDDEN));
    }
}
```

**文件**：`src/main/java/top/mqxu/security/security/JwtAuthenticationFilter.java`

```java
package top.mqxu.security.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 从 Authorization: Bearer 头解析令牌，校验通过后把认证信息放进 SecurityContext
 * <p>
 * 这个类不是 Spring Bean，由 SecurityConfig 在装配过滤链时 new 出来。
 * 若加上 @Component，Spring Boot 会把它同时注册成 Servlet 过滤器，一次请求会跑两遍。
 **/
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService tokenService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenService tokenService, UserDetailsService userDetailsService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, token);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 令牌无效时不在这里写响应，只清空上下文放行。
     * 后续的授权判断会拒绝匿名请求，由 RestAuthenticationEntryPoint 统一返回 401，
     * 避免同一个错误出现两种响应格式。
     */
    private void authenticate(HttpServletRequest request, String token) {
        try {
            String username = tokenService.parseUsername(token);
            UserDetails user = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | UsernameNotFoundException e) {
            SecurityContextHolder.clearContext();
            log.debug("令牌校验未通过：{}", e.getMessage());
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
```

**为什么这么写**

- 过滤器不加 `@Component`，由 `SecurityConfig` 在装配过滤链时 `new` 出来。加了 `@Component`，Spring Boot 会把它同时注册成 Servlet 过滤器，一次请求跑两遍，日志里能看到重复的解析记录。
- 令牌无效时**不在这里写响应**，只清空上下文放行。让后面的授权判断去拒绝匿名请求，401 由 `RestAuthenticationEntryPoint` 统一输出。两个地方都写响应，就会出现两种格式。
- Spring Security 默认未认证会重定向到登录页，返回 302。纯 API 场景必须换成 `AuthenticationEntryPoint` 输出 401 JSON。
- `RestErrorWriter` 单独抽出来，是因为两个 Handler 要写一模一样的响应头与 JSON 格式，复制两份迟早不一致。
- `tools.jackson.databind.ObjectMapper` 是 Jackson 3 的包名。Spring Boot 4 默认用 Jackson 3，写成 `com.fasterxml.jackson` 也能编过（jjwt 会带 Jackson 2 进来），但注入的是另一套 mapper，配置不共享。

**验证**

```bash
mvn -o -pl 09-security compile
```

预期：BUILD SUCCESS。401 与 403 的实测放在第 08、10 步。

## 步骤 08 · 把过滤链装起来

**这一步做什么**

这是整个模块的中枢：关掉不需要的默认行为，定 URL 规则，挂上自定义过滤器，指定两个异常处理器。

Security 7 只认组件式配置，`WebSecurityConfigurerAdapter` 早就移除了，网上很多老教程还在用它。

**文件**：`src/main/java/top/mqxu/security/config/SecurityConfig.java`

```java
package top.mqxu.security.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import top.mqxu.security.security.JwtAuthenticationFilter;
import top.mqxu.security.security.JwtTokenService;
import top.mqxu.security.security.RestAccessDeniedHandler;
import top.mqxu.security.security.RestAuthenticationEntryPoint;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 安全配置：过滤链、认证管理器、密码编码器、跨域
 * <p>
 * Security 7 只认组件式配置，WebSecurityConfigurerAdapter 早已移除。
 **/
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * BCrypt 自带随机盐，同一密码两次加密结果不同，因此只能 matches 不能字符串比较
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 登录接口用这个 AuthenticationManager 校验账号密码，
     * 密码比对、账号禁用检查都由 DaoAuthenticationProvider 完成
     * <p>
     * Provider 在这里内部构造，不再单独暴露成 Bean：
     * 容器里出现 AuthenticationProvider Bean 时，Security 会认为认证管理器已有主，
     * 启动日志里会多一条 UserDetailsService 不被用于自动配置的警告。
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                      PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * 前后端分离的跨域配置。前端跑在 5173，后端在 8009，端口不同就算跨域。
     * <p>
     * 令牌走请求头而不是 cookie，所以不需要 allowCredentials。
     * 如果开发时用 Vite 的 proxy 转发，这个配置不是必需的，但上线分离部署时必须有。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenService jwtTokenService,
                                                   UserDetailsService userDetailsService,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenService, userDetailsService);
        http
                // 纯 Bearer 令牌、无 cookie 会话，可以关掉 CSRF；一旦改回 session 认证必须重新打开
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 规则按从具体到宽泛排列，anyRequest 必须放最后
                .authorizeHttpRequests(auth -> auth
                        // 登录接口必须放行，否则连令牌都拿不到
                        .requestMatchers("/auth/login", "/public/**", "/error").permitAll()
                        // 管理接口整组限管理员，第二道防线在 Controller 的注解上
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 任务接口只要求登录，具体谁能做什么由方法上的权限点决定
                        .requestMatchers("/api/tasks/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

**为什么这么写**

- `csrf` 关掉的前提是纯 Bearer 令牌、不用 cookie 会话。一旦改回 session 认证必须重新打开。
- `formLogin`、`httpBasic`、`logout` 全关。不关 `formLogin`，未认证请求会 302 到 `/login`，前端拿到的是一段 HTML。
- `SessionCreationPolicy.STATELESS`：不创建 HttpSession，每个请求都靠令牌自证身份。
- 规则从具体到宽泛排列，`anyRequest` 必须放最后。把 `anyRequest().authenticated()` 写在 `permitAll` 前面，登录接口也会被拦，表现为“连登录都 401”。
- `/admin/**` 在 URL 层限管理员，Controller 上还有一层注解。两层都留是故意的：哪天有人放宽了 URL 规则，注解仍然拦得住。
- `DaoAuthenticationProvider` 不暴露成 Bean，在 `authenticationManager` 里内部构造。容器里一旦出现 `AuthenticationProvider` Bean，Security 会认为认证管理器已有主，启动日志多一条“UserDetailsService 不参与自动配置”的警告。
- CORS 配了 `localhost` 与 `127.0.0.1` 任意端口。开发时用 Vite 代理其实用不到它，但直接访问 8009 或将来分离部署时必须有。
- `/error` 要放行。不放行时，任何 404 都会被再套一层 401，排查起来莫名其妙。

**验证**

```bash
mvn -o -pl 09-security spring-boot:run
curl -s -w '\n[%{http_code}]\n' http://127.0.0.1:8009/public/ping
curl -s -w '\n[%{http_code}]\n' http://127.0.0.1:8009/auth/me
```

预期：第一条返回 200 与 `{"code":200,...,"status":"ok"}`；第二条返回 401 与 `{"code":401,"message":"未认证或令牌已失效"}`。看到 302 说明 `formLogin` 没关。

## 步骤 09 · 登录接口：换令牌

**这一步做什么**

登录不自己比对密码，交给 `AuthenticationManager`。它内部会用 `DaoAuthenticationProvider` 调 `UserDetailsService` 取账号、用 `BCryptPasswordEncoder` 比对密文、检查账号是否停用。

校验通过后签发令牌，把令牌、过期时间、用户名、昵称、角色一起返回。

**文件**：`src/main/java/top/mqxu/security/dto/LoginRequest.java`

```java
package top.mqxu.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 登录请求
 **/
public record LoginRequest(

        @NotBlank(message = "用户名不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
```

**文件**：`src/main/java/top/mqxu/security/vo/LoginResponse.java`

```java
package top.mqxu.security.vo;

import java.time.Instant;
import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 登录响应。只回令牌与身份信息，不回密码
 * <p>
 * 昵称一起带回去，前端登录后立刻能显示“陶老师”这类称呼，省掉一次 /auth/me 请求。
 **/
public record LoginResponse(

        String token,
        String tokenType,
        Instant expiresAt,
        String username,
        String nickname,
        Set<String> roles
) {
}
```

**文件**：`src/main/java/top/mqxu/security/vo/UserProfile.java`

```java
package top.mqxu.security.vo;

import top.mqxu.security.model.Account;
import top.mqxu.security.model.Role;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号的对外视图：只带白名单字段，密码压根不进来
 **/
public record UserProfile(

        String username,
        String nickname,
        Set<String> roles,
        List<String> authorities,
        boolean enabled
) {

    public static UserProfile of(Account account) {
        Set<String> roles = new LinkedHashSet<>(account.roleNames());
        return new UserProfile(account.username(), account.nickname(),
                roles, Role.authorities(account.roles()), account.enabled());
    }
}
```

**文件**：`src/main/java/top/mqxu/security/service/AuthService.java`

```java
package top.mqxu.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import top.mqxu.security.common.BusinessException;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.dto.LoginRequest;
import top.mqxu.security.security.JwtTokenService;
import top.mqxu.security.security.SecurityUser;
import top.mqxu.security.vo.LoginResponse;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 登录：交给 AuthenticationManager 校验，校验通过后签发令牌
 **/
@Slf4j
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (DisabledException e) {
            log.warn("账号已禁用：{}", request.username());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        } catch (BadCredentialsException e) {
            // 用户名不存在与密码错误给同一个提示，避免被用来枚举账号
            log.warn("登录失败：{}", request.username());
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        } catch (AuthenticationException e) {
            log.warn("登录异常：{}，原因：{}", request.username(), e.getMessage());
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }

        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        JwtTokenService.IssuedToken issued = jwtTokenService.issue(user);
        log.info("用户 {} 登录成功，角色 {}", user.getUsername(), user.roleNames());
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(),
                user.getUsername(), user.account().nickname(), user.roleNames());
    }
}
```

**文件**：`src/main/java/top/mqxu/security/controller/AuthController.java`

```java
package top.mqxu.security.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;
import top.mqxu.security.dto.LoginRequest;
import top.mqxu.security.security.SecurityUser;
import top.mqxu.security.service.AuthService;
import top.mqxu.security.vo.LoginResponse;
import top.mqxu.security.vo.UserProfile;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 认证接口
 **/
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 唯一放行的接口，其余都要带令牌
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 用令牌换当前用户信息，前端刷新页面时用它恢复登录态
     */
    @GetMapping("/me")
    public Result<UserProfile> me(@AuthenticationPrincipal SecurityUser user) {
        return Result.ok(UserProfile.of(user.account()));
    }
}
```

**文件**：`src/main/java/top/mqxu/security/controller/PublicController.java`

```java
package top.mqxu.security.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;

import java.util.Map;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 开放接口，对应 SecurityConfig 里 permitAll 的 /public/**
 **/
@RestController
@RequestMapping("/public")
public class PublicController {

    @GetMapping("/ping")
    public Result<Map<String, String>> ping() {
        return Result.ok(Map.of("status", "ok", "message", "无需认证即可访问"));
    }
}
```

**为什么这么写**

- 用框架的认证链路，而不是 `passwordEncoder.matches` 手写一遍。后面接验证码、第三方登录、登录失败计数，都能挂在这条链上。
- `DisabledException` 单独映射成 30002，`BadCredentialsException` 映射成 30001。用户名不存在也回 30001，不能提示“用户不存在”，否则接口能被人拿来枚举账号。
- 登录响应带昵称，前端登录后立刻能显示“陶老师”，省掉一次 `/auth/me` 请求。
- `/auth/me` 用 `@AuthenticationPrincipal` 直接拿当前用户，不解析请求头。身份是过滤器放进上下文的，Controller 里再解析一遍就重复了。
- 响应对象是 `LoginResponse` 而不是实体，密码字段压根不进来。
- `/public/ping` 是验证放行规则用的最小接口，没有业务含义，排错时很好用。

**验证**

```bash
curl -s -X POST http://127.0.0.1:8009/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"student01","password":"Student#2026"}'
```

预期：返回 `code:200`，`data` 里有 `token`、`tokenType:"Bearer"`、`expiresAt`、`nickname:"朱彦清"`、`roles:["STUDENT"]`。换成 `guest` 返回 30002，换成错密码返回 30001。

## 步骤 10 · 任务业务：提交、我的、全部、批改

**这一步做什么**

这一节是整个模块的教学重点：数据归属。学生只能看自己的、教师能批改、越权要拿到真实的 403。

五个接口：提交、我的、全部、详情、批改。每条路径的注解上都写清了谁能调。

**文件**：`src/main/java/top/mqxu/security/dto/SubmitTaskRequest.java`

```java
package top.mqxu.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 提交实训任务的请求
 * <p>
 * 注意这里没有 owner 字段。提交人由服务端从令牌里取，
 * 前端就算在请求体里多写一个 owner 也不会被采纳。
 **/
public record SubmitTaskRequest(

        @NotBlank(message = "任务标题不能为空")
        @Size(max = 100, message = "任务标题不超过 100 个字")
        String title,

        @NotBlank(message = "任务内容不能为空")
        @Size(max = 2000, message = "任务内容不超过 2000 个字")
        String content
) {
}
```

**文件**：`src/main/java/top/mqxu/security/dto/ReviewTaskRequest.java`

```java
package top.mqxu.security.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 教师批改的请求
 **/
public record ReviewTaskRequest(

        @NotNull(message = "分数不能为空")
        @Min(value = 0, message = "分数不能小于 0")
        @Max(value = 100, message = "分数不能大于 100")
        Integer score,

        @Size(max = 500, message = "评语不超过 500 个字")
        String comment
) {
}
```

**文件**：`src/main/java/top/mqxu/security/vo/TaskView.java`

```java
package top.mqxu.security.vo;

import top.mqxu.security.model.TaskRow;
import top.mqxu.security.model.TaskStatus;

import java.time.LocalDateTime;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 任务的对外视图
 * <p>
 * status 给前端做逻辑判断，statusText 直接显示。前端不用再写一份
 * “SUBMITTED 就是待批改”的映射，枚举的中文由后端给。
 **/
public record TaskView(

        Long id,
        String title,
        String content,
        Long ownerId,
        String owner,
        String ownerNickname,
        String status,
        String statusText,
        Integer score,
        String comment,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {

    public static TaskView of(TaskRow row) {
        return new TaskView(
                row.getId(),
                row.getTitle(),
                row.getContent(),
                row.getOwnerId(),
                row.getOwner(),
                row.getOwnerNickname(),
                row.getStatus(),
                TaskStatus.valueOf(row.getStatus()).text(),
                row.getScore(),
                row.getComment(),
                row.getSubmittedAt(),
                row.getReviewedAt());
    }
}
```

**文件**：`src/main/java/top/mqxu/security/service/TaskService.java`

```java
package top.mqxu.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.mqxu.security.common.BusinessException;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.dto.ReviewTaskRequest;
import top.mqxu.security.dto.SubmitTaskRequest;
import top.mqxu.security.entity.Task;
import top.mqxu.security.mapper.TaskMapper;
import top.mqxu.security.model.TaskRow;
import top.mqxu.security.model.TaskStatus;
import top.mqxu.security.security.SecurityUser;
import top.mqxu.security.vo.TaskView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 实训任务：学生提交、教师批改、按归属决定能看哪几条
 **/
@Slf4j
@Service
public class TaskService {

    /**
     * 能批改任务的人持有的权限点，写常量避免字符串散落
     */
    private static final String REVIEW_PERMISSION = "task:review";

    private final TaskMapper taskMapper;

    public TaskService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 提交人从令牌取，不从请求体取。这是本模块最重要的一条规矩：
     * 凡是“归谁所有”，只能由服务端按令牌判定。
     */
    public TaskView submit(SubmitTaskRequest request, SecurityUser user) {
        Long duplicated = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getOwnerId, user.id())
                .eq(Task::getTitle, request.title())
                .eq(Task::getStatus, TaskStatus.SUBMITTED));
        if (duplicated != null && duplicated > 0) {
            throw new BusinessException(ErrorCode.TASK_DUPLICATED);
        }

        Task task = new Task();
        task.setTitle(request.title());
        task.setContent(request.content());
        task.setOwnerId(user.id());
        task.setStatus(TaskStatus.SUBMITTED);
        task.setSubmittedAt(LocalDateTime.now());
        taskMapper.insert(task);

        log.info("{} 提交任务：{}", user.getUsername(), request.title());
        return TaskView.of(findRow(task.getId()));
    }

    /**
     * 只返回自己的。参数是令牌里的用户 id，前端没法通过改参数看到别人的提交
     */
    public List<TaskView> listMine(Long ownerId) {
        return taskMapper.selectRowsByOwnerId(ownerId).stream().map(TaskView::of).toList();
    }

    /**
     * 全部提交，给教师批改用，权限点已在 Controller 上校验
     * <p>
     * 现在一次返回全量，前端在本地做筛选与统计。数据量上来要换成分页，
     * 那时统计得单独出一个聚合接口，不能拿当前页的数据算平均分。
     */
    public List<TaskView> listAll() {
        return taskMapper.selectAllRows().stream().map(TaskView::of).toList();
    }

    /**
     * 看单条。URL 里的 id 是可以随便改的，所以必须在服务端判断这条记录归不归你
     */
    public TaskView detail(Long id, SecurityUser viewer) {
        TaskRow row = findRow(id);
        if (!canView(row, viewer)) {
            log.warn("{} 试图查看 {} 的任务 {}", viewer.getUsername(), row.getOwner(), id);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return TaskView.of(row);
    }

    public TaskView review(Long id, ReviewTaskRequest request) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        if (task.getStatus() == TaskStatus.REVIEWED) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_REVIEWED);
        }
        task.markReviewed(request.score(), request.comment(), LocalDateTime.now());
        taskMapper.updateById(task);

        log.info("任务 {} 已批改，得分 {}", id, request.score());
        return TaskView.of(findRow(id));
    }

    /**
     * 两种人能看：提交人本人，或者有批改权限的教师与管理员
     */
    private boolean canView(TaskRow row, SecurityUser viewer) {
        if (row.getOwnerId().equals(viewer.id())) {
            return true;
        }
        return viewer.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(REVIEW_PERMISSION));
    }

    private TaskRow findRow(Long id) {
        TaskRow row = taskMapper.selectRowById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return row;
    }
}
```

**文件**：`src/main/java/top/mqxu/security/controller/TaskController.java`

```java
package top.mqxu.security.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;
import top.mqxu.security.dto.ReviewTaskRequest;
import top.mqxu.security.dto.SubmitTaskRequest;
import top.mqxu.security.security.SecurityUser;
import top.mqxu.security.service.TaskService;
import top.mqxu.security.vo.TaskView;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 实训任务接口。每条路径后面写清“谁能调、为什么是他”
 * <p>
 * 前端调用顺序：登录拿令牌 -> 每个请求带 Authorization -> 收到 401 跳登录、403 提示无权限。
 **/
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 提交任务：只有学生有这个权限点，教师调会得到 403
     */
    @PostMapping
    @PreAuthorize("hasAuthority('task:submit')")
    public Result<TaskView> submit(@Valid @RequestBody SubmitTaskRequest request,
                                   @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(taskService.submit(request, user));
    }

    /**
     * 我的提交：登录即可，不需要额外权限点。
     * 注意 URL 里没有用户名，前端想查别人的也无从下手；
     * 这里传的是令牌里的用户 id，不是用户名
     */
    @GetMapping("/mine")
    public Result<List<TaskView>> mine(@AuthenticationPrincipal SecurityUser user) {
        return Result.ok(taskService.listMine(user.id()));
    }

    /**
     * 全部提交：教师要批改，所以按权限点放行，学生调会得到 403
     */
    @GetMapping
    @PreAuthorize("hasAuthority('task:review')")
    public Result<List<TaskView>> all() {
        return Result.ok(taskService.listAll());
    }

    /**
     * 看单条：本人或有批改权限的人。判断在 Service 里做，因为要看这条记录的数据
     */
    @GetMapping("/{id}")
    public Result<TaskView> detail(@PathVariable Long id, @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(taskService.detail(id, user));
    }

    /**
     * 批改：教师与管理员
     */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('task:review')")
    public Result<TaskView> review(@PathVariable Long id, @Valid @RequestBody ReviewTaskRequest request) {
        return Result.ok(taskService.review(id, request));
    }
}
```

**为什么这么写**

- `SubmitTaskRequest` 里没有 `owner` 字段。提交人由服务端从令牌取，前端就算在请求体里塞 `"owner":"student02"` 也不会被采纳。这是本模块最重要的一条规矩：归谁所有，只能由服务端判定。
- `/mine` 的 URL 里没有用户名，传的是令牌里的 `user.id()`。前端想查别人的，连参数都没地方放。
- 详情接口的归属判断写在 Service 里，因为要拿这条记录的数据才能判断。URL 里的 id 是可以随便改的，必须在服务端校验。
- 越权抛 `BusinessException(ErrorCode.FORBIDDEN)`，全局异常处理器把它转成真 403。不是 200 里包一个错误码。
- 同一任务重复提交返回 30011，重复批改返回 30012。两个都是业务失败，走 200 + code。
- 归属比对用 `owner_id` 与令牌里的用户 id，不用用户名。用户名能改，id 不变。
- `@PreAuthorize` 用的都是权限点而不是角色：`task:submit`、`task:review`。将来教师和助教都能批改，只要给助教配同一个权限点，接口不用改。
- 列表一次返回全量，前端在本地做筛选和统计。代码里留了注释：换成后端分页后，平均分不能拿当前页算。

**验证**

```bash
T=$(curl -s -X POST http://127.0.0.1:8009/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"student01","password":"Student#2026"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -s -H "Authorization: Bearer $T" http://127.0.0.1:8009/api/tasks/mine
curl -s -w '\n[%{http_code}]\n' -H "Authorization: Bearer $T" http://127.0.0.1:8009/api/tasks
curl -s -w '\n[%{http_code}]\n' -H "Authorization: Bearer $T" http://127.0.0.1:8009/api/tasks/2
```

预期：`/mine` 返回自己的 3 条；`/api/tasks` 返回 403（学生没有 `task:review`）；`/api/tasks/2` 返回 403（id=2 是 student02 的）。

## 步骤 11 · 账号管理接口

**这一步做什么**

三个接口：列表、新增、删除。类上有 `@PreAuthorize("hasRole('ADMIN')")`，URL 层还有 `/admin/**` 的规则。

新增时角色用 `Set<Role>` 接，传 `["STUDENT"]` 会被 Jackson 直接转成枚举，写错角色名在反序列化阶段就失败。

**文件**：`src/main/java/top/mqxu/security/dto/CreateUserRequest.java`

```java
package top.mqxu.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import top.mqxu.security.model.Role;

import java.util.Set;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 新增用户请求。密码明文只在这个对象里出现，落库前必须加密
 **/
public record CreateUserRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度 3 到 32 个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度 6 到 64 个字符")
        String password,

        @NotBlank(message = "昵称不能为空")
        String nickname,

        @NotEmpty(message = "至少指定一个角色")
        Set<Role> roles
) {
}
```

**文件**：`src/main/java/top/mqxu/security/controller/UserAdminController.java`

```java
package top.mqxu.security.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.security.common.Result;
import top.mqxu.security.dto.CreateUserRequest;
import top.mqxu.security.security.SecurityUser;
import top.mqxu.security.service.UserService;
import top.mqxu.security.vo.UserProfile;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 账号管理接口，由 URL 规则（/admin/**）与方法注解双重把关
 **/
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<List<UserProfile>> list() {
        return Result.ok(userService.listProfiles());
    }

    /**
     * 显式要求 user:write 权限点：即便某天把 /admin/** 的规则放宽，这里仍是第二道防线
     */
    @PostMapping
    @PreAuthorize("hasAuthority('user:write')")
    public Result<UserProfile> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.ok(userService.create(request));
    }

    @DeleteMapping("/{username}")
    public Result<Void> delete(@PathVariable String username, @AuthenticationPrincipal SecurityUser user) {
        userService.delete(username, user.getUsername());
        return Result.ok();
    }
}
```

**为什么这么写**

- 类级注解管三个方法，新增方法上额外要求 `user:write`。这样做是示范“第二道防线”：URL 规则放宽了，注解还在。
- 删除接口把当前登录用户名传进 Service，禁止删自己。最后一个管理员把自己删掉，就再也登不进来了。
- 列表返回 `UserProfile`，字段是白名单，密码不进来。用实体返回的话，`password` 会原样出现在 JSON 里。
- `CreateUserRequest.roles` 用 `Set` 不用 `List`，同一个角色传两次不会重复插关联，也不会撞主键。

**验证**

```bash
A=$(curl -s -X POST http://127.0.0.1:8009/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin#2026"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -s -H "Authorization: Bearer $A" http://127.0.0.1:8009/admin/users | head -c 300
curl -s -X DELETE -w '\n[%{http_code}]\n' -H "Authorization: Bearer $A" http://127.0.0.1:8009/admin/users/admin
```

预期：列表返回 14 个账号，`data` 里没有 `password` 字段；删除自己的请求返回 200 与 `code:30005`（不能删除当前登录账号）。

## 步骤 12 · 把规则写成测试

**这一步做什么**

22 个用例，按“登录 → 学生 → 教师 → 管理员”的顺序排，断言的顺序就是前端实际调用的顺序。跑在真实 MySQL 上，不 mock 数据访问。

**文件**：`src/test/java/top/mqxu/security/SecurityApiTest.java`

```java
package top.mqxu.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 案例主流程测试：学生提交、只看自己的、教师批改、管理员管账号
 * <p>
 * 断言的顺序就是前端实际调用的顺序，出问题时能直接看出卡在哪一步。
 **/
@SpringBootTest
@AutoConfigureMockMvc
class SecurityApiTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin#2026";

    private static final String TEACHER = "teacher01";
    private static final String TEACHER_PASSWORD = "Teacher#2026";

    private static final String STUDENT = "student01";
    private static final String STUDENT_PASSWORD = "Student#2026";

    /**
     * 预置数据里 student02 的那条任务，用来验证越权查看
     */
    private static final long STUDENT2_TASK_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private String tokenOf(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String submitBody(String title) {
        return "{\"title\":\"" + title + "\",\"content\":\"按实训要求做完了，代码见附件。\"}";
    }

    private long submitTask(String token, String title) throws Exception {
        String body = mockMvc.perform(withToken(post("/api/tasks"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(title)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.data.id");
        return id.longValue();
    }

    /* ==================== 一、登录拿到身份 ==================== */

    @Test
    @DisplayName("放行接口不带令牌也能访问")
    void publicEndpointIsOpen() throws Exception {
        mockMvc.perform(get("/public/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"));
    }

    @Test
    @DisplayName("没带令牌访问任务接口返回 401")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/tasks/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登录成功返回令牌与角色")
    void loginIssuesToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(STUDENT, STUDENT_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.roles", hasItem("STUDENT")));
    }

    @Test
    @DisplayName("密码错误与用户不存在给同一个提示")
    void loginWithBadCredentials() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(STUDENT, "wrong-password")))
                .andExpect(jsonPath("$.code").value(30001));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody", "whatever")))
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    @DisplayName("停用账号登录返回 30002")
    void loginWithDisabledAccount() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("guest", "Guest#2026")))
                .andExpect(jsonPath("$.code").value(30002));
    }

    @Test
    @DisplayName("缺少密码时返回参数校验提示")
    void loginValidatesInput() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student01\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码不能为空"));
    }

    @Test
    @DisplayName("带令牌换当前用户，响应里没有密码")
    void tokenGrantsAccess() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(get("/auth/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(STUDENT))
                .andExpect(jsonPath("$.data.roles", hasItem("STUDENT")))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("令牌被改过一位就按未认证处理")
    void tamperedTokenIsRejected() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        mockMvc.perform(withToken(get("/auth/me"), tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /* ==================== 二、学生只能碰自己的数据 ==================== */

    @Test
    @DisplayName("学生提交任务，归属由服务端写死成当前登录人")
    void studentSubmitsTask() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(post("/api/tasks"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("实训三：接口鉴权改造")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner").value(STUDENT))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.statusText").value("待批改"));
    }

    @Test
    @DisplayName("请求体里塞别人的用户名不生效，归属仍是当前登录人")
    void ownerCannotBeForgedByBody() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(post("/api/tasks"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"实训四：伪造归属\",\"content\":\"试试改 owner\",\"owner\":\"student02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner").value(STUDENT));
    }

    @Test
    @DisplayName("我的提交里只有自己的")
    void studentSeesOnlyOwnTasks() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(get("/api/tasks/mine"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].owner", everyItem(is(STUDENT))));
    }

    @Test
    @DisplayName("学生看别人的任务返回真实 403，不是 200 里裹一个错误码")
    void studentCannotViewOthersTask() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(get("/api/tasks/" + STUDENT2_TASK_ID), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("学生能看自己那条的详情，含分数与评语")
    void studentCanViewOwnTask() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);
        long id = submitTask(token, "实训五：查看详情");

        mockMvc.perform(withToken(get("/api/tasks/" + id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.owner").value(STUDENT));
    }

    /* ==================== 三、教师的权限点与批改 ==================== */

    @Test
    @DisplayName("学生不能看全部提交，也不能批改")
    void studentCannotReview() throws Exception {
        String token = tokenOf(STUDENT, STUDENT_PASSWORD);

        mockMvc.perform(withToken(get("/api/tasks"), token))
                .andExpect(status().isForbidden());

        mockMvc.perform(withToken(post("/api/tasks/" + STUDENT2_TASK_ID + "/review"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":100,\"comment\":\"自己给自己打满分\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("教师看得到全部提交，也能看到是谁交的")
    void teacherSeesAllTasks() throws Exception {
        String token = tokenOf(TEACHER, TEACHER_PASSWORD);

        mockMvc.perform(withToken(get("/api/tasks"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].owner").isNotEmpty());
    }

    @Test
    @DisplayName("教师批改后学生能看到分数，同一份不能批改两次")
    void teacherReviewsTask() throws Exception {
        String studentToken = tokenOf(STUDENT, STUDENT_PASSWORD);
        String teacherToken = tokenOf(TEACHER, TEACHER_PASSWORD);
        long id = submitTask(studentToken, "实训六：等待批改");

        mockMvc.perform(withToken(post("/api/tasks/" + id + "/review"), teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":88,\"comment\":\"归属判断对了，注意异常分支\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.score").value(88));

        // 学生这边立刻能看到分数
        mockMvc.perform(withToken(get("/api/tasks/" + id), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(88))
                .andExpect(jsonPath("$.data.comment").value("归属判断对了，注意异常分支"));

        // 再批一次被拒
        mockMvc.perform(withToken(post("/api/tasks/" + id + "/review"), teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":60,\"comment\":\"改个分\"}"))
                .andExpect(jsonPath("$.code").value(30012));
    }

    @Test
    @DisplayName("教师没有提交权限，调提交接口返回 403")
    void teacherCannotSubmit() throws Exception {
        String token = tokenOf(TEACHER, TEACHER_PASSWORD);

        mockMvc.perform(withToken(post("/api/tasks"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("教师不该能交任务")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("分数超出 0 到 100 时被参数校验拦住")
    void reviewValidatesScore() throws Exception {
        String studentToken = tokenOf(STUDENT, STUDENT_PASSWORD);
        String teacherToken = tokenOf(TEACHER, TEACHER_PASSWORD);
        long id = submitTask(studentToken, "实训七：分数越界");

        mockMvc.perform(withToken(post("/api/tasks/" + id + "/review"), teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":120}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("分数不能大于 100"));
    }

    /* ==================== 四、管理员管账号 ==================== */

    @Test
    @DisplayName("教师进不了管理接口")
    void teacherCannotReachAdminArea() throws Exception {
        String token = tokenOf(TEACHER, TEACHER_PASSWORD);

        mockMvc.perform(withToken(get("/admin/users"), token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员能看到账号列表，响应里没有密码")
    void adminCanListUsers() throws Exception {
        String token = tokenOf(ADMIN, ADMIN_PASSWORD);

        mockMvc.perform(withToken(get("/admin/users"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").isNotEmpty())
                .andExpect(jsonPath("$.data[0].password").doesNotExist());
    }

    @Test
    @DisplayName("管理员新增学生后，这个学生能登录并提交任务")
    void createdUserCanLogin() throws Exception {
        String adminToken = tokenOf(ADMIN, ADMIN_PASSWORD);

        mockMvc.perform(withToken(post("/admin/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"student99","password": "NewStu#2026","nickname":"新加的同学","roles":["STUDENT"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItem("STUDENT")));

        String token = tokenOf("student99", "NewStu#2026");
        assertThat(token).isNotBlank();

        mockMvc.perform(withToken(get("/api/tasks/mine"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("不能删掉当前登录的账号")
    void cannotDeleteSelf() throws Exception {
        String token = tokenOf(ADMIN, ADMIN_PASSWORD);

        mockMvc.perform(withToken(delete("/admin/users/" + ADMIN), token))
                .andExpect(jsonPath("$.code").value(30005));
    }
}
```

**为什么这么写**

- 用 `MockMvc` 而不是 `TestRestTemplate`：不起真实端口，快；同时走完过滤器链，401、403 这些行为能测到。
- 测试直接读种子数据里的 id（`STUDENT2_TASK_ID = 2`），所以 `data.sql` 的主键必须写死。
- 新增账号用例用 `student99`，避开种子里的 `student01` 到 `student10`。测试建号后再用新账号登录、查自己的提交为空，整条链路一次验完。
- 断言越权用 `status().isForbidden()` 而不是断言响应体的 code。状态码错了但 body 对了，前端照样会把拒绝当成功。
- `mvn test` 会重建表并灌数据，所以跑完测试后演示库里有测试产生的记录。要干净数据就重启一次应用。

**验证**

```bash
mvn -o -pl 09-security test
```

预期：Tests run: 22, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS。

## 步骤 13 · 前端工程与构建配置

**这一步做什么**

Vite + Vue 3 + TypeScript，不引 UI 组件库。样式手写，注意力留在鉴权链路上。

这一节只建工程骨架，还没有页面。`npm install` 能成功就算过。

**文件**：`web/package.json`

```json
{
  "name": "security-web-demo",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "description": "前后端分离联调 demo：登录拿令牌、请求带令牌、401 跳登录、403 提示无权限",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "typecheck": "vue-tsc --noEmit",
    "preview": "vite preview"
  },
  "dependencies": {
    "axios": "^1.21.0",
    "vue": "^3.5.44",
    "vue-router": "^4.6.4"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^6.0.10",
    "typescript": "^5.9.3",
    "vite": "^8.3.5",
    "vue-tsc": "^3.3.13"
  }
}
```

**文件**：`web/tsconfig.json`

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "jsx": "preserve",
    "isolatedModules": true,
    "esModuleInterop": true,
    "resolveJsonModule": true,
    "skipLibCheck": true,
    "noEmit": true,
    "useDefineForClassFields": true,
    "types": ["vite/client"],
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.d.ts", "src/**/*.vue", "vite.config.ts"]
}
```

**文件**：`web/vite.config.ts`

```ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 开发期把这三个前缀的请求转发给后端。
    // 浏览器全程只访问 5173，同源，所以开发时其实用不到后端那份 CORS 配置。
    //
    // 少写一条的后果：/admin/users 会打到 Vite 自己身上，返回 index.html，
    // 前端解析 JSON 失败，页面看着像“接口没数据”。代理前缀必须和实际路径一一对上。
    proxy: {
      '/api': { target: 'http://127.0.0.1:8009', changeOrigin: true },
      '/auth': { target: 'http://127.0.0.1:8009', changeOrigin: true },
      '/admin': { target: 'http://127.0.0.1:8009', changeOrigin: true },
    },
  },
})
```

**文件**：`web/index.html`

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>实训任务提交与批改</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

**文件**：`web/src/env.d.ts`

```ts
/// <reference types="vite/client" />
```

**为什么这么写**

- `strict: true` 是这份工程的核心设置。接口返回 `score: null` 时，页面必须显式处理，否则类型检查不过。
- `noUnusedLocals`、`noUnusedParameters` 打开。`vue-tsc` 会把没用到的变量当错误，逼着人删干净。
- `build` 写成 `vue-tsc --noEmit && vite build`。类型检查放进构建，字段名写错在打包阶段就失败，不用等浏览器白屏。
- Vite 代理配三条：`/api`、`/auth`、`/admin`。少配一条的后果是请求打到 Vite 自己身上返回 `index.html`，前端解析 JSON 失败，页面看着像“接口没数据”。这个坑在管理页上尤其隐蔽，学生页和教师页都正常，只有管理员页空白。
- `changeOrigin: true` 让转发出去的 Host 头变成后端地址。某些后端会校验 Host，不加会 400。
- `types: ["vite/client"]` 才能用 `import.meta.env` 这类 Vite 注入的东西。

**验证**

```bash
cd 09-security/web && npm install
```

预期：看到 `added N packages` 且 exit 0。国内网络慢就换成 `npm install --registry=https://registry.npmmirror.com`。

## 步骤 14 · 与后端对齐的类型

**这一步做什么**

一个文件写完所有接口类型。这些不是手抄的，是对着后端 `vo` 与 `dto` 一个个核过的：字段名、可空性、枚举取值。

改后端字段时先改这里，`npm run typecheck` 会把所有用到的地方指出来。

**文件**：`web/src/types/api.ts`

```ts
/**
 * 与后端约定的类型，一个文件写完。
 *
 * 这些接口不是手抄的，是对着后端 vo 与 dto 一个个核过的：
 * 改后端字段时先改这里，`npm run typecheck` 会把用到的地方全指出来。
 */

/** 角色编码，与后端 Role 枚举同名 */
export type RoleCode = 'STUDENT' | 'TEACHER' | 'ADMIN'

/** 任务状态，与后端 TaskStatus 枚举同名 */
export type TaskStatusCode = 'SUBMITTED' | 'REVIEWED'

/**
 * 后端统一响应体。业务失败也是 HTTP 200，靠 code 区分
 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresAt: string
  username: string
  nickname: string
  roles: RoleCode[]
}

/** 当前登录人，只存页面要用的字段，别把整份 UserProfile 塞进 localStorage */
export interface CurrentUser {
  username: string
  nickname: string
  roles: RoleCode[]
}

export interface UserProfile {
  username: string
  nickname: string
  roles: RoleCode[]
  authorities: string[]
  enabled: boolean
}

export interface TaskView {
  id: number
  title: string
  content: string
  ownerId: number
  owner: string
  ownerNickname: string
  status: TaskStatusCode
  /** 后端给的中文，前端不再自己映射 */
  statusText: string
  score: number | null
  comment: string | null
  submittedAt: string
  reviewedAt: string | null
}

export interface SubmitTaskPayload {
  title: string
  content: string
}

export interface ReviewTaskPayload {
  score: number
  comment: string
}

export interface CreateUserPayload {
  username: string
  password: string
  nickname: string
  roles: RoleCode[]
}
```

**为什么这么写**

- `TaskView.score` 是 `number | null`，不是 `number`。未批改时后端给 null，写成 `number` 页面里就会漏掉判空。
- `status` 用联合类型 `'SUBMITTED' | 'REVIEWED'`，与后端枚举同名。写错字符串编译就报。
- `statusText` 也放在类型里。中文由后端给，前端不再写一份“SUBMITTED 就是待批改”的映射，两处映射迟早对不上。
- `CurrentUser` 与 `UserProfile` 分开：前者是 localStorage 里存的少量字段，后者是接口返回的完整视图。混用一个类型，容易把敏感字段顺手存到浏览器里。

**验证**

```bash
cd 09-security/web
npx vue-tsc --noEmit
```

预期：此时会报“找不到 ../router”之类的错误，因为后续文件还没建。这一步只确认 `types/api.ts` 自身没有语法错误。

## 步骤 15 · 令牌存哪、怎么清

**这一步做什么**

登录态只有三件事：存令牌、存当前用户、清空。用 `reactive` 包一层，组件里改完会自动更新。

`toast.ts` 是一个极简提示条，不引组件库，用一个数组加定时清理。

**文件**：`web/src/store/auth.ts`

```ts
import { reactive } from 'vue'
import type { CurrentUser, LoginResponse, RoleCode, UserProfile } from '../types/api'

/**
 * 登录态只做三件事：存令牌、存当前用户、清空。
 *
 * 存 localStorage 而不是内存，是因为刷新页面后内存变量会丢，刷新就掉登录。
 * 为什么不存 cookie：令牌走请求头，浏览器不会自动带上，天然不受 CSRF 影响；
 * 代价是脚本能读到它，所以要防 XSS。
 */
const TOKEN_KEY = 'demo_token'
const USER_KEY = 'demo_user'

function readUser(): CurrentUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as CurrentUser
  } catch {
    // 存进去的东西被人改坏了就当没登录，别让页面崩在这里
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export const auth = reactive({
  token: localStorage.getItem(TOKEN_KEY) ?? '',
  user: readUser() as CurrentUser | null,
})

export function saveLogin(data: LoginResponse): void {
  auth.token = data.token
  auth.user = { username: data.username, nickname: data.nickname, roles: data.roles }
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(auth.user))
}

/** 刷新页面后用 /auth/me 换回来的最新身份，角色可能已被管理员改过 */
export function saveUser(user: UserProfile): void {
  auth.user = { username: user.username, nickname: user.nickname, roles: user.roles }
  localStorage.setItem(USER_KEY, JSON.stringify(auth.user))
}

export function clearLogin(): void {
  auth.token = ''
  auth.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function hasRole(role: RoleCode): boolean {
  return auth.user?.roles.includes(role) ?? false
}

export function hasAnyRole(roles: readonly RoleCode[]): boolean {
  return roles.some((role) => hasRole(role))
}
```

**文件**：`web/src/store/toast.ts`

```ts
import { reactive } from 'vue'

/**
 * 极简提示条。不引组件库，把注意力留给鉴权链路本身。
 */
interface Toast {
  id: number
  message: string
}

export const toasts = reactive<Toast[]>([])

let seq = 0

export function notify(message: string): void {
  const id = ++seq
  toasts.push({ id, message })
  setTimeout(() => {
    const index = toasts.findIndex((item) => item.id === id)
    if (index > -1) toasts.splice(index, 1)
  }, 2600)
}
```

**为什么这么写**

- 令牌存 `localStorage` 而不是内存：存内存刷新页面就掉登录。
- 不存 cookie：令牌走请求头，浏览器不会自动带上，天然不受 CSRF 影响；代价是脚本能读到它，所以要防 XSS。
- 读 `localStorage` 时包 `try/catch`。存进去的内容被人改坏（不是合法 JSON），不兜住会让整个应用在启动时白屏。
- `clearLogin` 同时清内存与 localStorage。只清一个，刷新页面又“登录”回来了。
- `hasRole` 与 `hasAnyRole` 放在 store 里，菜单、路由守卫、按钮都用同一份判断。

**验证**

```bash
cd 09-security/web
npx vue-tsc --noEmit
```

预期：仍然会报后续模块缺失，属正常。`store` 这两个文件本身不应该有类型错误。

## 步骤 16 · 路由与守卫

**这一步做什么**

四个页面：登录、我的提交、待批改、账号管理。每个路由用 `meta.roles` 声明谁能进。

守卫拦两种情况：没令牌去登录页，角色不对回自己的首页。

**文件**：`web/src/router/index.ts`

```ts
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { auth, hasAnyRole } from '../store/auth'
import type { RoleCode } from '../types/api'

/**
 * 给路由元信息加类型，写 meta.roles 时 IDE 会提示可选值，
 * 写错角色名在 typecheck 阶段就报错，不用等运行时白屏。
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** true 表示不需要登录 */
    public?: boolean
    /** 允许进入的角色，不写表示登录即可 */
    roles?: RoleCode[]
  }
}

/**
 * 路由守卫是前端的第一道门，但它只是体验层的拦截。
 * 真正的权限在后端，前端就算绕过去，接口照样返回 403。
 */
export function homeOf(): string {
  if (hasAnyRole(['ADMIN'])) return '/users'
  if (hasAnyRole(['TEACHER'])) return '/review'
  return '/tasks'
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/tasks',
    name: 'tasks',
    component: () => import('../views/TaskList.vue'),
    meta: { roles: ['STUDENT'] },
  },
  {
    path: '/review',
    name: 'review',
    component: () => import('../views/ReviewList.vue'),
    meta: { roles: ['TEACHER', 'ADMIN'] },
  },
  {
    path: '/users',
    name: 'users',
    component: () => import('../views/Users.vue'),
    meta: { roles: ['ADMIN'] },
  },
  { path: '/', redirect: () => homeOf() },
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !hasAnyRole(to.meta.roles)) {
    // 角色不对时回到自己的首页，而不是踢回登录页
    return homeOf()
  }
  return true
})

export default router
```

**为什么这么写**

- 用 `declare module 'vue-router'` 给 `RouteMeta` 加类型，`meta.roles` 写错角色名编译就报。
- 角色不对时回自己的首页，不是踢回登录页。学生手输 `/users`，踢他去登录页会让他以为登录失效了。
- `homeOf()` 按角色算首页，登录成功与根路径重定向都用它，只有一处逻辑。
- 页面用动态 `import()` 懒加载，每个页面单独一个 chunk，首屏只加载登录页。
- 守卫只是体验层的拦截。有人改 localStorage 就能进页面，但进去以后接口全是 403。真正的权限在后端，这句话要写在注释里，也要在课上强调。

**验证**

```bash
cd 09-security/web
npx vue-tsc --noEmit
```

预期：会报找不到 `../views/Login.vue` 等模块，因为页面还没建。这是预期的，下一步补上。

## 步骤 17 · 请求封装与接口清单

**这一步做什么**

一个 `request.ts` 承担三条约定：每个请求自动带 `Authorization`；401 清登录态回登录页；403 只提示不踢人。

`api/index.ts` 是接口清单，页面不直接写 URL，每个方法都标了返回类型。

**文件**：`web/src/api/request.ts`

```ts
import axios, { type AxiosError } from 'axios'
import router from '../router'
import { auth, clearLogin } from '../store/auth'
import { notify } from '../store/toast'
import type { ApiResult } from '../types/api'

/**
 * 前后端分离里，这一个文件承担三条约定：
 *
 * 1. 每个请求自动带上 Authorization: Bearer <token>
 * 2. HTTP 401 说明没登录或令牌失效，清登录态回登录页
 * 3. HTTP 403 说明登录了但没权限，只提示，不能把用户踢去重新登录
 *
 * 页面里都不写这几段逻辑，只写业务，这样才不会每个页面各写一套。
 */
const request = axios.create({ timeout: 10000 })

request.interceptors.request.use((config) => {
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    // 后端的约定：成功和数据都在 Result 里，业务失败也是 HTTP 200
    const body = response.data as ApiResult<unknown>
    if (body.code === 200) {
      return body.data as never
    }
    notify(body.message || '操作失败')
    return Promise.reject(new Error(body.message || '操作失败'))
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    const message = error.response?.data?.message

    if (status === 401) {
      clearLogin()
      const from = router.currentRoute.value.fullPath
      router.push({ path: '/login', query: from === '/login' ? {} : { redirect: from } })
      notify(message || '登录已失效，请重新登录')
    } else if (status === 403) {
      // 只提示，不动登录态
      notify(message || '你没有权限做这个操作')
    } else if (status) {
      notify(message || `请求失败（${status}）`)
    } else {
      notify('连不上后端，确认 8009 是否已启动')
    }
    return Promise.reject(error)
  }
)

/**
 * 拦截器已经把 Result 拆成 data 了，但 axios 的类型仍认为是完整响应，
 * 所以在这里统一校正一次：调用方拿到的就是 T，不用再 .data.data。
 * 只写这一处断言，页面里全是实打实的类型。
 */
async function unwrap<T>(promise: Promise<unknown>): Promise<T> {
  return (await promise) as T
}

export const http = {
  get: <T>(url: string) => unwrap<T>(request.get(url)),
  post: <T>(url: string, data?: unknown) => unwrap<T>(request.post(url, data)),
  del: <T>(url: string) => unwrap<T>(request.delete(url)),
}

export default request
```

**文件**：`web/src/api/index.ts`

```ts
import { http } from './request'
import type {
  CreateUserPayload,
  LoginPayload,
  LoginResponse,
  ReviewTaskPayload,
  SubmitTaskPayload,
  TaskView,
  UserProfile,
} from '../types/api'

/**
 * 所有接口集中在这里，页面不直接写 URL。
 * 后端改路径时只改这个文件，页面不用动；每个方法都标了返回类型，
 * 页面里忘了处理 null 或写错字段，typecheck 会直接报出来。
 */

// 认证
export const login = (data: LoginPayload) => http.post<LoginResponse>('/auth/login', data)
export const me = () => http.get<UserProfile>('/auth/me')

// 实训任务
export const submitTask = (data: SubmitTaskPayload) => http.post<TaskView>('/api/tasks', data)
export const myTasks = () => http.get<TaskView[]>('/api/tasks/mine')
export const taskDetail = (id: number) => http.get<TaskView>(`/api/tasks/${id}`)
export const allTasks = () => http.get<TaskView[]>('/api/tasks')
export const reviewTask = (id: number, data: ReviewTaskPayload) =>
  http.post<TaskView>(`/api/tasks/${id}/review`, data)

// 账号管理
export const listUsers = () => http.get<UserProfile[]>('/admin/users')
export const createUser = (data: CreateUserPayload) => http.post<UserProfile>('/admin/users', data)
export const deleteUser = (username: string) => http.del<void>(`/admin/users/${username}`)
```

**为什么这么写**

- 请求拦截器统一加头。页面里不写 `headers: { Authorization: ... }`，写一次就漏一次。
- 401 与 403 必须分开：401 是“你没登录”，清登录态并跳登录页，带上 `redirect` 记住来处；403 是“你登录了但没权限”，只提示，不动登录态。写反的后果是学生点了一个教师按钮就被踢下线，重新登录进去还在那个页面，再点又被踢。
- 业务失败也是 HTTP 200，靠 `code` 区分。响应拦截器先看 body 的 code，非 200 就提示并 reject，不能让页面把失败当成功。
- `unwrap<T>` 只在这一处断言。拦截器已经把 `Result` 拆成了 `data`，但 axios 的类型仍认为是完整响应，页面里到处 `as` 不如在这里收敛一次。
- `error` 标成 `AxiosError<ApiResult<unknown>>`，`error.response?.data?.message` 才有类型，不用强转。
- 超时设 10 秒。不设的话，后端没启动时请求会挂很久，页面上什么反应都没有。

**验证**

```bash
cd 09-security/web
npx vue-tsc --noEmit
```

预期：此时只剩页面模块缺失的报错。`request.ts` 与 `api/index.ts` 自身不应有错。

## 步骤 18 · 页面外壳、样式与登录页

**这一步做什么**

`App.vue` 是外壳：顶栏、菜单、当前用户、退出按钮、提示条容器。菜单按角色显示，学生看不到“待批改”。

登录页带四个快捷账号按钮，课堂演示时点一下就能切换身份。

**文件**：`web/src/style.css`

```css
:root {
  --bg: #f7f8fb;
  --surface: #ffffff;
  --border: #e4e8f0;
  --text: #1b2333;
  --text-2: #5b6779;
  --text-3: #8b95a5;
  --primary: #2f5cf6;
  --primary-soft: #eef2ff;
  --green: #0f7b46;
  --green-soft: #f1fbf5;
  --green-border: #c6e8d6;
  --amber: #b45309;
  --amber-soft: #fff7ed;
  --amber-border: #f3d9b5;
  --red: #c0392b;
  --red-soft: #fdf2f1;
  --radius: 10px;
  --mono: "SFMono-Regular", Menlo, Consolas, monospace;
}

* { box-sizing: border-box; }

body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  font-size: 14.5px;
  line-height: 1.7;
}

button { font-family: inherit; font-size: inherit; cursor: pointer; }
input, textarea { font-family: inherit; font-size: inherit; }

/* ---------- 顶栏 ---------- */
.topbar {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 28px;
  height: 58px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 10;
}
.brand { font-weight: 600; letter-spacing: .3px; }
.brand span { color: var(--text-3); font-weight: 400; margin-left: 8px; font-size: 13px; }
.nav { display: flex; gap: 4px; margin-left: 8px; }
.nav a {
  padding: 6px 12px;
  border-radius: 7px;
  color: var(--text-2);
  text-decoration: none;
  font-size: 14px;
}
.nav a:hover { background: var(--primary-soft); color: var(--primary); }
.nav a.active { background: var(--primary-soft); color: var(--primary); font-weight: 600; }
.spacer { flex: 1; }
.who { font-size: 13px; color: var(--text-2); }
.who b { color: var(--text); }
.role-tag {
  display: inline-block;
  font-size: 11.5px;
  padding: 1px 7px;
  border-radius: 20px;
  background: var(--primary-soft);
  color: var(--primary);
  margin-left: 6px;
  vertical-align: 1px;
}

/* ---------- 布局 ---------- */
.container { max-width: 1020px; margin: 0 auto; padding: 26px 24px 60px; }
.grid { display: grid; grid-template-columns: 360px minmax(0, 1fr); gap: 18px; align-items: start; }
@media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }

.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 18px 20px;
}
.card + .card { margin-top: 16px; }
.card h2 { font-size: 16px; margin: 0 0 4px; }
.card .hint { font-size: 12.5px; color: var(--text-3); margin: 0 0 14px; }
.muted { color: var(--text-3); }
.empty { padding: 26px 0; text-align: center; color: var(--text-3); font-size: 13.5px; }

/* ---------- 表单 ---------- */
.field { margin-bottom: 12px; }
.field label { display: block; font-size: 13px; color: var(--text-2); margin-bottom: 5px; }
.field input, .field textarea {
  width: 100%;
  padding: 8px 11px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  color: var(--text);
  outline: none;
  transition: border-color .15s, box-shadow .15s;
}
.field textarea { resize: vertical; min-height: 76px; }
.field input:focus, .field textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(47, 92, 246, .1);
}
.btn {
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text);
  border-radius: 8px;
  padding: 7px 14px;
  white-space: nowrap;
  transition: all .15s;
}
.btn:hover { border-color: var(--primary); color: var(--primary); }
.btn-primary { background: var(--primary); border-color: var(--primary); color: #fff; }
.btn-primary:hover { background: #2449d8; border-color: #2449d8; color: #fff; }
.btn-primary:disabled { opacity: .55; cursor: not-allowed; }
.btn-danger { color: var(--red); border-color: #f0cec9; background: var(--red-soft); }
.btn-danger:hover { border-color: var(--red); color: var(--red); }
.btn-sm { padding: 4px 10px; font-size: 13px; }

/* ---------- 统计条 ---------- */
.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
@media (max-width: 900px) { .stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.stat {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 11px 14px;
}
.stat .k { font-size: 12px; color: var(--text-3); }
.stat .v {
  font-size: 21px;
  font-weight: 600;
  line-height: 1.35;
  font-variant-numeric: tabular-nums;
}
.stat .v small { font-size: 12px; font-weight: 400; color: var(--text-3); margin-left: 3px; }
.stat.amber .v { color: var(--amber); }
.stat.green .v { color: var(--green); }

/* ---------- 工具栏：筛选与搜索 ---------- */
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.tabs {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
}
.tabs button {
  border: 0;
  background: transparent;
  color: var(--text-2);
  padding: 4px 11px;
  border-radius: 6px;
  font-size: 13px;
}
.tabs button:hover { color: var(--primary); }
.tabs button.on {
  background: var(--surface);
  color: var(--text);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(16, 24, 40, .12);
}
.tabs button .n {
  margin-left: 5px;
  font-family: var(--mono);
  font-size: 11.5px;
  color: var(--text-3);
}
.search {
  width: 190px;
  padding: 6px 11px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  color: var(--text);
  font-family: inherit;
  font-size: 13.5px;
  outline: none;
}
.search:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(47, 92, 246, .1); }
.count { font-size: 12.5px; color: var(--text-3); margin-left: auto; }

/* 列表里的长文本压成一行，点开详情看全文 */
.clip {
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ---------- 表格 ---------- */
table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
th, td { text-align: left; padding: 9px 10px; border-bottom: 1px solid var(--border); vertical-align: top; }
th { font-size: 12px; color: var(--text-3); font-weight: 600; letter-spacing: .3px; }
tbody tr:last-child td { border-bottom: none; }
tbody tr:hover { background: #fafbfe; }
td.mono { font-family: var(--mono); font-size: 12.5px; color: var(--text-2); }

.tag {
  display: inline-block;
  font-size: 11.5px;
  padding: 1px 8px;
  border-radius: 20px;
  border: 1px solid;
  white-space: nowrap;
}
.tag-wait { color: var(--amber); background: var(--amber-soft); border-color: var(--amber-border); }
.tag-done { color: var(--green); background: var(--green-soft); border-color: var(--green-border); }

.score { font-family: var(--mono); font-weight: 600; }

/* ---------- 登录页 ---------- */
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}
.login-card {
  width: 380px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 30px 30px 24px;
  box-shadow: 0 12px 40px -24px rgba(16, 24, 40, .3);
}
.login-card h1 { font-size: 21px; margin: 0 0 4px; }
.login-card .sub { font-size: 13px; color: var(--text-3); margin: 0 0 22px; }
.login-card .btn-primary { width: 100%; padding: 9px; margin-top: 4px; }
.err {
  font-size: 13px;
  color: var(--red);
  background: var(--red-soft);
  border: 1px solid #f0cec9;
  border-radius: 7px;
  padding: 7px 11px;
  margin-bottom: 12px;
}
.accounts { margin-top: 20px; padding-top: 16px; border-top: 1px dashed var(--border); }
.accounts .t { font-size: 12px; color: var(--text-3); margin-bottom: 8px; }
.accounts .row { display: flex; gap: 8px; flex-wrap: wrap; }
.chip {
  font-size: 12px;
  padding: 3px 10px;
  border: 1px solid var(--border);
  border-radius: 20px;
  background: var(--bg);
  color: var(--text-2);
}
.chip:hover { border-color: var(--primary); color: var(--primary); }

/* ---------- 提示 ---------- */
.toast-wrap {
  position: fixed;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 100;
}
.toast {
  background: #1b2333;
  color: #fff;
  font-size: 13.5px;
  padding: 9px 16px;
  border-radius: 8px;
  box-shadow: 0 8px 24px -12px rgba(0, 0, 0, .5);
  animation: drop .18s ease-out;
}
@keyframes drop {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}
```

**文件**：`web/src/main.ts`

```ts
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './style.css'

createApp(App).use(router).mount('#app')
```

**文件**：`web/src/App.vue`

```vue
<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { me } from './api'
import { auth, clearLogin, hasRole, saveUser } from './store/auth'
import { toasts } from './store/toast'

const router = useRouter()
const route = useRoute()

const loggedIn = computed(() => Boolean(auth.token) && route.path !== '/login')

/**
 * 刷新页面后用令牌换回用户信息。
 * 之所以要多这一步：localStorage 里的身份可能过期（角色被管理员改过），
 * 以接口返回的为准才靠得住。
 */
onMounted(async () => {
  if (!auth.token) return
  try {
    saveUser(await me())
  } catch {
    // 401 已在拦截器里清登录态并跳转，这里不用重复处理
  }
})

function logout(): void {
  clearLogin()
  router.push('/login')
}
</script>

<template>
  <div v-if="loggedIn" class="topbar">
    <div class="brand">实训任务提交与批改<span>09 · 认证与授权</span></div>
    <nav class="nav">
      <router-link v-if="hasRole('STUDENT')" to="/tasks">我的提交</router-link>
      <router-link v-if="hasRole('TEACHER') || hasRole('ADMIN')" to="/review">待批改</router-link>
      <router-link v-if="hasRole('ADMIN')" to="/users">账号管理</router-link>
    </nav>
    <div class="spacer"></div>
    <div class="who">
      <b>{{ auth.user?.nickname || auth.user?.username }}</b>
      <span class="role-tag">{{ auth.user?.roles.join(' / ') }}</span>
    </div>
    <button class="btn btn-sm" @click="logout">退出</button>
  </div>

  <router-view />

  <div class="toast-wrap">
    <div v-for="item in toasts" :key="item.id" class="toast">{{ item.message }}</div>
  </div>
</template>
```

**文件**：`web/src/views/Login.vue`

```vue
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api'
import { saveLogin } from '../store/auth'
import { homeOf } from '../router'
import type { LoginPayload } from '../types/api'

const router = useRouter()
const route = useRoute()

const form = reactive<LoginPayload>({ username: 'student01', password: 'Student#2026' })
const loading = ref(false)
const error = ref('')

interface DemoAccount {
  label: string
  username: string
  password: string
}

const demoAccounts: DemoAccount[] = [
  { label: '学生 student01', username: 'student01', password: 'Student#2026' },
  { label: '教师 teacher01', username: 'teacher01', password: 'Teacher#2026' },
  { label: '管理员 admin', username: 'admin', password: 'Admin#2026' },
  { label: '停用 guest', username: 'guest', password: 'Guest#2026' },
]

function fill(account: DemoAccount): void {
  form.username = account.username
  form.password = account.password
  error.value = ''
}

async function onSubmit(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    const data = await login(form)
    // 令牌和身份落库，后面每个请求由拦截器自动带上
    saveLogin(data)
    // query 的类型是 string | string[] | null，直接给 router 会报错，这里显式收敛
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || homeOf())
  } catch (e) {
    // 密码错、账号停用这类属于业务失败，后端返回 200 + 非 200 的 code，
    // 拦截器已经提示过一次，这里把原因显示在表单上方
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="login-card" @submit.prevent="onSubmit">
      <h1>实训任务提交与批改</h1>
      <p class="sub">登录后获取令牌，之后每个请求都会自动带上</p>

      <div v-if="error" class="err">{{ error }}</div>

      <div class="field">
        <label for="username">用户名</label>
        <input id="username" v-model.trim="form.username" autocomplete="username" placeholder="student01" />
      </div>
      <div class="field">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="form.password"
          type="password"
          autocomplete="current-password"
          placeholder="Student#2026"
        />
      </div>

      <button class="btn btn-primary" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登录' }}
      </button>

      <div class="accounts">
        <div class="t">点一下直接填入，不用手打</div>
        <div class="row">
          <button
            v-for="account in demoAccounts"
            :key="account.username"
            type="button"
            class="chip"
            @click="fill(account)"
          >
            {{ account.label }}
          </button>
        </div>
      </div>
    </form>
  </div>
</template>
```

**为什么这么写**

- `App.vue` 挂载时用 `/auth/me` 换一次最新身份。localStorage 里的角色可能是旧的（管理员刚改过），以接口返回的为准。
- `loggedIn` 同时判断令牌和当前路由，登录页不显示顶栏。
- 登录失败时把原因显示在表单上方。密码错、账号停用这类是业务失败，后端返回 200 + 非 200 的 code，拦截器已经弹过一次提示，表单上再留一条便于对照 30001 与 30002。
- `route.query.redirect` 的类型是 `string | string[] | null`，直接传给 router 会报类型错，先收敛成 string。
- 样式用 CSS 变量集中管理颜色，401 与 403 的提示色、状态标签都从这里取，改主题只改一处。
- 登录页把演示账号写成数组渲染，加账号只加一行数据，不用复制四段 HTML。

**验证**

```bash
cd 09-security/web && npm run dev
```

预期：打开 http://localhost:5173 能看到登录页与四个快捷账号按钮。注意地址要用 localhost，Vite 8 只监听 ::1。这一步先不要点登录，业务页面还没建，跳转会因模块加载失败报错。

## 步骤 19 · 三个业务页面

**这一步做什么**

学生页：左边提交表单，右边我的提交，顶部四格统计。教师页：全班列表、状态筛选、搜索、批改表单。账号页：新增账号、账号列表、角色筛选。

三个页面的统计都用 `computed` 从列表算，不加后端统计接口。

**文件**：`web/src/views/TaskList.vue`

```vue
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { myTasks, submitTask, taskDetail } from '../api'
import type { SubmitTaskPayload, TaskStatusCode, TaskView } from '../types/api'

type Filter = 'ALL' | TaskStatusCode

const tasks = ref<TaskView[]>([])
const form = reactive<SubmitTaskPayload>({ title: '', content: '' })
const submitting = ref(false)
const detail = ref<TaskView | null>(null)
const filter = ref<Filter>('ALL')

/**
 * 统计都在前端算。列表接口本来就返回全量，再单独做一个统计接口没有意义；
 * 等真上了分页，统计就得换后端聚合，这里写注释是提醒这一点。
 */
const stats = computed(() => {
  const reviewed = tasks.value.filter((task) => task.status === 'REVIEWED')
  const total = reviewed.reduce((acc, task) => acc + (task.score ?? 0), 0)
  return {
    total: tasks.value.length,
    pending: tasks.value.filter((task) => task.status === 'SUBMITTED').length,
    reviewed: reviewed.length,
    average: reviewed.length ? (total / reviewed.length).toFixed(1) : '—',
  }
})

const visible = computed(() =>
  filter.value === 'ALL' ? tasks.value : tasks.value.filter((task) => task.status === filter.value),
)

async function load(): Promise<void> {
  tasks.value = await myTasks()
}

onMounted(load)

async function onSubmit(): Promise<void> {
  if (!form.title || !form.content) return
  submitting.value = true
  try {
    await submitTask({ title: form.title, content: form.content })
    form.title = ''
    form.content = ''
    await load()
  } finally {
    submitting.value = false
  }
}

async function openDetail(task: TaskView): Promise<void> {
  detail.value = await taskDetail(task.id)
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : ''
}
</script>

<template>
  <div class="container">
    <div class="stats">
      <div class="stat">
        <div class="k">已提交</div>
        <div class="v">{{ stats.total }}<small>份</small></div>
      </div>
      <div class="stat amber">
        <div class="k">待批改</div>
        <div class="v">{{ stats.pending }}<small>份</small></div>
      </div>
      <div class="stat green">
        <div class="k">已批改</div>
        <div class="v">{{ stats.reviewed }}<small>份</small></div>
      </div>
      <div class="stat">
        <div class="k">平均分</div>
        <div class="v">{{ stats.average }}<small v-if="stats.reviewed">分</small></div>
      </div>
    </div>

    <div class="grid">
      <div>
        <div class="card">
          <h2>提交实训任务</h2>
          <p class="hint">提交人由后端按令牌写入，请求里没有用户名字段</p>
          <form @submit.prevent="onSubmit">
            <div class="field">
              <label for="title">任务标题</label>
              <input id="title" v-model.trim="form.title" placeholder="实训六：分页查询与条件筛选" />
            </div>
            <div class="field">
              <label for="content">完成情况</label>
              <textarea id="content" v-model.trim="form.content" placeholder="做了哪些、卡在哪里"></textarea>
            </div>
            <button class="btn btn-primary" type="submit" :disabled="submitting">
              {{ submitting ? '提交中…' : '提交' }}
            </button>
          </form>
        </div>
      </div>

      <div>
        <div class="card">
          <h2>我的提交</h2>
          <p class="hint">接口是 /api/tasks/mine，URL 里没有用户名，改也改不出别人的数据</p>

          <div class="toolbar">
            <div class="tabs">
              <button :class="{ on: filter === 'ALL' }" @click="filter = 'ALL'">
                全部<span class="n">{{ stats.total }}</span>
              </button>
              <button :class="{ on: filter === 'SUBMITTED' }" @click="filter = 'SUBMITTED'">
                待批改<span class="n">{{ stats.pending }}</span>
              </button>
              <button :class="{ on: filter === 'REVIEWED' }" @click="filter = 'REVIEWED'">
                已批改<span class="n">{{ stats.reviewed }}</span>
              </button>
            </div>
            <span class="count">共 {{ visible.length }} 条</span>
          </div>

          <div v-if="!visible.length" class="empty">
            {{ tasks.length ? '这个状态下没有记录' : '还没有提交过任务' }}
          </div>
          <table v-else>
            <thead>
              <tr>
                <th>标题</th>
                <th style="width: 90px">状态</th>
                <th style="width: 64px">分数</th>
                <th style="width: 150px">提交时间</th>
                <th style="width: 60px"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in visible" :key="task.id">
                <td>
                  {{ task.title }}
                  <div v-if="task.comment" class="muted clip" style="font-size: 12.5px">
                    评语：{{ task.comment }}
                  </div>
                </td>
                <td>
                  <span class="tag" :class="task.status === 'REVIEWED' ? 'tag-done' : 'tag-wait'">
                    {{ task.statusText }}
                  </span>
                </td>
                <td class="score">{{ task.score ?? '—' }}</td>
                <td class="mono">{{ formatTime(task.submittedAt) }}</td>
                <td><button class="btn btn-sm" @click="openDetail(task)">查看</button></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="detail" class="card">
          <h2>{{ detail.title }}</h2>
          <p class="hint">
            {{ detail.ownerNickname }} · 提交于 {{ formatTime(detail.submittedAt) }}
            <template v-if="detail.reviewedAt"> · 批改于 {{ formatTime(detail.reviewedAt) }}</template>
          </p>
          <p>{{ detail.content }}</p>
          <div v-if="detail.status === 'REVIEWED'" class="sub-card">
            <div><b>得分</b> <span class="score">{{ detail.score }}</span></div>
            <div><b>评语</b> {{ detail.comment || '教师未填写评语' }}</div>
          </div>
          <p v-else class="muted">教师还没批改</p>
          <button class="btn btn-sm" @click="detail = null">收起</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sub-card {
  border: 1px solid var(--green-border);
  background: var(--green-soft);
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 12px;
  font-size: 13.5px;
}
.sub-card div + div {
  margin-top: 4px;
}
</style>
```

**文件**：`web/src/views/ReviewList.vue`

```vue
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { allTasks, reviewTask } from '../api'
import { notify } from '../store/toast'
import type { ReviewTaskPayload, TaskStatusCode, TaskView } from '../types/api'

type Filter = 'ALL' | TaskStatusCode

const tasks = ref<TaskView[]>([])
const editing = ref<TaskView | null>(null)
const form = reactive<ReviewTaskPayload>({ score: 90, comment: '' })
const saving = ref(false)
const filter = ref<Filter>('ALL')
const keyword = ref('')

const stats = computed(() => {
  const reviewed = tasks.value.filter((task) => task.status === 'REVIEWED')
  const total = reviewed.reduce((acc, task) => acc + (task.score ?? 0), 0)
  return {
    total: tasks.value.length,
    pending: tasks.value.filter((task) => task.status === 'SUBMITTED').length,
    reviewed: reviewed.length,
    average: reviewed.length ? (total / reviewed.length).toFixed(1) : '—',
    students: new Set(tasks.value.map((task) => task.owner)).size,
  }
})

/** 筛选和搜索都在本地做，列表接口一次给全量；接分页时这两步要改成后端参数 */
const visible = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return tasks.value.filter((task) => {
    if (filter.value !== 'ALL' && task.status !== filter.value) return false
    if (!kw) return true
    return [task.title, task.owner, task.ownerNickname].some((text) => text.toLowerCase().includes(kw))
  })
})

async function load(): Promise<void> {
  tasks.value = await allTasks()
}

onMounted(load)

function startReview(task: TaskView): void {
  editing.value = task
  form.score = task.score ?? 90
  form.comment = task.comment ?? ''
}

async function onSubmit(): Promise<void> {
  if (!editing.value) return
  saving.value = true
  try {
    await reviewTask(editing.value.id, { score: Number(form.score), comment: form.comment })
    notify('批改已保存，学生那边立刻能看到')
    editing.value = null
    await load()
  } finally {
    saving.value = false
  }
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : ''
}
</script>

<template>
  <div class="container">
    <div class="stats">
      <div class="stat">
        <div class="k">全部提交</div>
        <div class="v">{{ stats.total }}<small>份</small></div>
      </div>
      <div class="stat amber">
        <div class="k">待批改</div>
        <div class="v">{{ stats.pending }}<small>份</small></div>
      </div>
      <div class="stat green">
        <div class="k">已批改</div>
        <div class="v">{{ stats.reviewed }}<small>份</small></div>
      </div>
      <div class="stat">
        <div class="k">平均分 · {{ stats.students }} 人提交</div>
        <div class="v">{{ stats.average }}<small v-if="stats.reviewed">分</small></div>
      </div>
    </div>

    <div class="card">
      <h2>全部提交</h2>
      <p class="hint">接口是 /api/tasks，需要 task:review 权限点。学生调这个接口会拿到 403</p>

      <div class="toolbar">
        <div class="tabs">
          <button :class="{ on: filter === 'ALL' }" @click="filter = 'ALL'">
            全部<span class="n">{{ stats.total }}</span>
          </button>
          <button :class="{ on: filter === 'SUBMITTED' }" @click="filter = 'SUBMITTED'">
            待批改<span class="n">{{ stats.pending }}</span>
          </button>
          <button :class="{ on: filter === 'REVIEWED' }" @click="filter = 'REVIEWED'">
            已批改<span class="n">{{ stats.reviewed }}</span>
          </button>
        </div>
        <input v-model="keyword" class="search" placeholder="按姓名、账号或标题搜" />
        <span class="count">筛出 {{ visible.length }} 条</span>
      </div>

      <div v-if="!visible.length" class="empty">
        {{ tasks.length ? '没有符合条件的记录' : '还没有人提交' }}
      </div>
      <table v-else>
        <thead>
          <tr>
            <th style="width: 46px">#</th>
            <th>标题</th>
            <th style="width: 96px">提交人</th>
            <th style="width: 90px">状态</th>
            <th style="width: 64px">分数</th>
            <th style="width: 150px">提交时间</th>
            <th style="width: 76px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in visible" :key="task.id">
            <td class="mono">{{ task.id }}</td>
            <td>
              {{ task.title }}
              <div v-if="task.comment" class="muted clip" style="font-size: 12.5px">评语：{{ task.comment }}</div>
            </td>
            <td>
              {{ task.ownerNickname }}
              <div class="muted mono" style="font-size: 12px">{{ task.owner }}</div>
            </td>
            <td>
              <span class="tag" :class="task.status === 'REVIEWED' ? 'tag-done' : 'tag-wait'">
                {{ task.statusText }}
              </span>
            </td>
            <td class="score">{{ task.score ?? '—' }}</td>
            <td class="mono">{{ formatTime(task.submittedAt) }}</td>
            <td>
              <button v-if="task.status !== 'REVIEWED'" class="btn btn-sm" @click="startReview(task)">
                批改
              </button>
              <span v-else class="muted" style="font-size: 12.5px">已批</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="editing" class="card">
      <h2>批改：{{ editing.title }}</h2>
      <p class="hint">
        {{ editing.ownerNickname }}（{{ editing.owner }}）· 提交于 {{ formatTime(editing.submittedAt) }}
        · 同一份不能批改两次，后端会返回业务码 30012
      </p>
      <p class="submitted">{{ editing.content }}</p>
      <form @submit.prevent="onSubmit">
        <div class="field" style="max-width: 160px">
          <label for="score">分数（0 到 100）</label>
          <input id="score" v-model.number="form.score" type="number" min="0" max="100" />
        </div>
        <div class="field">
          <label for="comment">评语</label>
          <textarea id="comment" v-model.trim="form.comment" placeholder="写清哪里对、哪里要改"></textarea>
        </div>
        <button class="btn btn-primary" type="submit" :disabled="saving">
          {{ saving ? '保存中…' : '保存批改' }}
        </button>
        <button class="btn" type="button" style="margin-left: 8px" @click="editing = null">取消</button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.submitted {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 13.5px;
  color: var(--text-2);
  margin: 0 0 14px;
}
</style>
```

**文件**：`web/src/views/Users.vue`

```vue
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createUser, deleteUser, listUsers } from '../api'
import { notify } from '../store/toast'
import type { CreateUserPayload, RoleCode, UserProfile } from '../types/api'

/** 表单只让选一个角色，提交时再包成数组，所以不用 CreateUserPayload 的 roles 字段 */
interface UserForm {
  username: string
  password: string
  nickname: string
  role: RoleCode
}

type Filter = 'ALL' | RoleCode

const users = ref<UserProfile[]>([])
const form = reactive<UserForm>({
  username: '',
  password: 'Student#2026',
  nickname: '',
  role: 'STUDENT',
})
const creating = ref(false)
const filter = ref<Filter>('ALL')
const keyword = ref('')

const stats = computed(() => ({
  total: users.value.length,
  students: users.value.filter((user) => user.roles.includes('STUDENT')).length,
  teachers: users.value.filter((user) => user.roles.includes('TEACHER')).length,
  disabled: users.value.filter((user) => !user.enabled).length,
}))

const visible = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return users.value.filter((user) => {
    if (filter.value !== 'ALL' && !user.roles.includes(filter.value)) return false
    if (!kw) return true
    return [user.username, user.nickname].some((text) => text.toLowerCase().includes(kw))
  })
})

async function load(): Promise<void> {
  users.value = await listUsers()
}

onMounted(load)

async function onCreate(): Promise<void> {
  creating.value = true
  try {
    const payload: CreateUserPayload = {
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      roles: [form.role],
    }
    await createUser(payload)
    notify('账号已创建')
    form.username = ''
    form.nickname = ''
    form.password = 'Student#2026'
    form.role = 'STUDENT'
    await load()
  } finally {
    creating.value = false
  }
}

async function onDelete(user: UserProfile): Promise<void> {
  if (!window.confirm(`确定删除 ${user.username}？`)) return
  await deleteUser(user.username)
  notify('已删除')
  await load()
}
</script>

<template>
  <div class="container">
    <div class="stats">
      <div class="stat">
        <div class="k">账号总数</div>
        <div class="v">{{ stats.total }}<small>个</small></div>
      </div>
      <div class="stat">
        <div class="k">学生</div>
        <div class="v">{{ stats.students }}<small>个</small></div>
      </div>
      <div class="stat">
        <div class="k">教师</div>
        <div class="v">{{ stats.teachers }}<small>个</small></div>
      </div>
      <div class="stat amber">
        <div class="k">停用</div>
        <div class="v">{{ stats.disabled }}<small>个</small></div>
      </div>
    </div>

    <div class="grid">
      <div class="card">
        <h2>新增账号</h2>
        <p class="hint">只有 ADMIN 能调，教师访问 /admin/users 会拿到 403</p>
        <form @submit.prevent="onCreate">
          <div class="field">
            <label for="username">用户名</label>
            <input id="username" v-model.trim="form.username" placeholder="student11" />
          </div>
          <div class="field">
            <label for="nickname">姓名</label>
            <input id="nickname" v-model.trim="form.nickname" placeholder="马嘉懿" />
          </div>
          <div class="field">
            <label for="password">初始密码</label>
            <input id="password" v-model.trim="form.password" />
          </div>
          <div class="field">
            <label for="role">角色</label>
            <select id="role" v-model="form.role">
              <option value="STUDENT">学生 STUDENT</option>
              <option value="TEACHER">教师 TEACHER</option>
              <option value="ADMIN">管理员 ADMIN</option>
            </select>
          </div>
          <button class="btn btn-primary" type="submit" :disabled="creating">
            {{ creating ? '创建中…' : '创建' }}
          </button>
        </form>
      </div>

      <div class="card">
        <h2>账号列表</h2>
        <p class="hint">响应里没有密码字段，出参对象压根不带它</p>

        <div class="toolbar">
          <div class="tabs">
            <button :class="{ on: filter === 'ALL' }" @click="filter = 'ALL'">
              全部<span class="n">{{ stats.total }}</span>
            </button>
            <button :class="{ on: filter === 'STUDENT' }" @click="filter = 'STUDENT'">
              学生<span class="n">{{ stats.students }}</span>
            </button>
            <button :class="{ on: filter === 'TEACHER' }" @click="filter = 'TEACHER'">
              教师<span class="n">{{ stats.teachers }}</span>
            </button>
            <button :class="{ on: filter === 'ADMIN' }" @click="filter = 'ADMIN'">管理员</button>
          </div>
          <input v-model="keyword" class="search" placeholder="按用户名或姓名搜" />
          <span class="count">筛出 {{ visible.length }} 个</span>
        </div>

        <div v-if="!visible.length" class="empty">没有符合条件的账号</div>
        <table v-else>
          <thead>
            <tr>
              <th>用户名</th>
              <th>姓名</th>
              <th>角色</th>
              <th>状态</th>
              <th style="width: 64px"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in visible" :key="user.username">
              <td class="mono">{{ user.username }}</td>
              <td>{{ user.nickname }}</td>
              <td>{{ user.roles.join(' / ') }}</td>
              <td>
                <span class="tag" :class="user.enabled ? 'tag-done' : 'tag-wait'">
                  {{ user.enabled ? '启用' : '停用' }}
                </span>
              </td>
              <td><button class="btn btn-sm btn-danger" @click="onDelete(user)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
select {
  width: 100%;
  padding: 8px 11px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  color: var(--text);
  font-family: inherit;
  font-size: inherit;
  outline: none;
}
select:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(47, 92, 246, 0.1);
}
</style>
```

**为什么这么写**

- 统计在前端算，是因为列表本来就返回全量，切筛选不用重新请求，也不会闪。数据量在几百条以内这么写最省事。
- 列表一旦分页，统计必须挪到后端：平均分不能拿当前页算，第 2 页的数据也不在手里。代码里留了这条注释。
- 学生页的提交表单只有标题和内容两个字段，没有“提交人”。这是把后端的规矩在界面上也体现出来。
- 教师页批改时把学生提交的原文显示出来。不显示原文让人凭标题打分，等于闭着眼睛批改。
- 可空字段（`score`、`comment`）在模板里用 `?? '—'` 与 `v-if` 显式处理。TypeScript 的 `strict` 会逼着写这些判断。
- 删除账号前 `window.confirm` 确认。这个模块没做二次确认组件，用浏览器原生的够用，也不引依赖。

**验证**

```bash
cd 09-security/web && npm run typecheck
```

预期：无输出，exit 0。这一步所有文件齐了，类型检查应该全过。

## 步骤 20 · 构建、启动、联调验收

**这一步做什么**

两个终端，两个命令。先后端 8009，再前端 5173。

**为什么这么写**

- 先起后端再起前端。反过来的话，前端首页一加载就发 `/auth/me`，拿到连接失败，提示“连不上后端”，容易被误判成代码问题。
- `npm run build` 会先跑类型检查再打包，所以它同时是“能不能上线”和“类型对不对”两个检查。
- 用 curl 走 5173 的代理，而不是直接打 8009。这样验的是浏览器真实走的路径，代理少配一条立刻能发现。

**验证**

```bash
# 终端一：后端 8009
mvn -o -pl 09-security spring-boot:run

# 终端二：前端 5173（先确认类型与打包都过）
cd 09-security/web
npm run build
npm run dev

# 终端三：走代理验证，验的是浏览器真实走的路径
curl -s -X POST http://localhost:5173/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"student01","password":"Student#2026"}' | head -c 200
```

预期：`npm run build` 打出 dist 产物且无类型错误；登录请求返回 `code:200` 与令牌。如果返回的是 HTML（`<!DOCTYPE html>`），说明 `/auth` 代理没配或后端没起。


## 验收清单

后端跑起来后，按顺序执行一遍。每一条都对应前面某一步的验证，全部对上就说明搭完了。

| # | 请求 | 预期 |
| --- | --- | --- |
| 1 | `GET /public/ping` 不带令牌 | 200，`code:200` |
| 2 | `GET /auth/me` 不带令牌 | 401，`code:401` |
| 3 | `POST /auth/login` 学生账号 | 200，返回令牌、昵称、角色 |
| 4 | `POST /auth/login` 错密码 | 200，`code:30001` |
| 5 | `POST /auth/login` 停用账号 | 200，`code:30002` |
| 6 | `GET /api/tasks/mine` 带学生令牌 | 200，只有自己的记录 |
| 7 | `GET /api/tasks` 带学生令牌 | 403 |
| 8 | `GET /api/tasks/2` 带学生令牌 | 403，id=2 不是他的 |
| 9 | `GET /api/tasks` 带教师令牌 | 200，28 条 |
| 10 | `POST /api/tasks/1/review` 带教师令牌 | 200，`status:REVIEWED` |
| 11 | 同上，再批一次 | 200，`code:30012` |
| 12 | `GET /admin/users` 带教师令牌 | 403 |
| 13 | `GET /admin/users` 带管理员令牌 | 200，14 个账号，无密码字段 |
| 14 | 篡改令牌最后三位再访问 | 401 |

页面上验三件事：学生登录只看得到自己的 3 条；教师批改后学生刷新立刻看到分数；学生手动输入 `/users` 会回到自己的首页，不是跳登录页。

## 搭建期最容易卡住的十处

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动报 `Table 'sec_user' doesn't exist` | `spring.sql.init.mode` 没写 `always` | MySQL 不是内嵌库，必须显式声明；再确认脚本在 classpath 根下 |
| 所有接口 401，连登录也 401 | 登录路径没放行，或 `anyRequest` 写在 `permitAll` 前面 | 规则从具体到宽泛排，`anyRequest` 必须最后 |
| 未认证返回 302 | `formLogin` 没关 | `formLogin(AbstractHttpConfigurer::disable)` |
| 一次请求跑了两遍过滤器 | 过滤器加了 `@Component` | 去掉注解，由 `SecurityConfig` 里 `new` |
| 令牌比预期长，算法是 HS384 | `signWith(key)` 单参数按密钥长度自动升级 | 改成 `signWith(key, Jwts.SIG.HS256)` |
| `Mapper` 注入失败 | 启动类漏了 `@MapperScan` | 加在 `SecurityApplication` 上 |
| 启动日志提示 UserDetailsService 不参与自动配置 | `DaoAuthenticationProvider` 暴露成了 Bean | 在 `AuthenticationManager` 里内部构造 |
| 管理页空白，学生页教师页正常 | Vite 代理漏了 `/admin`，请求打到 Vite 自己身上返回 HTML | 代理前缀与实际路径一一对齐 |
| `npm run typecheck` 报 axios 返回类型不对 | 拦截器改了运行时返回值，类型没跟上 | 在 `http` 工具里统一断言一次，页面里不再 `as` |
| `127.0.0.1:5173` 连不上 | Vite 8 默认只监听 `::1` | 用 `localhost` 访问 |

## 搭完之后可以改什么

三个练习，难度递增，题面与评分点见 `docs/09-security.md` 的课堂练习与课后作业：

1. **按钮级权限**：教师页的“批改”按钮只对有 `task:review` 的人显示。现在只有路由级控制，进入页面后按钮对所有人都渲染。
2. **任务列表分页**：引入 `PaginationInnerInterceptor`，把 `GET /api/tasks` 改成 `page`、`size` 参数，前端加分页条。统计要一起挪到后端，否则平均分算的是当前页。
3. **刷新令牌与登出黑名单**：改密码或点退出后旧令牌立刻失效。令牌版本号存用户表，或者维护 `jti` 黑名单。

## 文件清单

搭完核对一下，后端 39 个 Java 文件、前端 14 个源文件：

```text
09-security/
├── pom.xml
├── src/main/resources/{application.yml,schema.sql,data.sql}
├── src/main/java/top/mqxu/security/
│   ├── SecurityApplication.java
│   ├── common/{ErrorCode,Result,BusinessException,GlobalExceptionHandler}.java
│   ├── config/{SecurityConfig,JwtProperties}.java
│   ├── controller/{AuthController,PublicController,TaskController,UserAdminController}.java
│   ├── dto/{LoginRequest,SubmitTaskRequest,ReviewTaskRequest,CreateUserRequest}.java
│   ├── entity/{User,Task}.java
│   ├── mapper/{UserMapper,TaskMapper,RoleMapper,UserRoleMapper}.java
│   ├── model/{Role,Account,TaskStatus,TaskRow,UserRoleEntry}.java
│   ├── security/{SecurityUser,JwtTokenService,JwtAuthenticationFilter,
│   │             RestErrorWriter,RestAuthenticationEntryPoint,RestAccessDeniedHandler}.java
│   ├── service/{AuthService,UserService,TaskService,AccountUserDetailsService}.java
│   └── vo/{LoginResponse,UserProfile,TaskView}.java
├── src/test/java/top/mqxu/security/SecurityApiTest.java
└── web/
    ├── {package.json,tsconfig.json,vite.config.ts,index.html}
    └── src/
        ├── {main.ts,App.vue,style.css,env.d.ts}
        ├── api/{request.ts,index.ts}
        ├── router/index.ts
        ├── store/{auth.ts,toast.ts}
        ├── types/api.ts
        └── views/{Login.vue,TaskList.vue,ReviewList.vue,Users.vue}
```
