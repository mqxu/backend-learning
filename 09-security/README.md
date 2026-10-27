# 09-security · 认证与授权

> 📖 详细教程：[docs/09-security.md](../docs/09-security.md)

> 状态：✅ 核心案例实现完成，14 个接口测试通过

用 Spring Security + JWT 做登录签发、无状态鉴权和 RBAC 权限控制。用户与角色放在内存里，不依赖数据库和中间件，模块单独就能跑起来。

## 学习目标

- 用 `SecurityFilterChain` 组件式配置规则，分清 `permitAll`、`hasRole`、`authenticated` 各自的作用位置
- 用 BCrypt 存密码、用 `AuthenticationManager` 校验账号密码，不自己写密码比对
- 签发与解析 JWT，把令牌里的身份换成 Spring Security 的认证对象
- 分清 401 与 403 的两个来源：过滤器链上的 `EntryPoint` / `AccessDeniedHandler`，以及方法级 `@PreAuthorize`
- 授权规则按 URL、按角色、按权限点、按数据归属四种粒度落地

## 接口清单

| 方法 | 路径 | 访问要求 | 说明 |
| --- | --- | --- | --- |
| POST | `/auth/login` | 放行 | 登录，返回令牌与过期时间 |
| GET | `/auth/me` | 已认证 | 用令牌换当前用户信息，响应不含密码 |
| GET | `/public/ping` | 放行 | 开放接口，验证 `permitAll` 生效 |
| GET | `/security/hello` | 已认证 | 最小受保护接口，演示 `@AuthenticationPrincipal` |
| GET | `/security/authorities` | 已认证 | 打印当前令牌换到的权限集合 |
| GET | `/admin/users` | ADMIN | 用户列表 |
| POST | `/admin/users` | ADMIN + `user:write` | 新增用户，密码入库存密文 |
| DELETE | `/admin/users/{username}` | ADMIN | 删除用户，禁止删自己 |
| GET | `/api/reports` | `report:read` | 权限点级鉴权，两个角色都持有 |
| GET | `/api/reports/admin-summary` | ADMIN | 角色级鉴权 |
| GET | `/api/reports/{owner}` | 本人或 ADMIN | 数据归属级鉴权，SpEL 写 `#owner == authentication.name` |

失败响应统一走 `Result` 结构：未认证返回 401，权限不足返回 403，账号密码错误返回业务码 30001。

## 初始账号

| 用户名 | 密码 | 角色 | 状态 |
| --- | --- | --- | --- |
| admin | admin123 | ADMIN | 启用 |
| teacher | teacher123 | USER | 启用 |
| guest | guest123 | USER | 禁用，用于验证禁用账号登录 |

密码在仓库初始化时用 `BCryptPasswordEncoder` 加密，内存里不存明文。

## RBAC 模型

角色是粗粒度身份，权限点才是接口鉴权的依据。`Role` 枚举把两者写在一起：

| 角色 | 换到的权限 |
| --- | --- |
| ADMIN | `ROLE_ADMIN`、`user:read`、`user:write`、`report:read` |
| USER | `ROLE_USER`、`report:read` |

`hasRole("ADMIN")` 实际比对的是 `ROLE_ADMIN`，前缀由 Spring Security 补，配置里不要手写。

## 认证流程

1. 客户端提交用户名密码到 `/auth/login`；
2. `AuthenticationManager` 通过 `DaoAuthenticationProvider` 调用 `UserDetailsService` 取账号，比对 BCrypt 密文、检查账号是否禁用；
3. 校验通过后 `JwtTokenService` 签发 HMAC-SHA256 令牌，写入 `sub`、`iss`、`iat`、`exp` 与展示用的 `roles`；
4. 后续请求带 `Authorization: Bearer <token>`，`JwtAuthenticationFilter` 验签后按用户名回查账号，把 `SecurityUser` 放进 `SecurityContext`；
5. 授权判断读 `SecurityContext`：URL 规则、角色、权限点，哪一层拒绝就返回 401 或 403。

令牌里只放身份，不放权限。权限每次请求回库查，改角色能立刻生效；代价是每个请求多一次用户查询，换成数据库后可以加缓存。

## 目录结构

```
09-security
├── src/main/java/top/mqxu/security
│   ├── SecurityApplication.java
│   ├── common/       Result、ErrorCode、BusinessException、GlobalExceptionHandler
│   ├── config/       SecurityConfig、JwtProperties
│   ├── controller/   AuthController、PublicController、SecurityController、UserAdminController、ReportController
│   ├── dto/          LoginRequest、CreateUserRequest
│   ├── model/        UserAccount、Role
│   ├── repository/   InMemoryUserRepository
│   ├── security/     JwtTokenService、JwtAuthenticationFilter、SecurityUser、RestErrorWriter、RestAuthenticationEntryPoint、RestAccessDeniedHandler
│   ├── service/      AuthService、AccountUserDetailsService、UserAdminService
│   └── vo/           LoginResponse、UserProfile
├── src/main/resources/application.yml
└── src/test/java/top/mqxu/security/SecurityApiTest.java
```

