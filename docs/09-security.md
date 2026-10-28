# 09 · 认证与授权 —— 跟着实训任务案例走通前后端分离

> - 难度：进阶
> - 前置模块：04-web（统一响应、全局异常）、05-mybatis（MyBatis-Plus 与数据访问）
> - 课时：90 分钟（含随堂练习），课后作业另计
> - 对应代码：`09-security`（后端，22 个接口测试）、`09-security/web`（前端，TypeScript）
> - 投影版讲义：`09-security/lecture.html`，浏览器直接打开，带目录与打印样式
> - 版本基线：Spring Boot 4.1.1、Spring Security 7.1.1、MyBatis-Plus 3.5.17、MySQL 8.4、jjwt 0.13.0、Java 21、Vue 3.5、TypeScript 5.9、Vite 8、axios 1.21

---

## 案例：实训任务提交与批改

一个每门实训课都在用的小系统，三种角色，三条规则：

| 角色 | 能做的事 | 不能做的事 |
| --- | --- | --- |
| 学生 STUDENT | 提交任务、看自己的提交与分数 | 看别人的、批改 |
| 教师 TEACHER | 看全部提交、打分写评语 | 提交任务、进管理接口 |
| 管理员 ADMIN | 全部提交、账号增删查 | — |

要解决的三件事必须同时成立：后端知道你是谁、后端知道你能做什么、前端知道出事了怎么办。

演示账号：

| 账号 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| admin | Admin#2026 | ADMIN | 全部提交 + 账号管理 |
| teacher01 | Teacher#2026 | TEACHER | 看全部、能批改（陶老师） |
| teacher02 | Teacher#2026 | TEACHER | 第二个教师账号（沈老师） |
| student01 | Student#2026 | STUDENT | 朱彦清，3 条提交，实训一待批改 |
| student02 | Student#2026 | STUDENT | 李思远，3 条提交，id=2 用于演示越权 |
| student03 至 student10 | Student#2026 | STUDENT | 王雨桐、陈子豪、周欣怡、吴俊杰、徐嘉宁、孙浩然、郑雅琪、黄泽宇 |
| guest | Guest#2026 | STUDENT | 往届旁听生，已停用，登录返回 30002 |

种子数据共 14 个账号、28 条提交，其中 11 条待批改、17 条已批改，平均分 83.4。提交时间是“几天前的某个白天时刻”，用 `TIMESTAMP(CURDATE() - INTERVAL n DAY, '09:40:00')` 写，全部落在 08:00 到 20:00 之间；不用 `NOW()` 减小时数，那样清晨启动会算出凌晨的提交时间。`id=1` 留成待批改，课堂上直接批改它。

密码在 `data.sql` 里是 BCrypt 密文，明文只出现在讲义和 README 的账号表里。改密码要重新算哈希，不要手改那一列。

## 数据库

与 05-mybatis 共用 `backend_learning` 库，表加 `sec_` 前缀，两个模块的表不打架。

| 表 | 关键列 | 说明 |
| --- | --- | --- |
| `sec_role` | `code` 唯一 | STUDENT / TEACHER / ADMIN |
| `sec_user` | `username` 唯一、`password`、`enabled` | 账号，密码不可逆 |
| `sec_user_role` | 主键 `(user_id, role_id)` | 用户与角色多对多 |
| `sec_task` | `owner_id`、`status`、`score` | 任务，归属指向 `sec_user.id` |

两个设计点值得在课上点一句。

**归属用 id 不用用户名。** 用户名将来可能允许改，改了以后历史任务的“提交人”就跟着变；存 id 不会。页面要显示姓名时再 join 出来。

**权限点没有建表。** 角色与用户的绑定是运行期数据，天天变；权限点是代码资产，跟着接口一起评审、一起提交。放在 `Role` 枚举里，改权限就是改一行代码，review 时一眼能看到影响面。要配置化时再加 `sec_permission` 与 `sec_role_permission` 两张表，把 `Role.authorities()` 换成查库即可。

`schema.sql` 每次启动先删后建，`data.sql` 灌演示数据，主键写死，测试里引用任务 id 才稳定。真实项目该换成 Flyway 做版本化迁移，这一点在 14-deploy 里会再提。

## 跑起来

MySQL 先启动，数据源与 05-mybatis 一致，库会自动建。

