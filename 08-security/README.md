# 08-security · 认证与授权

> 📖 详细教程：[docs/08-security.md](../docs/08-security.md)

> 状态：✅ 已实现（全局拦截 / 白名单 / 角色与权限点 / 表单登录与 CSRF / JWT 无状态 / 短信验证码登录 / 统一 JSON 错误 / CORS）

教程按 18 章一条链往下改，改到后面就看不到前面。本模块把它拆成五条 `SecurityFilterChain`，各自用 `securityMatcher` 管一段路径，一次启动就能把五个阶段的规则放在一起对照，不用改配置重启。

五个阶段共用同一套账号数据、同一个 `AuthenticationManager`、同一套权限点，区别只在“哪条路径、用什么凭证、错了返回什么”。

## 学习目标

- 搞清楚认证与授权的先后：认证过滤器在授权过滤器前面，`anyRequest()` 只能写在最后
- 分得清 `hasRole` / `hasAuthority` / `hasAnyRole` / `hasAllRoles` 的差异，以及 `ROLE_` 前缀从哪来
- 能定位 403 的两个来源：`CsrfFilter` 与 `AuthorizationFilter`
- 知道过滤器链里的异常和 Controller 里的异常是两套处理，`EntryPoint` 与 `@RestControllerAdvice` 都要写
- 把有状态 Session 换成无状态 JWT，理解必须同时关掉的四个开关
- 用 `AuthenticationProvider` 增加一种凭证类型，让短信登录复用全部下游机制

## 五个阶段

| 阶段 | 路径 | 规则 | 认证方式 | 对照教程 |
| --- | --- | --- | --- | --- |
| 一 | `/stage1/**` | `anyRequest().authenticated()` | HTTP Basic | 第 2、3 章 |
| 二 | `/stage2/**` | 加 `permitAll`，GET 与写操作分开 | HTTP Basic | 第 4 章 |
| 三 | `/stage3/**` | `hasRole` / `hasAnyRole` / `hasAllRoles` / `hasAuthority` | HTTP Basic | 第 5 章 |
| 四 | `/stage4/**` | 表单登录 + Session + CSRF | Session | 第 6、7、8 章 |
| 五 | 其余（`/api/**`） | 无状态 JWT + 短信登录 + 统一 JSON + CORS | Bearer token | 第 9 至 16 章 |

阶段四的登录页是静态资源 `/stage4/login.html`，登录提交到 `/stage4/login`，两个地址都在 `/stage4/**` 里，避开了 `loginPage` 与 `loginProcessingUrl` 同址造成的重定向死循环。

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| 规则顺序 | 匹配从上往下、命中即停，越具体越靠前，`anyRequest()` 永远最后 |
| `ROLE_` 前缀 | `.roles("ADMIN")` 存的是 `ROLE_ADMIN`；库里存 `ADMIN` 时前缀要自己补，`hasRole("ROLE_ADMIN")` 会去找 `ROLE_ROLE_ADMIN` |
| 权限点 | 细粒度权限不加前缀，用 `hasAuthority("ARTICLE_DELETE")`，表里用 `grant_type` 区分要不要补前缀 |
| `hasAllRoles` | Security 7 新增，要求全部具备；`hasAnyRole` 满足其一即可 |
| 认证失败 | 密码错是 `BadCredentialsException`，账号禁用是 `DisabledException`，锁定是 `LockedException`，三者对外分开返回 |
| 用户不存在 | `DaoAuthenticationProvider` 默认把它包装成 `BadCredentialsException`，防止靠提示枚举用户名 |
| 两套异常处理 | 过滤器链的异常走 `AuthenticationEntryPoint` / `AccessDeniedHandler`；Controller 里的异常走 `@RestControllerAdvice`，互不替代 |
| `@PreAuthorize` 拒绝 | 抛的是 `AuthorizationDeniedException`，在 Controller 调用栈里抛出，不接住会被兜底成 500 |
| 无状态四开关 | `sessionCreationPolicy(STATELESS)`、关 `formLogin`、关 `httpBasic`、关 `logout`，CSRF 也一并关掉 |
| 过滤器位置 | 认证类过滤器必须 `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`，用 `addFilter` 会排到授权之后，设了上下文也没用 |
| 一条铁律 | 过滤器里不直接返回错误，清空上下文放行，让授权层统一拒绝，错误响应只有一份实现 |
| 短信登录 | 不是新接口，是新的凭证类型：`SmsCodeAuthenticationToken` + `SmsCodeAuthenticationProvider`，塞进 `ProviderManager` |
| 验证码三防 | 发送间隔限频（`setIfAbsent` + TTL）、5 分钟有效、一次性（校验通过立即删）；再加错误次数上限 |
| 两个 `UserDetailsService` | 密码登录按 username 查，短信登录按 phone 查。容器里有两个同类型 Bean，注入要按名字或 `@Qualifier`，别用 `@Primary` |
| 自写 Provider 要补检查 | `DaoAuthenticationProvider` 替密码登录做的账号状态检查，自写 Provider 里必须自己调 `isEnabled()` / `isAccountNonLocked()` |
| CORS 位置 | 必须配在 Security 层，401 响应才会带 CORS 头；只写 `@CrossOrigin` 或 `WebMvcConfigurer` 不生效 |

