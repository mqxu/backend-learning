# 09-security · 认证与授权

> 📖 详细教程：[docs/09-security.md](../docs/09-security.md)
> 🖥 投影版讲义：[lecture.html](lecture.html)，浏览器直接打开

> 状态：✅ 后端 22 个接口测试通过（真实 MySQL），前端 TypeScript 工程 `npm run typecheck` 通过

案例是**实训任务提交与批改**：学生交任务，教师批改，管理员管账号。后端 Spring Security + JWT + MyBatis-Plus + MySQL，前端 Vue 3 + TypeScript + axios，两个工程都放在本目录下，起起来就能联调。

## 三条要成立的规则

| 角色 | 能做的事 | 不能做的事 |
| --- | --- | --- |
| 学生 STUDENT | 提交任务、看自己的提交与分数 | 看别人的、批改 |
| 教师 TEACHER | 看全部提交、打分写评语 | 提交任务、进管理接口 |
| 管理员 ADMIN | 全部提交、账号增删查 | — |

演示账号（密码在 data.sql 里存 BCrypt 密文）：

| 账号 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| admin | Admin#2026 | ADMIN | 全部提交 + 账号管理 |
| teacher01 | Teacher#2026 | TEACHER | 看全部、能批改 |
| student01 | Student#2026 | STUDENT | 预置一条待批改任务 |
| student02 | Student#2026 | STUDENT | 预置一条已批改任务，用于演示越权 |
| guest | Guest#2026 | STUDENT | 停用账号，登录返回 30002 |

## 数据库

与 05-mybatis 共用 `backend_learning` 库，表统一加 `sec_` 前缀，十个模块可以放在一个库里互不影响。

| 表 | 说明 |
| --- | --- |
| `sec_role` | 角色：STUDENT / TEACHER / ADMIN |
| `sec_user` | 账号，password 存 BCrypt 密文，enabled 控制停用 |
| `sec_user_role` | 用户与角色的关联，主键是 `(user_id, role_id)` |
| `sec_task` | 实训任务，归属用 `owner_id` 指向 `sec_user` |

`schema.sql` 每次启动先删后建，`data.sql` 灌演示数据（主键写死，测试引用任务 id 才稳定）。改表结构不用手工 ALTER，重启即可；真实项目该换 Flyway 做版本化迁移。

权限点留在 `Role` 枚举里而不是建表，理由：角色与用户的绑定是运行期数据，天天变；权限点是代码资产，跟着接口一起评审、一起提交，放枚举比放表更容易 review。要改成配置化时，加 `sec_permission` 与 `sec_role_permission` 两张表即可，`Role.authorities()` 换成查库。

## 接口清单

| 方法 | 路径 | 访问要求 | 说明 |
| --- | --- | --- | --- |
| POST | `/auth/login` | 放行 | 换令牌，返回用户名、昵称、角色 |
| GET | `/auth/me` | 已认证 | 刷新页面后恢复身份 |
| POST | `/api/tasks` | `task:submit` | 学生提交，提交人从令牌取 |
| GET | `/api/tasks/mine` | 已认证 | 只返回自己的，URL 里没有用户名 |
| GET | `/api/tasks/{id}` | 本人或 `task:review` | 越权返回 403 |
| GET | `/api/tasks` | `task:review` | 教师看全部提交 |
| POST | `/api/tasks/{id}/review` | `task:review` | 批改打分，同一份只能批一次 |
| GET | `/admin/users` | ADMIN | 账号列表，响应不含密码 |
| POST | `/admin/users` | ADMIN + `user:write` | 新增账号 |
| DELETE | `/admin/users/{username}` | ADMIN | 删除账号，禁止删自己 |
| GET | `/public/ping` | 放行 | 对比放行效果 |

失败响应统一走 `Result`：业务失败 HTTP 200 + `code`，越权返回真实的 403，未认证返回真实的 401。

## 怎么跑

先确认 MySQL 已启动，`application.yml` 里的数据源与 05-mybatis 保持一致（`root` / 见配置文件），库 `backend_learning` 会自动建。

两个终端。

```bash
# 后端 8009，启动时自动建表灌数据
mvn -pl 09-security spring-boot:run

# 前端 5173
cd 09-security/web && npm install && npm run dev
```