```bash
# 终端一：后端 8009，启动时自动建表灌数据
mvn -pl 09-security spring-boot:run

# 终端二：前端 5173
cd 09-security/web
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。Vite 8 只监听 `::1`，用 `127.0.0.1:5173` 会连不上。

开发期跨域靠 Vite 代理，浏览器全程只访问 5173：

```ts
proxy: {
  '/api': { target: 'http://127.0.0.1:8009', changeOrigin: true },
  '/auth': { target: 'http://127.0.0.1:8009', changeOrigin: true },
  '/admin': { target: 'http://127.0.0.1:8009', changeOrigin: true },
}
```

三个前缀必须和实际路径一一对上。少了 `/admin`，管理接口的请求会打到 Vite 自己身上，返回 `index.html`，前端解析 JSON 失败，页面看着像“接口没数据”——这个坑我在这份代码里也踩过一次，注释写在那儿了。

上线分离部署时没有这层代理，才需要后端那份 CORS 配置（`SecurityConfig#corsConfigurationSource`）。

## 接口契约

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

响应统一是 `Result`：成功时 `{"code":200,"data":...,"message":"success"}`。业务失败也是 HTTP 200，靠 `code` 区分；越权则必须是真实的 403。

## 数据访问怎么写

`UserMapper` 与 `TaskMapper` 继承 `BaseMapper`，单表增删改查不用写 SQL：

```java
public interface UserMapper extends BaseMapper<User> {
}
```

查询用条件构造器，列名靠 `map-underscore-to-camel-case` 自动转驼峰：

```java
User user = userMapper.selectOne(
        new LambdaQueryWrapper<User>().eq(User::getUsername, username));
```

联表不硬套 Wrapper。任务要显示提交人姓名，三处查询都用同一个 join 片段：

```java
String SELECT_COLUMNS = """
        t.id, t.title, t.content, t.owner_id, t.status, t.score, t.comment,
        t.submitted_at, t.reviewed_at,
        u.username AS owner, u.nickname AS owner_nickname
        """;

@Select("SELECT " + SELECT_COLUMNS + FROM_JOIN + "WHERE t.owner_id = #{ownerId} ORDER BY t.submitted_at DESC")
List<TaskRow> selectRowsByOwnerId(@Param("ownerId") Long ownerId);
```

分层上有一条规矩：**实体不出 service**。`User` 只在 mapper 与 service 之间传，接口出参是 `UserProfile`，`password` 不可能顺着实体漏出去。安全层再隔一层 `Account`（用户 + 角色的领域对象），`SecurityUser` 就不必知道表结构。

新增账号写两张表，用事务保证一致：

```java
@Transactional(rollbackFor = Exception.class)
public UserProfile create(CreateUserRequest request) {
    if (findByUsername(request.username()).isPresent()) {
        throw new BusinessException(ErrorCode.USERNAME_EXISTS);
    }
    User user = new User();
    user.setUsername(request.username());
    user.setPassword(passwordEncoder.encode(request.password()));   // 明文只在这里出现一次
    user.setNickname(request.nickname());
    user.setEnabled(true);
    userMapper.insert(user);

    for (Role role : request.roles()) {
        Long roleId = roleMapper.selectIdByCode(role.name());
        userRoleMapper.insert(user.getId(), roleId);
    }
    ...
}
```

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

`sub` 是身份，`exp` 由解析时自动校验，`iss` 防止别的系统用同密钥签的令牌混进来。`roles` 只给前端显示菜单用，不参与后端鉴权：令牌签发后改不了，管理员把某人降级后旧令牌里还写着旧角色。

## 后端怎么认出你