## 目录结构

```
08-security
└── src/main
    ├── java/top/mqxu/security
    │   ├── SecurityApplication.java
    │   ├── common
    │   │   ├── Result.java                     # 统一响应壳
    │   │   ├── ErrorCode.java
    │   │   ├── BusinessException.java
    │   │   └── GlobalExceptionHandler.java     # Controller 层异常
    │   ├── config
    │   │   ├── SecurityConfig.java             # 五条过滤器链 + AuthenticationManager
    │   │   ├── CorsConfig.java
    │   │   └── SecurityProperties.java         # mqxu.security.*
    │   ├── security
    │   │   ├── JwtService.java                 # 签发 / 解析
    │   │   ├── JwtAuthenticationFilter.java    # 每请求读 Bearer
    │   │   ├── JsonAuthenticationEntryPoint.java   # 401
    │   │   ├── JsonAccessDeniedHandler.java        # 403
    │   │   └── RequestTraceFilter.java         # 访问日志，演示过滤器位置
    │   ├── sms
    │   │   ├── SmsCodeService.java
    │   │   ├── RedisSmsCodeService.java        # sms.store=redis
    │   │   ├── InMemorySmsCodeService.java     # sms.store=memory
    │   │   ├── SmsCodeAuthenticationToken.java
    │   │   └── SmsCodeAuthenticationProvider.java
    │   ├── service
    │   │   ├── UserAssembler.java              # 账号行 → UserDetails
    │   │   ├── DbUserDetailsService.java       # 密码登录，按 username
    │   │   └── PhoneUserDetailsService.java    # 短信登录，按 phone
    │   ├── entity / mapper                     # sec_user / sec_role
    │   ├── controller
    │   │   ├── AuthController.java             # /api/auth/login、/api/auth/me
    │   │   ├── SmsAuthController.java          # /api/auth/sms/**
    │   │   ├── ApiController.java              # 业务示例
    │   │   └── StageController.java            # /stage1 至 /stage4 的对照端点
    │   └── dto
    ├── test/java/top/mqxu/security
    │   ├── JwtServiceTest.java
    │   ├── SysRoleTest.java
    │   ├── InMemorySmsCodeTest.java
    │   └── SecurityIntegrationTest.java
    └── resources
        ├── application.yml
        ├── schema.sql                          # sec_user、sec_role，启动时先删后建
        ├── data.sql                            # 六个账号
        └── static
            ├── index.html                      # 五个阶段的导航页
            └── stage4/login.html + login.css   # 表单登录页
```

## 运行方式

先确认两个外部服务：

```bash
# MySQL，沿用 backend_learning 库，表名 sec_ 前缀
mysql -h127.0.0.1 -P3306 -uroot -p123456 -e "show databases;"

# Redis，验证码存在 db 11，密码见 application.yml
redis-cli --no-auth-warning -a abc123 -n 11 ping
```

没有 Redis 也能跑，把 `mqxu.security.sms.store` 改成 `memory`，验证码改存进程内 `ConcurrentHashMap`，重启即失效。

```bash
mvn -pl 08-security spring-boot:run
```

端口：`8008`。浏览器打开 `http://127.0.0.1:8008/` 是五个阶段的导航页。

`schema.sql` 里有 `DROP TABLE`，每次启动和 DevTools 热重启都会重建账号数据。

演示账号，密码都是 `123456`（库里存的是 BCrypt 密文）：