浏览器打开 `http://localhost:5173`，页面上有快捷按钮切换账号。Vite 8 只监听 `::1`，用 `127.0.0.1:5173` 会连不上。

前端代理配了 `/api`、`/auth`、`/admin` 三个前缀。少配一条，请求会打到 Vite 自己身上返回 HTML，页面看着像“接口没数据”。

## RBAC 模型

```java
public enum Role {
    STUDENT(Set.of("task:submit", "task:read")),
    TEACHER(Set.of("task:review", "task:read")),
    ADMIN(Set.of("user:read", "user:write", "task:read", "task:review"));
}
```

接口绑权限点而不是绑角色：将来多一个“教务”，能看全部但不能批改，配 `task:read` 就行，接口代码不用动。

`hasRole("ADMIN")` 比对的是 `ROLE_ADMIN`，前缀由 Security 补；`hasAuthority("task:review")` 原样比对。两者混用是初学者最常见的 403 原因。

## 认证流程

1. 前端提交用户名密码到 `/auth/login`；
2. `AuthenticationManager` 通过 `DaoAuthenticationProvider` 调 `UserDetailsService`，后者用 MyBatis-Plus 查 `sec_user` 与 `sec_user_role`，比对 BCrypt 密文、检查账号是否停用；
3. 校验通过后 `JwtTokenService` 签发 HS256 令牌，写入 `sub`、`iss`、`iat`、`exp` 与展示用的 `roles`；
4. 前端把令牌存 localStorage，此后每个请求由 axios 请求拦截器加 `Authorization: Bearer <token>`；
5. `JwtAuthenticationFilter` 验签后按用户名回库查账号，把 `SecurityUser` 放进 `SecurityContext`；
6. 授权判断读上下文：URL 规则、权限点，拒绝时给 403；令牌无效由 `RestAuthenticationEntryPoint` 给 401；
7. 前端响应拦截器分开处理：401 清登录态跳登录页并记住来处，403 只提示。

令牌只放身份，不放权限。权限每次请求按用户名回查，改角色立刻生效；代价是每个请求多两条 SQL，换缓存可以缓解。

## 分层

```
controller  收参数、校验、调 service，不写业务判断
service     业务规则：归属判断、状态流转、事务边界
mapper      MyBatis-Plus BaseMapper 做单表 CRUD，联表用注解 SQL
entity      表映射，不出 service
model       领域对象与投影：Account、Role、TaskStatus、TaskRow
dto / vo    入参校验对象 / 接口出参白名单
```

几处刻意的选择：

- **实体不出 service**：`User` 只在 mapper 与 service 之间用，接口出参是 `UserProfile`，password 不可能顺着实体漏出去。
- **Account 隔一层**：`UserDetailsService` 需要“用户 + 角色”，实体只有一张表，用 `Account` 把两者合起来，`SecurityUser` 就不必知道表结构。
- **联表不硬套 Wrapper**：`TaskMapper` 里三个 `@Select` 写清 join，比用条件构造器拼子查询好读。
- **归属判断放 service**：`/api/tasks/{id}` 的越权判断要看这一行数据，放 Controller 会把业务规则散到接口层。

## 目录结构