## 依赖

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| `spring-boot-starter-security` | 由父 POM 管理（Security 7.1.1） | 过滤链、BCrypt、方法级鉴权 |
| `spring-boot-starter-validation` | 由父 POM 管理 | 登录与新增用户入参校验 |
| `io.jsonwebtoken:jjwt-api` | 0.13.0 | 编译期用到的 JWT API |
| `io.jsonwebtoken:jjwt-impl` | 0.13.0 | 运行期实现，`runtime` 作用域 |
| `io.jsonwebtoken:jjwt-jackson` | 0.13.0 | JWT 载荷的 JSON 序列化，`runtime` 作用域 |

jjwt 不在 Spring Boot 的依赖管理范围内，版本要自己写在模块 POM 里。`jjwt-jackson` 依赖的是 Jackson 2，而 Spring Boot 4 的 HTTP 序列化用的是 Jackson 3，两套 JSON 库共存但互不干扰：前者只被 jjwt 用来读写令牌载荷。

## 运行方式

```bash
mvn -pl 09-security spring-boot:run
```

端口：`8009`。

## 验证

```bash
# 1. 开放接口，返回 200
curl http://127.0.0.1:8009/public/ping

# 2. 不带令牌访问受保护接口，返回 401
curl -i http://127.0.0.1:8009/security/hello

# 3. 登录拿令牌
curl -X POST http://127.0.0.1:8009/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'

# 4. 带上令牌访问，返回 200
curl -H "Authorization: Bearer <上一步返回的 token>" http://127.0.0.1:8009/auth/me

# 5. 换普通用户令牌访问管理接口，返回 403
curl -i -H "Authorization: Bearer <teacher 的 token>" http://127.0.0.1:8009/admin/users
```

令牌解码后能看到三段内容，签名段随密钥变化，前两段是 Base64URL 编码的 JSON：

```
{"alg":"HS256"}
{"iss":"backend-learning","sub":"admin","iat":1793118442,"exp":1793125642,"roles":["ADMIN"]}
```

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `app.jwt.secret` | 内置演示串，可用环境变量 `JWT_SECRET` 覆盖 | HS256 要求不短于 32 字节，启动时校验 |
| `app.jwt.issuer` | `backend-learning` | 签发方，解析时用 `requireIssuer` 一并校验 |
| `app.jwt.expire-minutes` | `120` | 有效期，至少 1 分钟 |

`JwtProperties` 是 record 绑定，校验注解在启动时生效。密钥不合格会在启动阶段直接失败，而不是等签发第一个令牌才报错。

## 实现要点

- **过滤器不注册成 Bean**：`JwtAuthenticationFilter` 由 `SecurityConfig` 内部 `new` 出来。加了 `@Component`，Spring Boot 会把它同时注册成 Servlet 过滤器，一次请求会跑两遍。
- **令牌无效不提前写响应**：过滤器只清空 `SecurityContext` 然后放行，最终由 `RestAuthenticationEntryPoint` 统一返回 401，避免同一个错误出现两种响应格式。
- **两处写 JSON 的地方分开**：认证与授权失败发生在进 Controller 之前，`@RestControllerAdvice` 接不到，所以用 `RestErrorWriter` 在过滤器层拼响应；`@PreAuthorize` 拒绝发生在 Controller 调用中，由全局异常处理器接住并返回 403。
- **登录失败不区分原因**：用户名不存在与密码错误都给业务码 30001，避免被用来枚举账号；禁用账号单独给 30002。
- **双重把关**：`/admin/**` 由 URL 规则限 ADMIN，写接口再叠一层 `@PreAuthorize("hasAuthority('user:write')")`，规则放宽时还有第二道防线。
- **CSRF 关闭有前提**：纯 Bearer 令牌、无 cookie 会话才能关；一旦改回 session 认证必须重新打开。

## 已知边界

内存仓库、无刷新令牌、无登出黑名单、无登录失败次数限制。要做到“改密码后旧令牌立刻失效”，需要引入令牌版本号或黑名单，本模块没有覆盖。

## 避坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 全接口 401，登录也 401 | 登录路径没放行 | `requestMatchers("/auth/login").permitAll()` 且写在 `anyRequest()` 之前 |
| `hasRole("ADMIN")` 始终不通过 | 权限里存的是 `ADMIN` 而不是 `ROLE_ADMIN` | 角色名加 `ROLE_` 前缀，或用 `hasAuthority("ADMIN")` |
| 令牌签发时报 WeakKeyException | 密钥短于 32 字节 | 换更长的密钥，长度由 `@Size` 在启动时校验 |
| 令牌解析报签名不匹配 | 签发与解析用了两个不同的密钥实例 | 密钥统一从 `JwtProperties` 取，只构造一次 `SecretKey` |
| 请求带令牌仍然 403 | 过滤器没挂进过滤链，或 `SecurityContext` 被清空 | 确认 `addFilterBefore(...)`，并检查令牌是否过期 |
| 启动日志提示 UserDetailsService 不参与自动配置 | 容器里暴露了 `AuthenticationProvider` Bean | 在 `AuthenticationManager` 内部构造 `DaoAuthenticationProvider`，不单独暴露 |
| 方法级鉴权不生效 | 少 `@EnableMethodSecurity` | 加在配置类上，`@PreAuthorize` 才会被拦截 |
| `#owner == authentication.name` 里的参数取到 null | 编译没开 `-parameters` | 父 POM 已开启，改 POM 后需重新编译 |