| 账号 | 手机号 | 角色与权限 | 用途 |
| --- | --- | --- | --- |
| alice | 13800000001 | `ROLE_USER` + `ARTICLE_DELETE` | 普通账号，但能删除文章 |
| bob | 13800000002 | `ROLE_ADMIN` + `ROLE_AUDITOR` | `hasAllRoles` 通过 |
| carol | 13800000003 | `ROLE_ADMIN` | 只有 ADMIN，`hasAllRoles` 被拒 |
| erin | 13800000006 | `ROLE_AUDITOR` | `hasAnyRole` 通过，管理员接口被拒 |
| dave | 无 | `ROLE_USER` | `enabled=false`，登录返回 403 |
| frank | 无 | `ROLE_USER` | `locked=true`，登录返回 403 |

## 接口清单

| 分组 | 请求 | 说明 |
| --- | --- | --- |
| 阶段一 | `GET /stage1/hello` | 全拦截，匿名 401 |
| 阶段二 | `GET /stage2/public/info` | 匿名可访问 |
| | `GET /stage2/articles/{id}` | 匿名可访问 |
| | `DELETE /stage2/articles/{id}` | 未放行，需要认证 |
| 阶段三 | `GET /stage3/admin/reports` | `hasRole("ADMIN")` |
| | `GET /stage3/ops/alerts` | `hasAnyRole("ADMIN","AUDITOR")` |
| | `GET /stage3/audit/logs` | `hasAllRoles("ADMIN","AUDITOR")` |
| | `DELETE /stage3/articles/{id}` | `hasAuthority("ARTICLE_DELETE")` |
| 阶段四 | `GET /stage4/login.html` | 静态登录页 |
| | `GET /stage4/csrf-token` | 取 CSRF token，同时写入 XSRF-TOKEN Cookie |
| | `POST /stage4/login` | 表单登录，成功 302 到 `/stage4/me` |
| | `POST /stage4/logout` | 登出，必须 POST 且带 `_csrf` |
| | `GET /stage4/me` | 当前登录人 |
| | `GET /stage4/admin/panel` | 需要 `ROLE_ADMIN` |
| 阶段五 | `POST /api/auth/login` | 用户名密码换 token |
| | `GET /api/auth/me` | 需要 token |
| | `POST /api/auth/sms/code?phone=` | 发送验证码 |
| | `POST /api/auth/sms/login` | 手机号 + 验证码换 token |
| | `GET /api/hello` | 需要 token，返回身份与权限 |
| | `GET /api/public/info` | 公开 |
| | `GET /api/admin/dashboard` | 需要 `ROLE_ADMIN` |
| | `GET /api/reports` | `@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")` |
| | `DELETE /api/articles/{id}` | 需要 `ARTICLE_DELETE` |

## 验证

以下命令都在模块根目录执行，`B=http://127.0.0.1:8008`。

### 阶段一：全拦截

```bash
B=http://127.0.0.1:8008

curl -i $B/stage1/hello                          # 401
curl -u bob:123456 $B/stage1/hello               # 200 阶段一通过，当前用户：bob
curl -u bob:wrong  $B/stage1/hello               # 401，密码错也是 401 不是 403
```

401 表示“不知道你是谁”，403 表示“知道你是谁但你不能进”。这两个混用，前端就没法区分该跳登录页还是提示无权限。

### 阶段二：白名单

```bash
curl -s $B/stage2/public/info                    # 200 公开接口，匿名可访问
curl -s $B/stage2/articles/1                     # 200 GET 放行
curl -X DELETE $B/stage2/articles/1              # 401 同一个路径的 DELETE 没放行
curl -s -u bob:123456 -X DELETE $B/stage2/articles/1   # 200
```

### 阶段三：角色与权限点

```bash
curl -s -u alice:123456 $B/stage3/admin/reports  # 403 alice 只有 ROLE_USER
curl -s -u bob:123456   $B/stage3/admin/reports  # 200
curl -s -u bob:123456   $B/stage3/audit/logs     # 200 ADMIN + AUDITOR 都有
curl -s -u carol:123456 $B/stage3/audit/logs     # 403 缺 AUDITOR，hasAllRoles 不通过
curl -s -u erin:123456  $B/stage3/ops/alerts     # 200 hasAnyRole 满足其一
curl -s -u alice:123456 -X DELETE $B/stage3/articles/1  # 200 有 ARTICLE_DELETE
curl -s -u bob:123456   -X DELETE $B/stage3/articles/1  # 403 有 ADMIN 但没有这个权限点
```