```
09-security
├── src/main/java/top/mqxu/security
│   ├── SecurityApplication.java      启动类，@MapperScan 扫描 mapper 包
│   ├── common/       Result、ErrorCode、BusinessException、GlobalExceptionHandler
│   ├── config/       SecurityConfig、JwtProperties
│   ├── controller/   AuthController、TaskController、UserAdminController、PublicController
│   ├── dto/          LoginRequest、SubmitTaskRequest、ReviewTaskRequest、CreateUserRequest
│   ├── entity/       User（sec_user）、Task（sec_task）
│   ├── mapper/       UserMapper、TaskMapper、RoleMapper、UserRoleMapper
│   ├── model/        Account、Role、TaskStatus、TaskRow、UserRoleEntry
│   ├── security/     JwtTokenService、JwtAuthenticationFilter、SecurityUser、
│   │                 RestErrorWriter、RestAuthenticationEntryPoint、RestAccessDeniedHandler
│   ├── service/      AuthService、UserService、TaskService、AccountUserDetailsService
│   └── vo/           LoginResponse、UserProfile、TaskView
├── src/main/resources
│   ├── application.yml    数据源、MyBatis-Plus、sql init、JWT 配置
│   ├── schema.sql         建表
│   └── data.sql           演示数据（密码为 BCrypt 密文）
├── src/test/java/.../SecurityApiTest.java    22 个用例
├── lecture.html           投影版讲义
└── web/                   前端 TypeScript 工程
    ├── src/types/api.ts   与后端一一对应的类型
    ├── src/api/request.ts axios 实例与三个拦截逻辑
    ├── src/api/index.ts   接口清单，每个方法都有返回类型
    ├── src/store/         auth.ts（令牌读写）、toast.ts
    ├── src/router/        index.ts（路由与守卫，meta 有类型）
    ├── src/views/         Login、TaskList、ReviewList、Users
    ├── src/App.vue        顶栏菜单按角色显示
    ├── tsconfig.json      strict 模式
    └── vite.config.ts     代理配置
```

## 依赖

后端：

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| `spring-boot-starter-security` | 父 POM 管理（Security 7.1.1） | 过滤链、BCrypt、方法级鉴权 |
| `spring-boot-starter-validation` | 父 POM 管理 | 入参校验 |
| `mybatis-plus-spring-boot4-starter` | 3.5.17 | 通用 CRUD、Lambda 条件构造器 |
| `mysql-connector-j` | 父 POM 管理 | MySQL 驱动 |
| `io.jsonwebtoken:jjwt-api` | 0.13.0 | 编译期用的 JWT API |
| `io.jsonwebtoken:jjwt-impl` | 0.13.0，runtime | 运行期实现 |
| `io.jsonwebtoken:jjwt-jackson` | 0.13.0，runtime | 令牌载荷的 JSON 序列化 |

jjwt 不在 Spring Boot 依赖管理范围内，版本要写在模块 POM。`jjwt-jackson` 依赖 Jackson 2，而 Boot 4 的 HTTP 序列化用 Jackson 3，两套共存互不干扰。

前端：

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| `vue` | 3.5.44 | 页面 |
| `vue-router` | 4.6.4 | 路由与守卫 |
| `axios` | 1.21.0 | 请求与拦截器 |
| `typescript` | 5.9.3 | 类型检查 |
| `vue-tsc` | 3.3.13 | .vue 文件的类型检查 |
| `vite` | 8.3.5 | 开发服务器与代理 |
| `@vitejs/plugin-vue` | 6.0.10 | 编译 .vue |

刻意没引 UI 组件库，样式是手写的，让注意力留在鉴权链路上。

## 验证

```bash
# 1. 放行接口，200
curl -i http://127.0.0.1:8009/public/ping

# 2. 不带令牌，401
curl -i http://127.0.0.1:8009/api/tasks/mine

# 3. 学生登录拿令牌
curl -X POST http://127.0.0.1:8009/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"student01","password":"Student#2026"}'

# 4. 看自己的提交，200
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8009/api/tasks/mine

# 5. 调教师接口，403
curl -i -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8009/api/tasks

# 6. 看别人的任务，403
curl -i -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8009/api/tasks/2

# 7. 停用账号登录，业务码 30002
curl -X POST http://127.0.0.1:8009/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"guest","password":"Guest#2026"}'
```

前端类型检查：