```java
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                FilterChain filterChain) throws ServletException, IOException {
    String token = resolveToken(request);
    if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        String username = tokenService.parseUsername(token);   // 验签 + 校验过期与签发方
        UserDetails user = userDetailsService.loadUserByUsername(username);   // 这里回库
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

因为换成真库，每个请求会多两条 SQL（查人、查角色），日志里看得到。这是“令牌只放身份”的代价，也是改角色能立刻生效的原因。

令牌无效时不在这里返回 401，只清空上下文继续往下走，让授权判断拒绝这个匿名请求，401 由 `RestAuthenticationEntryPoint` 统一输出。

## 前端三件事

全部集中在 `src/api/request.ts`，四个页面不再关心鉴权。

```ts
request.interceptors.request.use((config) => {
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})
```

响应拦截把 401 和 403 分开：

```ts
request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>
    if (body.code === 200) return body.data as never
    notify(body.message || '操作失败')
    return Promise.reject(new Error(body.message || '操作失败'))
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    if (status === 401) {
      clearLogin()
      const from = router.currentRoute.value.fullPath
      router.push({ path: '/login', query: from === '/login' ? {} : { redirect: from } })
      notify(error.response?.data?.message || '登录已失效，请重新登录')
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

## 类型怎么串起来

类型写在 `src/types/api.ts`，一条条对着后端的 vo 与 dto 核过。改后端字段时先改这里，`npm run typecheck` 会把所有用到的地方指出来。

```ts
export interface TaskView {
  id: number
  title: string
  owner: string
  status: TaskStatusCode
  statusText: string
  score: number | null
  comment: string | null
  submittedAt: string
  reviewedAt: string | null
}
```

拦截器把 `Result` 拆成了 `data`，但 axios 的类型仍认为返回完整响应，所以在 `http` 工具里统一收敛一次，页面里就都是实打实的类型：

```ts
async function unwrap<T>(promise: Promise<unknown>): Promise<T> {
  return (await promise) as T
}

export const http = {
  get: <T>(url: string) => unwrap<T>(request.get(url)),
  post: <T>(url: string, data?: unknown) => unwrap<T>(request.post(url, data)),
  del: <T>(url: string) => unwrap<T>(request.del(url)),
}
```

接口清单因此可以一行一个，返回值类型直接写在签名上：

```ts
export const myTasks = () => http.get<TaskView[]>('/api/tasks/mine')
export const reviewTask = (id: number, data: ReviewTaskPayload) =>
  http.post<TaskView>(`/api/tasks/${id}/review`, data)
```

路由的 meta 也加了类型，角色名写错在编译期就报：

```ts
declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    roles?: RoleCode[]
  }
}
```

## 列表页的统计与筛选

学生页顶部四个数字、教师页那句“待批改 11 份”，都是前端从列表结果里算的，后端没有统计接口：

```ts
const stats = computed(() => {
  const reviewed = tasks.value.filter((task) => task.status === 'REVIEWED')
  const total = reviewed.reduce((sum, task) => sum + (task.score ?? 0), 0)
  return {
    total: tasks.value.length,
    pending: tasks.value.filter((task) => task.status === 'SUBMITTED').length,
    average: reviewed.length ? (total / reviewed.length).toFixed(1) : '—',
  }
})
```

筛选同理，一个 `filter` 值加一次数组过滤，不重新请求接口。这么做前提是列表一次拿全量，几百条以内没问题，切筛选也不闪。

列表一旦分页，这两件事都得挪走：平均分不能拿当前页算，第 2 页的数据也不在手里。那时统计要单独出聚合接口，筛选条件变成查询参数。`TaskService#listAll` 上留了这条注释。

## 数据归属

“学生只能看自己的”有三种写法，只有一种是对的：

| 做法 | 前端请求 | 后果 |
| --- | --- | --- |
| 前端传用户名 | `GET /api/tasks?owner=student01` | 改参数就能看别人的 |
| 路径带用户名 | `GET /api/tasks/mine/student01` | 改路径就能越权 |
| 服务端从令牌取 | `GET /api/tasks/mine` | URL 里没有身份，无处可改 |

```java
@GetMapping("/mine")
public Result<List<TaskView>> mine(@AuthenticationPrincipal SecurityUser user) {
    return Result.ok(taskService.listMine(user.id()));   // id 来自令牌，不是请求参数
}
```

提交任务时请求体里也没有 owner 字段，前端硬塞一个 `"owner":"student02"` 不会被采纳（测试 `ownerCannotBeForgedByBody` 就是验证这条）。

详情接口的编号一定可以被改，判断放在服务端：

```java
public TaskView detail(Long id, SecurityUser viewer) {
    TaskRow row = findRow(id);
    if (!canView(row, viewer)) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    return TaskView.of(row);
}

private boolean canView(TaskRow row, SecurityUser viewer) {
    if (row.getOwnerId().equals(viewer.id())) return true;
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

- `hasRole("ADMIN")` 内部会拼成 `ROLE_ADMIN` 比对，权限集合里必须有这个前缀；`hasAuthority("task:review")` 是原样比对，不能加前缀。
- 管理接口做了双重把关：URL 规则 `/admin/**` 限管理员，Controller 上再写一次 `@PreAuthorize`。

## 测试覆盖

`mvn -pl 09-security test`，22 个用例，跑在真实 MySQL 上：

| 分组 | 覆盖点 |
| --- | --- |
| 登录 | 放行接口、未带令牌 401、登录返回令牌与角色、密码错与用户不存在同提示、停用账号 30002、参数校验、令牌换身份、令牌被改后 401 |
| 数据归属 | 提交归属写死为当前用户、请求体伪造 owner 无效、只返回自己的、看别人的返回真实 403、能看自己详情 |
| 教师权限 | 学生不能看全部、不能批改、教师能看到全部、批改后学生立刻能看分数、同一份不能批改两次、教师不能提交、分数越界被拦 |
| 管理 | 教师进不了管理接口、管理员能看列表且响应无密码、新增学生能登录、不能删自己 |

前端类型检查：

```bash
cd 09-security/web && npm run typecheck
```

## 排错表

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动报 `Table 'sec_user' doesn't exist` | `spring.sql.init.mode` 没写 always | MySQL 不是内嵌库，必须显式声明 |
| 建表脚本没执行 | `schema-locations` 路径不对 | 放 `classpath:schema.sql` 并确认打包进 jar |
| `/admin/users` 返回一坨 HTML | Vite 代理漏配 `/admin` | 代理前缀与实际路径一一对齐 |
| 角色读出来是空集合 | `sec_user_role` 没数据或 join 写错 | 直接查库确认关联行在不在 |
| 未认证返回 302 | `formLogin` 没关 | `formLogin(AbstractHttpConfigurer::disable)` |
| 401 响应是空白 HTML | 没配 entryPoint | 指定 `RestAuthenticationEntryPoint` |
| 登录接口也 401 | 没放行 `/auth/login`，或规则顺序写反 | `permitAll` 写在 `anyRequest` 之前 |
| `hasRole` 永远不通过 | 少了 `ROLE_` 前缀 | 统一用 `Role.authority()` 生成 |
| 令牌 Header 是 HS384 | 用了 `signWith(key)` 单参数版 | 显式 `signWith(key, Jwts.SIG.HS256)` |
| 过滤器跑两遍 | 它既是 Bean 又被加进链 | 去掉 `@Component`，配置类里 `new` |
| `@PreAuthorize` 不生效 | 少 `@EnableMethodSecurity` | 加在配置类上 |
| 越权返回 200 而不是 403 | 处理器返回了裸 `Result` | 用 `ResponseEntity.status(403)` |
| Mapper 注入失败 | 启动类没 `@MapperScan` | 加在 `SecurityApplication` 上 |
| typecheck 报 axios 返回类型不对 | 拦截器改了运行时返回值，类型没跟上 | 在 `http` 工具里统一断言一次 |
| 前端一点就跳登录 | 把 403 也当成 401 | 拦截器里分开两个分支 |

## 课堂练习

**让教师也能提交一条教学示范。** 给 `Role.TEACHER` 加 `task:submit` 即可，只碰一行枚举，接口代码没动。顺势问：为什么不能把注解改成 `hasAnyAuthority('task:submit','task:review')`？能跑，但把业务规则藏进了接口注解里。

**让学生能撤回自己还没被批改的任务。** 加 `DELETE /api/tasks/{id}`：

```java
@Transactional(rollbackFor = Exception.class)
public void withdraw(Long id, SecurityUser user) {
    Task task = taskMapper.selectById(id);
    if (task == null) {
        throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
    }
    if (!task.getOwnerId().equals(user.id())) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    if (task.getStatus() == TaskStatus.REVIEWED) {
        throw new BusinessException(ErrorCode.TASK_ALREADY_REVIEWED);
    }
    taskMapper.deleteById(id);
}
```

检查三点：学生删别人的要 403；删已批改的要给业务提示；教师是否允许删要明确写出来。

**把令牌有效期改成 1 分钟。** 改配置 `app.jwt.expire-minutes: 1`，登录后等一分钟再操作，观察前端反应：401 → 清登录态 → 跳登录页 → 登录后回到原页面。

## 课后作业

**基础（20 分）登录失败次数限制。** 同一用户连续失败 5 次锁定 10 分钟，锁定期间返回新业务码，成功后计数清零。要求说明换到 Redis 后 key 怎么设计。

**进阶（30 分）任务列表分页。** 引入 `PaginationInnerInterceptor` 与 `mybatis-plus-jsqlparser`，把 `GET /api/tasks` 改成 `page`、`size` 参数，返回 `{ records, total }`；前端表格加分页。要求联表查询下分页仍正确（count 语句要能对上）。

**挑战（30 分）刷新令牌与登出。** 改密码或点退出后旧令牌立刻失效。两条路线选一：令牌版本号存用户表；或者维护黑名单，登出时把 `jti` 放进去。要求有测试证明改密码后旧令牌立即 401。

## 已知边界

- 没有刷新令牌，令牌有效期 120 分钟，到期只能重新登录。
- 没有登出黑名单，退出只清前端存储，令牌在到期前仍然可用。
- 没有登录失败限流，登录接口可以被反复试。
- 列表不分页，28 条数据一次返回，筛选与统计都在前端算；数据量上去要改成后端分页加聚合接口。
- 前端只做了菜单与路由级权限，没做按钮级控制。
- `schema.sql` 每次启动重建表，数据不能留存；生产环境应换 Flyway。