403 的响应体固定是 `{"code":403,"message":"没有访问权限","data":null}`，来自 `JsonAccessDeniedHandler`。

### 阶段四：表单登录

```bash
rm -f /tmp/c.txt

# 未登录访问受保护接口，302 跳登录页
curl -o /dev/null -w "%{http_code} -> %{redirect_url}\n" \
     -H "Accept: text/html" $B/stage4/me

# 先取 token，顺手把 Cookie 存下来
T=$(curl -s -c /tmp/c.txt $B/stage4/csrf-token \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 不带 _csrf 直接 POST
curl -o /dev/null -w "%{http_code}\n" -X POST $B/stage4/login \
     -d "username=bob&password=123456"                      # 403

# 密码错
curl -o /dev/null -w "%{http_code} -> %{redirect_url}\n" \
     -b /tmp/c.txt -c /tmp/c.txt -X POST $B/stage4/login \
     -d "username=bob&password=wrong&_csrf=$T"               # 302 -> login.html?error

# 密码对
curl -o /dev/null -w "%{http_code} -> %{redirect_url}\n" \
     -b /tmp/c.txt -c /tmp/c.txt -X POST $B/stage4/login \
     -d "username=bob&password=123456&_csrf=$T"              # 302 -> /stage4/me

curl -s -b /tmp/c.txt $B/stage4/me
# {"code":200,"data":{"username":"bob","authorities":["ROLE_ADMIN","ROLE_AUDITOR","FACTOR_PASSWORD"]},"message":"ok"}
```

`FACTOR_PASSWORD` 是 Security 7 给通过密码认证的会话打的标记，用于多因素认证，不影响授权判断。

CSRF token 在 Cookie 里和表单隐藏域里必须一致。Cookie 存的是 36 字符的裸 UUID，默认的 `XorCsrfTokenRequestAttributeHandler` 会把 `_csrf` 属性掩码成 96 字符，两边对不上就永远 403，所以 stage4 换成了不做掩码的 `CsrfTokenRequestAttributeHandler`。

### 阶段五：JWT

```bash
# 匿名访问
curl -i $B/api/hello
# HTTP/1.1 401 {"code":401,"message":"未登录或登录已过期","data":null}

# 密码登录换 token
TOK=$(curl -s -X POST $B/api/auth/login -H "Content-Type: application/json" \
      -d '{"username":"alice","password":"123456"}' \
      | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 带 token 访问
curl -s -H "Authorization: Bearer $TOK" $B/api/hello
# {"code":200,"data":{"authorities":["ARTICLE_DELETE","ROLE_USER","FACTOR_PASSWORD"],"message":"你好，alice"},"message":"ok"}

# 普通账号进管理员接口
curl -s -H "Authorization: Bearer $TOK" $B/api/admin/dashboard      # 403

# 方法级注解
curl -s -H "Authorization: Bearer $TOK" $B/api/reports              # 403

# 权限点
curl -s -X DELETE -H "Authorization: Bearer $TOK" $B/api/articles/9  # 200

# 伪造 token
curl -s -H "Authorization: Bearer abc.def.ghi" $B/api/hello         # 401
```

四种账号状态，密码都是 `123456`：

```bash
curl -s -X POST $B/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"dave","password":"123456"}'
# 403 {"code":403,"message":"账号已被禁用","data":null}

curl -s -X POST $B/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"frank","password":"123456"}'
# 403 {"code":403,"message":"账号已被锁定","data":null}

curl -s -X POST $B/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"nobody","password":"123456"}'
# 401 {"code":401,"message":"用户名或密码错误","data":null}   用户不存在对外也是这句

curl -s -X POST $B/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"","password":"1"}'
# 400 {"code":400,"message":"用户名不能为空","data":null}
```

### 短信登录

