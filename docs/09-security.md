# 09 · 认证与授权 —— 跟着实训任务案例走通前后端分离

> - 难度：进阶
> - 前置模块：04-web（统一响应、全局异常）
> - 课时：90 分钟（含随堂练习），课后作业另计
> - 对应代码：`09-security`（后端，22 个接口测试）、`09-security/web`（前端，4 个页面）
> - 投影版讲义：`09-security/lecture.html`，浏览器直接打开，带目录与打印样式
> - 版本基线：Spring Boot 4.1.1、Spring Security 7.1.1、jjwt 0.13.0、Java 21、Vue 3.5、Vite 8、axios 1.21

---

## 案例：实训任务提交与批改

一个每门实训课都在用的小系统，三种角色，三条规则：

| 角色 | 能做的事 | 不能做的事 |
| --- | --- | --- |
| 学生 STUDENT | 提交任务、看自己的提交与分数 | 看别人的、批改 |
| 教师 TEACHER | 看全部提交、打分写评语 | 提交任务、进管理接口 |
| 管理员 ADMIN | 全部提交、账号增删查 | — |

要解决的三件事必须同时成立：后端知道你是谁、后端知道你能做什么、前端知道出事了怎么办。第二条和第三条是学生最容易漏的。

演示账号（密码在种子数据里用 BCrypt 加密后写死）：

| 账号 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| student01 | student123 | STUDENT | 预置一条待批改任务 |
| student02 | student123 | STUDENT | 预置一条已批改任务，用于演示越权 |
| teacher01 | teacher123 | TEACHER | 看全部、能批改 |
| admin | admin123 | ADMIN | 全部提交 + 账号管理 |
| guest | guest123 | STUDENT | 登录直接失败，账号已停用 |

## 跑起来

两个终端。

```bash
# 终端一：后端 8009
cd 09-security
mvn spring-boot:run

# 终端二：前端 5173
cd 09-security/web
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，用页面上的快捷按钮切换账号。

开发期跨域靠 Vite 代理解决，浏览器全程只访问 5173：

```js
server: {
  port: 5173,
  proxy: {
    '/api':  { target: 'http://127.0.0.1:8009', changeOrigin: true },
    '/auth': { target: 'http://127.0.0.1:8009', changeOrigin: true },
  },
}
```

上线分离部署时没有这层代理，才需要后端那份 CORS 配置（`SecurityConfig#corsConfigurationSource`）。

## 接口契约

前端与后端之间只靠这张表和两个状态码沟通。

| 方法 | 路径 | 谁能调 | 说明 |
| --- | --- | --- | --- |
| POST | `/auth/login` | 公开 | 换令牌 |
| GET | `/auth/me` | 登录 | 刷新页面后恢复身份 |
| POST | `/api/tasks` | 学生 | 提交，`task:submit` |
| GET | `/api/tasks/mine` | 登录 | 只返回自己的 |
| GET | `/api/tasks/{id}` | 本人或教师管理员 | 越权返回 403 |
| GET | `/api/tasks` | 教师、管理员 | 全部提交，`task:review` |
| POST | `/api/tasks/{id}/review` | 教师、管理员 | 批改，`task:review` |
| GET | `/admin/users` | 管理员 | 账号列表 |
| POST | `/admin/users` | 管理员 | 新增账号，`user:write` |
| DELETE | `/admin/users/{username}` | 管理员 | 删除账号 |
| GET | `/public/ping` | 公开 | 用来对比放行效果 |

响应统一是 `Result`：成功时 `{"code":200,"data":...,"message":"success"}`。业务失败也是 HTTP 200，靠 `code` 区分；越权则必须是真实的 403。这个约定直接决定前端拦截器怎么写。

## 登录那一下

后端不自己比对密码，交给 `AuthenticationManager`：

```java
Authentication authentication;
try {
    authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password()));
} catch (DisabledException e) {
    throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);   // 30002
} catch (AuthenticationException e) {
    // 密码错与用户不存在给同一个提示，防止被用来试探账号是否存在
    throw new BusinessException(ErrorCode.BAD_CREDENTIALS);    // 30001
}

SecurityUser user = (SecurityUser) authentication.getPrincipal();
JwtTokenService.IssuedToken issued = jwtTokenService.issue(user);
return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(),
        user.getUsername(), user.account().getNickname(), user.roleNames());
```

