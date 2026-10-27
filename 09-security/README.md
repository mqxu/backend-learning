# 09-security · 认证与授权

> 📖 详细教程：[docs/09-security.md](../docs/09-security.md)
> 🖥 投影版讲义：[lecture.html](lecture.html)，浏览器直接打开

> 状态：✅ 后端 22 个接口测试通过，前端 4 个页面可联调

案例是**实训任务提交与批改**：学生交任务，教师批改，管理员管账号。后端用 Spring Security + JWT，前端用 Vue 3 + axios，两个工程都放在本目录下，起起来就能联调。

## 三条要成立的规则

| 角色 | 能做的事 | 不能做的事 |
| --- | --- | --- |
| 学生 STUDENT | 提交任务、看自己的提交与分数 | 看别人的、批改 |
| 教师 TEACHER | 看全部提交、打分写评语 | 提交任务、进管理接口 |
| 管理员 ADMIN | 全部提交、账号增删查 | — |

演示账号（密码用 BCrypt 加密后写死在种子数据里）：

| 账号 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| student01 | student123 | STUDENT | 预置一条待批改任务 |
| student02 | student123 | STUDENT | 预置一条已批改任务，用于演示越权 |
| teacher01 | teacher123 | TEACHER | 看全部、能批改 |
| admin | admin123 | ADMIN | 全部提交 + 账号管理 |
| guest | guest123 | STUDENT | 登录直接失败，账号已停用 |

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

两个终端。

```bash
# 后端 8009
mvn -pl 09-security spring-boot:run

# 前端 5173
cd 09-security/web && npm install && npm run dev
```

浏览器打开 `http://localhost:5173`，页面上有快捷按钮切换三个账号。开发期跨域由 Vite 代理转发（`vite.config.js` 里的 `/api` 与 `/auth`），上线分离部署时才需要后端那份 CORS 配置。

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
2. `AuthenticationManager` 通过 `DaoAuthenticationProvider` 调 `UserDetailsService` 取账号，比对 BCrypt 密文、检查账号是否停用；
3. 校验通过后 `JwtTokenService` 签发 HS256 令牌，写入 `sub`、`iss`、`iat`、`exp` 与展示用的 `roles`；
4. 前端把令牌存 localStorage，此后每个请求由 axios 请求拦截器加 `Authorization: Bearer <token>`；
5. `JwtAuthenticationFilter` 验签后按用户名回查账号，把 `SecurityUser` 放进 `SecurityContext`；
6. 授权判断读上下文：URL 规则、权限点，拒绝时给 403；令牌无效由 `RestAuthenticationEntryPoint` 给 401；
7. 前端响应拦截器分开处理：401 清登录态跳登录页并记住来处，403 只提示。

令牌只放身份，不放权限。权限每次请求按用户名回查，改角色立刻生效；代价是每个请求多一次查询。

## 目录结构

```
09-security
├── src/main/java/top/mqxu/security
│   ├── common/       Result、ErrorCode、BusinessException、GlobalExceptionHandler
│   ├── config/       SecurityConfig、JwtProperties
│   ├── controller/   AuthController、TaskController、UserAdminController、PublicController
│   ├── dto/          LoginRequest、SubmitTaskRequest、ReviewTaskRequest、CreateUserRequest
│   ├── model/        UserAccount、Role、Task、TaskStatus
│   ├── repository/   InMemoryUserRepository、TaskRepository
│   ├── security/     JwtTokenService、JwtAuthenticationFilter、SecurityUser、
│   │                 RestErrorWriter、RestAuthenticationEntryPoint、RestAccessDeniedHandler
│   ├── service/      AuthService、TaskService、AccountUserDetailsService、UserAdminService
│   └── vo/           LoginResponse、UserProfile、TaskView
├── src/main/resources/application.yml
├── src/test/java/top/mqxu/security/SecurityApiTest.java
├── lecture.html      投影版讲义
└── web/              前端联调 demo
    ├── src/api/      request.js（拦截器）、index.js（接口清单）
    ├── src/store/    auth.js（令牌读写）、toast.js
    ├── src/router/   index.js（路由与守卫）
    ├── src/views/    Login、TaskList、ReviewList、Users
    ├── src/App.vue   顶栏菜单按角色显示
    └── vite.config.js
```

## 依赖

后端：

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| `spring-boot-starter-security` | 父 POM 管理（Security 7.1.1） | 过滤链、BCrypt、方法级鉴权 |
| `spring-boot-starter-validation` | 父 POM 管理 | 入参校验 |
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
  -d '{"username":"student01","password":"student123"}'