```bash
# 发码。expose-code=true 时验证码直接回显，方便 curl
curl -s -X POST "$B/api/auth/sms/code?phone=13800000002"
# {"code":200,"data":"验证码已发送（演示模式，验证码：081730）","message":"ok"}

# 60 秒内重发
curl -s -X POST "$B/api/auth/sms/code?phone=13800000002"
# {"code":429,"message":"验证码发送过于频繁，请稍后再试","data":null}

# 手机号格式错
curl -s -X POST "$B/api/auth/sms/code?phone=12345"
# {"code":400,"message":"手机号格式不正确","data":null}

# 登录
curl -s -X POST $B/api/auth/sms/login -H "Content-Type: application/json" \
     -d '{"phone":"13800000002","code":"081730"}'
# {"code":200,"data":{"token":"eyJhbGciOiJIUzUxMiJ9...","username":"bob",
#  "authorities":["ROLE_ADMIN","ROLE_AUDITOR"],...},"message":"ok"}

# 同一个码再登一次
curl -s -X POST $B/api/auth/sms/login -H "Content-Type: application/json" \
     -d '{"phone":"13800000002","code":"081730"}'
# 401 {"code":401,"message":"验证码错误或已过期","data":null}
```

验证码是六位数字，用 `%06d` 补零，全程按字符串处理。示例里那个 `081730` 的前导 0 一旦被 `int` 或 `Number()` 转换吃掉，就会永远校验失败。

换个没注册过的手机号，第一次登录会自动建号并分配 `ROLE_USER`：

```bash
curl -s -X POST "$B/api/auth/sms/code?phone=13800009999"
curl -s -X POST $B/api/auth/sms/login -H "Content-Type: application/json" \
     -d '{"phone":"13800009999","code":"<上一步的验证码>"}'
# data.authorities 是 ["ROLE_USER"]
```

自动注册是双刃剑，任何知道手机号的人都能建号。真实业务要么在发码前校验手机号是否已注册，要么把注册和登录分成两个接口。

### CORS

```bash
O=http://localhost:5173

# 预检
curl -i -X OPTIONS $B/api/hello -H "Origin: $O" \
     -H "Access-Control-Request-Method: GET" \
     -H "Access-Control-Request-Headers: authorization"

# 401 也要带 CORS 头，否则前端只看到跨域错误，拿不到 401
curl -i $B/api/hello -H "Origin: $O" | grep -i access-control

# 陌生来源
curl -i $B/api/public/info -H "Origin: http://evil.com"
# HTTP/1.1 403  Invalid CORS request，CorsFilter 在预检阶段就拒掉
```

`Invalid CORS request` 这句是 `CorsFilter` 自己吐的，不是代码里写的，请求根本到不了 Controller。

## 测试

```bash
mvn -pl 08-security test
```

26 个用例，四个类：

| 测试类 | 用例数 | 说明 |
| --- | --- | --- |
| `JwtServiceTest` | 4 | 纯单元：签发解析、篡改签名、过期、换密钥 |
| `SysRoleTest` | 3 | 纯单元：`ROLE_` 前缀、权限点原样、`grant_type` 为空 |
| `InMemorySmsCodeTest` | 5 | 纯单元：一次性、错误次数作废、发送间隔、过期、前导零 |
| `SecurityIntegrationTest` | 14 | 集成：五条链的真实行为，含账号禁用/锁定、hasAllRoles、短信登录 |

集成测试连本地 MySQL 与 Redis，用例开始前会清掉 `sms:*` 的 key，避免上一轮的发送冷却把这一轮顶成 429。

## 常见错误