真实响应：

```json
{
  "code": 200,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJiYWNrZW5kLWxlYXJuaW5nIiwi...",
    "tokenType": "Bearer",
    "expiresAt": "2026-10-28T03:17:08Z",
    "username": "student01",
    "nickname": "朱彦清",
    "roles": ["STUDENT"]
  },
  "message": "success"
}
```

令牌三段，前两段是明文，只是 Base64 编码：

```
Header    eyJhbGciOiJIUzI1NiJ9
          {"alg":"HS256"}

Payload   eyJpc3MiOiJiYWNrZW5kLWxlYXJuaW5nIiwic3ViIjoic3R1ZGVudDAxIiwiaWF0IjoxNzkzMTIxNDI4LCJleHAiOjE3OTMxMjg2MjgsInJvbGVzIjpbIlNUVURFTlQiXX0
          {"iss":"backend-learning","sub":"student01","iat":1793121428,"exp":1793128628,"roles":["STUDENT"]}

Signature 用前两段加密钥算出，不可反推
```

`sub` 是身份，`exp` 由解析时自动校验，`iss` 防止别的系统用同密钥签的令牌混进来。`roles` 只给前端显示菜单用，不参与后端鉴权：令牌签发后改不了，管理员把某人降级后旧令牌里还写着旧角色。本模块的做法是令牌只带身份，权限每次请求按用户名回查。

前端把令牌存 localStorage，三个函数读写：

```js
export function saveLogin(data) {
  auth.token = data.token
  auth.user = { username: data.username, nickname: data.nickname, roles: data.roles }
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(auth.user))
}
```

不存 cookie 的理由：令牌走请求头，浏览器不会自动带上，天然不受 CSRF 影响；代价是脚本能读到它，必须防 XSS。

## 后端怎么认出你

自己写一个过滤器，在进入 Controller 之前把令牌换成身份：

```java
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                FilterChain filterChain) throws ServletException, IOException {
    String token = resolveToken(request);
    if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        String username = tokenService.parseUsername(token);   // 验签 + 校验过期与签发方
        UserDetails user = userDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (JwtException | UsernameNotFoundException e) {
        SecurityContextHolder.clearContext();     // 不在这里写响应
    }
    filterChain.doFilter(request, response);
}
```

加进链里只有一行，位置必须早于授权判断：

```java
.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
```

排在授权判断后面，判断权限时上下文还是空的，所有请求都会被当成未登录。

令牌无效时不在这里返回 401，只清空上下文继续往下走，让授权判断拒绝这个匿名请求，401 由 `RestAuthenticationEntryPoint` 统一输出。要是在过滤器里也写一份响应，同一个错误就有两种格式。

## 前端三件事

全部集中在 `src/api/request.js`，四个页面不再关心鉴权。

请求拦截器加令牌：

```js
request.interceptors.request.use((config) => {
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})
```

响应拦截器把 401 和 403 分开：

```js
request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body.code === 200) return body.data
    notify(body.message || '操作失败')
    return Promise.reject(new Error(body.message))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      clearLogin()
      router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      notify('登录已失效，请重新登录')
    } else if (status === 403) {
      notify(error.response?.data?.message || '你没有权限做这个操作')   // 不动登录态
    } else if (status) {
      notify(`请求失败（${status}）`)
    } else {
      notify('连不上后端，确认 8009 是否已启动')
    }
    return Promise.reject(error)
  }
)
```

写反的后果：学生点了一个只有教师能点的按钮，直接被踢回登录页，重新登录进去还在那个页面，再点又被踢。

路由守卫只是体验层，改了 localStorage 就能进页面，但进去以后接口全是 403：

```js
router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.roles && !hasAnyRole(to.meta.roles)) return homeOf()
  return true
})
```

## 数据归属

“学生只能看自己的”有三种写法，只有一种是对的：