# 4. 看自己的提交，200
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8009/api/tasks/mine

# 5. 调教师接口，403
curl -i -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8009/api/tasks

# 6. 看别人的任务，403
curl -i -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8009/api/tasks/2

# 7. 停用账号登录，业务码 30002
curl -X POST http://127.0.0.1:8009/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"guest","password":"guest123"}'
```

令牌解码后前两段是明文 JSON：

```
{"alg":"HS256"}
{"iss":"backend-learning","sub":"student01","iat":1793121428,"exp":1793128628,"roles":["STUDENT"]}
```

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `app.jwt.secret` | 内置演示串，可用环境变量 `JWT_SECRET` 覆盖 | HS256 要求不短于 32 字节，启动时校验 |
| `app.jwt.issuer` | `backend-learning` | 签发方，解析时用 `requireIssuer` 一并校验 |
| `app.jwt.expire-minutes` | `120` | 有效期，至少 1 分钟 |

## 实现要点

- **过滤器不注册成 Bean**：`JwtAuthenticationFilter` 由 `SecurityConfig` 内部 `new` 出来。加了 `@Component`，Spring Boot 会把它同时注册成 Servlet 过滤器，一次请求跑两遍。
- **令牌无效不提前写响应**：过滤器只清空 `SecurityContext` 然后放行，401 由 `RestAuthenticationEntryPoint` 统一输出，避免同一个错误两种格式。
- **两处写 JSON 的地方分开**：认证与授权失败发生在进 Controller 之前，`@RestControllerAdvice` 接不到，用 `RestErrorWriter` 在过滤器层拼；`@PreAuthorize` 拒绝发生在 Controller 调用中，由全局异常处理器接住。
- **越权给真实 403**：`GlobalExceptionHandler` 对 `FORBIDDEN` 返回 `ResponseEntity.status(403)`，其余业务失败仍是 200 + code。包成 200 会让前端把拒绝当成功。
- **归属只看令牌**：`/api/tasks/mine` 不带用户名参数，提交请求体里没有 owner 字段，详情接口在 Service 里比对 `ownerUsername`。
- **登录失败不区分原因**：用户名不存在与密码错误都给 30001，避免被用来枚举账号；停用账号单独给 30002。
- **CSRF 关闭有前提**：纯 Bearer 令牌、无 cookie 会话才能关；改回 session 认证必须重新打开。

## 已知边界

没有刷新令牌，有效期 120 分钟到期需重新登录；没有登出黑名单，退出只清前端存储，令牌到期前仍可用；用户与任务存内存，重启即丢。前端只做了菜单与路由级权限，没做按钮级控制。

## 避坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 全接口 401，登录也 401 | 登录路径没放行 | `permitAll` 且写在 `anyRequest` 之前 |
| 未认证返回 302 | `formLogin` 没关 | `formLogin(AbstractHttpConfigurer::disable)` |
| 401 响应是空白 HTML | 没配 entryPoint | 指定 `RestAuthenticationEntryPoint` |
| `hasRole("ADMIN")` 始终不通过 | 权限里是 `ADMIN` 而不是 `ROLE_ADMIN` | 用 `Role.authority()` 统一生成 |
| 带令牌仍 403 | 角色没拿到权限点 | `GET /auth/me` 看 `authorities` 实际内容 |
| WeakKeyException | 密钥短于 32 字节 | 换长密钥，`@Size` 启动时校验 |
| 令牌 Header 是 HS384 | 用了 `signWith(key)` 单参数版 | 显式 `signWith(key, Jwts.SIG.HS256)` |
| 过滤器跑两遍 | 既是 Bean 又被加进链 | 去掉 `@Component`，配置类里 `new` |
| 启动提示 UserDetailsService 不参与自动配置 | 容器里暴露了 `AuthenticationProvider` Bean | 在 `AuthenticationManager` 内部构造 provider |
| 方法级鉴权不生效 | 少 `@EnableMethodSecurity` | 加在配置类上 |
| 越权返回 200 而不是 403 | 处理器返回了裸 `Result` | 用 `ResponseEntity.status(403)` |
| 前端一点就跳登录 | 把 403 也当成 401 | 响应拦截器里分开两个分支 |
| 刷新页面掉登录 | 令牌只存内存变量 | 存 localStorage，初始化时读回来 |
| 请求打到 5173 返回 HTML | 代理前缀与实际路径不一致 | 对齐 `vite.config.js` 的 proxy |