| 症状 | 原因 | 修法 |
| --- | --- | --- |
| 权限配了还是 403 | `CsrfFilter` 拒的，不是授权拒的 | 打开 TRACE 日志搜 `Invalid CSRF token`，有这句就是 CSRF |
| 所有错误码都变成 401 | `/error` 没放行，参数校验失败的内部转发被拦 | `/error` 一律 `permitAll` |
| `@PreAuthorize` 不生效 | 漏了 `@EnableMethodSecurity` | 加注解，并手动测一条“应被拒”的请求 |
| `@PreAuthorize` 拒绝返回 500 | `AuthorizationDeniedException` 落到兜底 handler | 在 `@RestControllerAdvice` 里接住 `AccessDeniedException` |
| 自定义 JWT 过滤器不生效 | 用了 `addFilter`，排到 `AuthorizationFilter` 之后 | `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` |
| `super(null)` 编译不过 | Security 7 给 `AbstractAuthenticationToken` 加了第二个构造器，两个都能匹配 | `super((Collection<? extends GrantedAuthority>) null)` |
| 一直提示 `There is no PasswordEncoder mapped for the id "null"` | 库里存了明文 | 存 `{bcrypt}$2a$10$...`，或临时用 `{noop}123456` |
| `.roles("USER").authorities("X")` 后角色消失 | 两个方法写同一个属性，后者覆盖前者 | 统一用 `.authorities("ROLE_USER", "X")` |
| `hasRole("ADMIN")` 永远不匹配 | 库里存的是 `ADMIN`，没补 `ROLE_` | 拼 `"ROLE_" + roleCode`，本模块用 `grant_type` 区分 |
| 日志里出现两条 `Found N UserDetailsService beans` 警告 | 密码登录与短信登录各有一个 `UserDetailsService` | 注入用 Bean 名或 `@Qualifier`，并显式提供 `AuthenticationManager` |
| 自定义过滤器执行了两次 | `@Component` 的 `Filter` 会被 Servlet 容器再注册一次 | 加 `FilterRegistrationBean` 并 `setEnabled(false)` |
| 登录页拿不到 CSRF token | 取 token 的端点没放行，返回 302 到登录页的 HTML | 把 `/stage4/csrf-token` 加进 `permitAll` |
| 验证码看着少一位、永远校验失败 | 前导 0 被数字类型吃掉 | 全程 `String`，生成时 `%06d` |

排查顺序：先看状态码分类（401 认人失败 / 403 权限或 CSRF / 302 有过滤器抢答 / 400 参数 / 429 限频 / 500 代码错），再用 `--logging.level.org.springframework.security=DEBUG` 看过滤器链，还不行上 TRACE，最后打一个 `/api/auth/me` 看上下文里到底是谁、有什么权限。

## 与教程的差异

| 项 | 教程 | 本模块 | 原因 |
| --- | --- | --- | --- |
| 数据库 | H2 内存库 | MySQL `backend_learning` | 与 05、06 模块共用一套环境 |
| 表名 | `sys_user` / `sys_role` | `sec_user` / `sec_role` | 加前缀避免和别的模块抢表名 |
| 角色存储 | 只存 `role_code` | 多一列 `grant_type` | 区分角色（补 `ROLE_`）与权限点（不补） |
| 密码 | `{noop}123456` | `{bcrypt}$2a$10$...` | 演示真实存储形态，明文只出现在本文件 |
| 结构 | 一条链逐步演进 | 五条链用 `securityMatcher` 并存 | 一次启动就能对照五个阶段 |
| 阶段一 | 表格登录 + Basic | 只开 Basic | 多链下默认登录页 `/login` 不落在 `/stage1/**` 里，`formLogin` 会跳到一个访问不了的地址 |
| 短信存储 | 直接写 Redis | 接口 + Redis / 内存双实现，配置切换 | 没装 Redis 也能跑通链路 |
| 需求 | 无 | 加了错误次数上限（默认 5 次作废） | 只有发送限频挡不住撞码 |

## 依赖

- `spring-boot-starter-security`（7.1.1，版本由父 POM 的 Boot 4.1.1 托管）
- `spring-boot-starter-validation`
- `spring-boot-starter-data-redis` + `commons-pool2`
- `mybatis-plus-spring-boot4-starter` 3.5.17
- `mysql-connector-j`（runtime）
- `jjwt-api` / `jjwt-impl` / `jjwt-jackson` 0.13.0

## 上线前必改

| # | 检查项 | 本模块的演示值 | 生产应该 |
| --- | --- | --- | --- |
| 1 | JWT 密钥 | 写在 `application.yml` | 环境变量或 KMS 注入，不进仓库 |
| 2 | JWT 有效期 | 120 分钟 | access 15 至 30 分钟 + refresh 7 天 |
| 3 | `expose-code` | `true` | `false`，并全局搜一遍确认 |
| 4 | CORS 来源 | `localhost:5173` 等 | 具体生产域名 |
| 5 | 密码编码 | BCrypt strength 10 | 不低于 10，或换 Argon2 |
| 6 | 短信自动注册 | `true` | 按业务决定，通常关闭 |
| 7 | 验证码日志 | 打印了验证码本身 | 删掉，或只打脱敏手机号 |
| 8 | HTTPS | 无 | 强制 HTTPS，并开 HSTS |

第 3 项和第 7 项是真实事故来源：验证码回显和打印同时带上线，任何人调一次接口就能拿到任意手机号的验证码，等于没有认证。