| 做法 | 前端请求 | 后果 |
| --- | --- | --- |
| 前端传用户名 | `GET /api/tasks?owner=student01` | 改参数就能看别人的 |
| 路径带用户名 | `GET /api/tasks/mine/student01` | 改路径就能越权 |
| 服务端从令牌取 | `GET /api/tasks/mine` | URL 里没有身份，无处可改 |

```java
/**
 * 我的提交：URL 里没有用户名，前端想查别人的也无从下手
 */
@GetMapping("/mine")
public Result<List<TaskView>> mine(@AuthenticationPrincipal SecurityUser user) {
    return Result.ok(taskService.listMine(user.getUsername()));
}
```

提交任务时请求体里也没有 owner 字段，前端硬塞一个进去不会被采纳（测试 `ownerCannotBeForgedByBody` 就是验证这条）。

详情接口的编号一定可以被改，判断要放在服务端：

```java
public TaskView detail(Long id, SecurityUser viewer) {
    Task task = find(id);
    if (!canView(task, viewer)) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    return TaskView.of(task);
}

private boolean canView(Task task, SecurityUser viewer) {
    if (task.getOwnerUsername().equals(viewer.getUsername())) return true;
    return viewer.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("task:review"));
}
```

全局异常处理器对越权单独给 403，其余业务失败仍是 200 + code：

```java
HttpStatus status = switch (e.getErrorCode()) {
    case FORBIDDEN -> HttpStatus.FORBIDDEN;
    case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
    default -> HttpStatus.OK;
};
return ResponseEntity.status(status).body(Result.fail(e.getErrorCode()));
```

把越权包在 HTTP 200 里，前端拦截器会当成成功响应，表格里就渲染出别人的数据。

## 权限点

```java
public enum Role {
    STUDENT(Set.of("task:submit", "task:read")),
    TEACHER(Set.of("task:review", "task:read")),
    ADMIN(Set.of("user:read", "user:write", "task:read", "task:review"));
}
```

接口绑权限点而不是绑角色：

```java
@PostMapping
@PreAuthorize("hasAuthority('task:submit')")
public Result<TaskView> submit(@Valid @RequestBody SubmitTaskRequest request,
                               @AuthenticationPrincipal SecurityUser user) {
    return Result.ok(taskService.submit(request, user));
}
```

将来多一个“教务”，能看全部但不能批改，给它配 `task:read` 就行，接口代码不用动。

两个必踩的坑：

- `hasRole("ADMIN")` 内部会拼成 `ROLE_ADMIN` 比对，权限集合里必须有这个前缀；`hasAuthority("task:review")` 是原样比对，不能加前缀。混用会出现“配置看起来对，却永远 403”。
- 管理接口做了双重把关：URL 规则 `/admin/**` 限管理员，Controller 上再写一次 `@PreAuthorize`。有人把 URL 规则改宽时，第二道还在。

## 测试覆盖

`mvn -pl 09-security test`，22 个用例，按链路分组：

| 分组 | 覆盖点 |
| --- | --- |
| 登录 | 放行接口、未带令牌 401、登录返回令牌与角色、密码错与用户不存在同提示、停用账号 30002、参数校验、令牌换身份、令牌被改后 401 |
| 数据归属 | 提交归属写死为当前用户、请求体伪造 owner 无效、只返回自己的、看别人的返回真实 403、能看自己详情 |
| 教师权限 | 学生不能看全部、不能批改、教师能看到全部、批改后学生立刻能看分数、同一份不能批改两次、教师不能提交、分数越界被拦 |
| 管理 | 教师进不了管理接口、管理员能看列表且响应无密码、新增学生能登录、不能删自己 |