```bash
cd 09-security/web
npm run typecheck    # vue-tsc --noEmit
npm run build        # 先 typecheck 再打包
```

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.datasource.*` | 与 05-mybatis 同一库同一账号 | 密码可用环境变量覆盖 |
| `spring.sql.init.mode` | `always` | 每次启动重建表并灌数据 |
| `mybatis-plus.configuration.log-impl` | 标准输出 | 开发期打印 SQL，上线关掉 |
| `app.jwt.secret` | 内置演示串，可用 `JWT_SECRET` 覆盖 | HS256 要求不短于 32 字节，启动时校验 |
| `app.jwt.issuer` | `backend-learning` | 签发方，解析时用 `requireIssuer` 一并校验 |
| `app.jwt.expire-minutes` | `120` | 有效期，至少 1 分钟 |

## 实现要点

- **过滤器不注册成 Bean**：`JwtAuthenticationFilter` 由 `SecurityConfig` 内部 `new` 出来。加了 `@Component`，Spring Boot 会把它同时注册成 Servlet 过滤器，一次请求跑两遍。
- **令牌无效不提前写响应**：过滤器只清空 `SecurityContext` 然后放行，401 由 `RestAuthenticationEntryPoint` 统一输出，避免同一个错误两种格式。
- **两处写 JSON 的地方分开**：认证与授权失败发生在进 Controller 之前，`@RestControllerAdvice` 接不到，用 `RestErrorWriter` 在过滤器层拼；`@PreAuthorize` 拒绝发生在 Controller 调用中，由全局异常处理器接住。
- **越权给真实 403**：`GlobalExceptionHandler` 对 `FORBIDDEN` 返回 `ResponseEntity.status(403)`，其余业务失败仍是 200 + code。包成 200 会让前端把拒绝当成功。
- **归属只看令牌**：`/api/tasks/mine` 不带用户名参数，提交请求体里没有 owner 字段，详情接口在 Service 里比对 `owner_id`。
- **新增账号用事务**：写 `sec_user` 与 `sec_user_role` 两张表，`@Transactional(rollbackFor = Exception.class)` 保证任一步失败整体回滚。
- **登录失败不区分原因**：用户名不存在与密码错误都给 30001，避免被用来枚举账号；停用账号单独给 30002。
- **前端只在一处断言类型**：axios 拦截器把 `Result` 拆成 `data`，但类型仍认为是完整响应，所以在 `http` 工具里统一 `as T`，页面里全是实打实的类型。

## 已知边界

没有刷新令牌，有效期 120 分钟到期需重新登录；没有登出黑名单，退出只清前端存储，令牌到期前仍可用；没有登录失败次数限制。前端只做了菜单与路由级权限，没做按钮级控制。数据量小，没有分页。

## 避坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 全接口 401，登录也 401 | 登录路径没放行 | `permitAll` 且写在 `anyRequest` 之前 |
| 未认证返回 302 | `formLogin` 没关 | `formLogin(AbstractHttpConfigurer::disable)` |
| 401 响应是空白 HTML | 没配 entryPoint | 指定 `RestAuthenticationEntryPoint` |
| `/admin/users` 返回一坨 HTML | Vite 代理漏配 `/admin` | 代理前缀与实际路径一一对齐 |
| `hasRole("ADMIN")` 始终不通过 | 权限里是 `ADMIN` 而不是 `ROLE_ADMIN` | 用 `Role.authority()` 统一生成 |
| 带令牌仍 403 | 角色没拿到权限点 | `GET /auth/me` 看 `authorities` 实际内容 |
| WeakKeyException | 密钥短于 32 字节 | 换长密钥，`@Size` 启动时校验 |
| 令牌 Header 是 HS384 | 用了 `signWith(key)` 单参数版 | 显式 `signWith(key, Jwts.SIG.HS256)` |
| 过滤器跑两遍 | 既是 Bean 又被加进链 | 去掉 `@Component`，配置类里 `new` |
| 启动提示 UserDetailsService 不参与自动配置 | 容器里暴露了 `AuthenticationProvider` Bean | 在 `AuthenticationManager` 内部构造 provider |
| 方法级鉴权不生效 | 少 `@EnableMethodSecurity` | 加在配置类上 |
| 越权返回 200 而不是 403 | 处理器返回了裸 `Result` | 用 `ResponseEntity.status(403)` |
| 启动报 `Table 'sec_user' doesn't exist` | `spring.sql.init.mode` 没写 always | MySQL 不是内嵌库，必须显式声明 |
| 前端一点就跳登录 | 把 403 也当成 401 | 响应拦截器里分开两个分支 |
| 刷新页面掉登录 | 令牌只存内存变量 | 存 localStorage，初始化时读回来 |
| `npm run typecheck` 报 axios 返回值类型不对 | 拦截器改了运行时返回值，类型没跟上 | 在 `http` 工具里统一断言一次 |