## 排错表

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 未认证返回 302 | `formLogin` 没关 | `formLogin(AbstractHttpConfigurer::disable)` |
| 401 响应体是空白 HTML | 没配 entryPoint | 指定 `RestAuthenticationEntryPoint` |
| 登录接口也 401 | 没放行 `/auth/login`，或规则顺序写反 | `permitAll` 写在 `anyRequest` 之前 |
| 带令牌仍 401 | 令牌过期、被改、签发与解析不是同一个密钥 | 解码看 `exp`；确认配置只有一个密钥 |
| 带令牌仍 403 | 角色没拿到权限点 | `GET /auth/me` 看 `authorities` 实际内容 |
| `hasRole` 永远不通过 | 少了 `ROLE_` 前缀 | 统一用 `Role.authority()` 生成 |
| 启动报 WeakKeyException | 密钥短于 32 字节 | 换长密钥，`@Size(min = 32)` 提前拦 |
| 令牌 Header 是 HS384 | 用了 `signWith(key)` 单参数版 | 显式 `signWith(key, Jwts.SIG.HS256)` |
| 过滤器跑两遍 | 它既是 Bean 又被加进链 | 去掉 `@Component`，配置类里 `new` |
| `@PreAuthorize` 不生效 | 少 `@EnableMethodSecurity` | 加在配置类上 |
| 越权返回 200 而不是 403 | 处理器返回了裸 `Result` | 用 `ResponseEntity.status(403)` |
| 前端一点就跳登录 | 把 403 也当成 401 | 拦截器里分开两个分支 |
| 刷新页面掉登录 | 令牌只存了内存变量 | 存 localStorage，初始化时读回来 |
| 页面能进但列表空白 | 接口 403，数据没返回 | 看 Network 里那条请求的响应体 |
| 请求打到 5173 返回 HTML | 代理前缀和请求路径对不上 | 对齐 `proxy` 配置与实际路径 |

## 课堂练习

**让教师也能提交一条教学示范。** 现在教师调 `POST /api/tasks` 是 403，要求教师也能提交，学生依旧不能批改。

给 `Role.TEACHER` 加 `task:submit` 即可，只碰一行枚举，接口代码没动。可以顺势问：为什么不能直接把注解改成 `hasAnyAuthority('task:submit','task:review')`？能跑，但把业务规则藏进了接口注解里，下次再变又要改代码。

**让学生能撤回自己还没被批改的任务。** 加 `DELETE /api/tasks/{id}`，只有本人且状态为待批改时才能删。

```java
public void withdraw(Long id, SecurityUser user) {
    Task task = find(id);
    if (!task.getOwnerUsername().equals(user.getUsername())) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    if (task.getStatus() == TaskStatus.REVIEWED) {
        throw new BusinessException(ErrorCode.TASK_ALREADY_REVIEWED);
    }
    taskRepository.delete(id);
}
```

检查三点：学生删别人的要 403；删已批改的要给业务提示；教师是否允许删要明确写出来，不要默认能删。

**把令牌有效期改成 1 分钟。** 改配置 `app.jwt.expire-minutes: 1`，登录后等一分钟再操作，观察前端反应：401 → 清登录态 → 跳登录页 → 登录后回到原页面。这条练完，学生就明白刷新令牌为什么存在。

## 课后作业

**基础（20 分）登录失败次数限制。** 同一用户名连续失败 5 次锁定 10 分钟，锁定期间返回新业务码，成功后计数清零。要求说明换到 Redis 后 key 怎么设计、过期怎么处理。

**进阶（30 分）账号与任务存数据库。** 用 MyBatis-Plus 建 `t_user`、`t_role`、`t_user_role`、`t_task`，替换两个内存仓库，要求 `UserDetailsService` 与鉴权代码零改动。这条是检验分层是否真的分开了。

**挑战（30 分）刷新令牌与登出。** 改密码或点退出后旧令牌立刻失效。两条路线选一：令牌版本号存用户表，签发时写入、校验时比对；或者维护黑名单，登出时把 `jti` 放进去，过期时间与令牌剩余有效期一致。要求有测试证明改密码后旧令牌立即 401。

## 已知边界

- 没有刷新令牌，令牌有效期 120 分钟，到期只能重新登录。
- 没有登出黑名单，退出只清前端存储，令牌在到期前仍然可用。
- 用户与任务存内存，重启即丢；种子数据每次启动重建。
- 前端没有做按钮级权限控制，只做了菜单与路由级；需要时用 `hasRole` 判断后 `v-if`。
