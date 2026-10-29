# 09-security 完整实现手册

这份文档自成一体。只拿它和 AI，就能做出结构一致、行为一致的前后端。不需要看别的文件，也不需要任何脚本。

分五部分：项目全貌、规格、二十步、验收、常见错误。第三部分是操作主体，前两部分是每一步都要用到的参照，第四部分做完之后验收，第五部分卡住时查。

## 怎么用

**AI 当实现者，你当设计者。** 每步的提示词写死了文件名、类名、方法签名，这些是你给 AI 的边界。实现的逻辑由它写，跑不通你自己改。

**一次只发一个步骤。** 二十步一次性丢给 AI，它会合并且删减，最后结构对不上。

**每步都有验收命令，过不了别走下一步。** 第 09 步登录不通就写前端，后面只会更难查。

**版本要报准。** 不说版本，AI 给你的多半是 Spring Security 5 的写法，那个年代的配置基类在 6 之后已经删了。

## 准备

数据库 `backend_learning` 先建好，MySQL 要跑起来，账号密码记下来。

提示词里的版本按本机实际填：Spring Boot 4.1.1、Spring Security 7.1.1、MyBatis-Plus 3.5.17、jjwt 0.13.0、MySQL 8.4、Vue 3.5.44、Vite 8.3.5、TypeScript 5.9.3。

第 01 步假设你是在现有工程里加模块。如果是新建独立工程，把 “父 POM 是 spring-boot-starter-parent 4.1.1” 说成 “用 Spring Initializr 生成，版本 4.1.1，打包方式 jar”，其余不变。

后端端口 8009，前端 5173。本模块所有表加 `sec_` 前缀，与其他模块共用一个库互不干扰。

---

# 第一部分 · 项目全貌

## 1.1 目录结构

后端：

```text
09-security/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/top/mqxu/security/
    │   │   ├── SecurityApplication.java
    │   │   ├── common/
    │   │   │   ├── BusinessException.java
    │   │   │   ├── ErrorCode.java
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── Result.java
    │   │   ├── config/
    │   │   │   ├── JwtProperties.java
    │   │   │   └── SecurityConfig.java
    │   │   ├── controller/
    │   │   │   ├── AuthController.java
    │   │   │   ├── PublicController.java
    │   │   │   ├── TaskController.java
    │   │   │   └── UserAdminController.java
    │   │   ├── dto/
    │   │   │   ├── CreateUserRequest.java
    │   │   │   ├── LoginRequest.java
    │   │   │   ├── ReviewTaskRequest.java
    │   │   │   └── SubmitTaskRequest.java
    │   │   ├── entity/
    │   │   │   ├── Task.java
    │   │   │   └── User.java
    │   │   ├── mapper/
    │   │   │   ├── RoleMapper.java
    │   │   │   ├── TaskMapper.java
    │   │   │   ├── UserMapper.java
    │   │   │   └── UserRoleMapper.java
    │   │   ├── model/
    │   │   │   ├── Account.java
    │   │   │   ├── Role.java
    │   │   │   ├── TaskRow.java
    │   │   │   ├── TaskStatus.java
    │   │   │   └── UserRoleEntry.java
    │   │   ├── security/
    │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   ├── JwtTokenService.java
    │   │   │   ├── RestAccessDeniedHandler.java
    │   │   │   ├── RestAuthenticationEntryPoint.java
    │   │   │   ├── RestErrorWriter.java
    │   │   │   └── SecurityUser.java
    │   │   ├── service/
    │   │   │   ├── AccountUserDetailsService.java
    │   │   │   ├── AuthService.java
    │   │   │   ├── TaskService.java
    │   │   │   └── UserService.java
    │   │   └── vo/
    │   │       ├── LoginResponse.java
    │   │       ├── TaskView.java
    │   │       └── UserProfile.java
    │   └── resources/
    │       ├── application.yml
    │       ├── data.sql
    │       └── schema.sql
    └── test/java/top/mqxu/security/
        └── SecurityApiTest.java
```

前端：

```text
09-security/web/
├── index.html
├── package.json
├── tsconfig.json
├── tsconfig.node.json
├── vite.config.ts
└── src/
    ├── App.vue
    ├── env.d.ts
    ├── main.ts
    ├── style.css
    ├── api/
    │   ├── index.ts
    │   └── request.ts
    ├── router/
    │   └── index.ts
    ├── store/
    │   ├── auth.ts
    │   └── toast.ts
    ├── types/
    │   └── api.ts
    └── views/
        ├── Login.vue
        ├── ReviewList.vue
        ├── TaskList.vue
        └── Users.vue
```

## 1.2 后端文件与关键成员

提示词里的类名、方法名一律以此表为准，不要改名。

**启动与配置**

| 文件 | 职责 | 关键成员 |
| --- | --- | --- |
| `SecurityApplication.java` | 启动类 | `@SpringBootApplication`、`@MapperScan("top.mqxu.security.mapper")` |
| `config/JwtProperties.java` | 绑 `app.jwt` 前缀 | record：`secret`、`issuer`、`expireMinutes` |
| `config/SecurityConfig.java` | 过滤链、密码编码器、认证管理器、跨域 | `passwordEncoder()`、`authenticationManager(UserDetailsService, PasswordEncoder)`、`securityFilterChain(...)`、`corsConfigurationSource()` |

**common**

| 文件 | 职责 | 关键成员 |
| --- | --- | --- |
| `Result.java` | 统一响应外壳 | 字段 `code`、`message`、`data`；`ok()`、`ok(T)`、`fail(ErrorCode)`、`fail(Integer, String)` |
| `ErrorCode.java` | 错误码枚举 | 见 2.4 |
| `BusinessException.java` | 业务异常 | 构造 `(ErrorCode)`、`(ErrorCode, String)` |
| `GlobalExceptionHandler.java` | 全局异常映射 | 七个处理方法，见下 |

`GlobalExceptionHandler` 处理七类：`BusinessException` 按错误码给状态码、`MethodArgumentNotValidException` 给 400 加第一个字段的中文提示、`MethodArgumentTypeMismatchException`、`HttpMessageNotReadableException`、`AccessDeniedException` 给 403、`NoResourceFoundException` 给 404、`Exception` 给 500。

**entity**

| 文件 | 职责 | 关键成员 |
| --- | --- | --- |
| `User.java` | 映射 `sec_user` | `id`、`username`、`password`、`nickname`、`enabled`、`createdAt` |
| `Task.java` | 映射 `sec_task` | `id`、`title`、`content`、`ownerId`、`status`、`score`、`comment`、`submittedAt`、`reviewedAt`；`markReviewed(int, String, LocalDateTime)` |

**model**

| 文件 | 职责 | 关键成员 |
| --- | --- | --- |
| `Role.java` | 角色与权限点 | 常量 `STUDENT`、`TEACHER`、`ADMIN`；`permissions()`、`authority()`、`static authorities(Collection<Role>)` |
| `Account.java` | 账号与角色的组合 | record：`id`、`username`、`password`、`nickname`、`enabled`、`Set<Role> roles`；`roleNames()` |
| `TaskStatus.java` | 状态与中文 | `SUBMITTED("待批改")`、`REVIEWED("已批改")`；`text()` |
| `TaskRow.java` | 联表查询投影 | `id`、`title`、`content`、`ownerId`、`owner`、`ownerNickname`、`status`、`score`、`comment`、`submittedAt`、`reviewedAt` |
| `UserRoleEntry.java` | 角色查询投影 | `userId`、`roleCode` |

**mapper**

| 文件 | 职责 | 关键成员 |
| --- | --- | --- |
| `UserMapper.java` | 账号单表 CRUD | `extends BaseMapper<User>` |
| `TaskMapper.java` | 任务查询与更新 | `extends BaseMapper<Task>`；`selectAllRows()`、`selectRowsByOwnerId(Long)`、`selectRowById(Long)`、`updateReviewed(Long, Integer, String, LocalDateTime)` |
| `RoleMapper.java` | 角色查询 | `selectIdByCode(String)`、`selectAllCodes()` |
| `UserRoleMapper.java` | 关联表 | `selectRoleCodesByUserId(Long)`、`insert(Long, Long)`、`deleteByUserId(Long)` |

三个联表查询用 `@Select` 注解，不套条件构造器。`TaskMapper` 用 `SELECT_COLUMNS` 与 `FROM_JOIN` 两个常量拼公共部分，三处复用。

**security**

| 文件 | 职责 | 关键成员 |
| --- | --- | --- |
| `JwtTokenService.java` | 签发与解析 | `issue(UserDetails)` 返回 `IssuedToken`（record：`token`、`expiresAt`）、`parseUsername(String)` |
| `JwtAuthenticationFilter.java` | 取令牌、验签、放上下文 | `extends OncePerRequestFilter` |
| `SecurityUser.java` | 用户适配 | `implements UserDetails`；`account()`、`id()`、`roleNames()`、`getAuthorities()`、五个 `isXxx` |
| `RestAuthenticationEntryPoint.java` | 未认证返回 401 JSON | `implements AuthenticationEntryPoint`，`commence(...)` |
| `RestAccessDeniedHandler.java` | 越权返回 403 JSON | `implements AccessDeniedHandler`，`handle(...)` |
| `RestErrorWriter.java` | 写 JSON 的公共方法 | `write(HttpServletResponse, HttpStatus, Result<Void>)` |

**service**

| 文件 | 职责 | 关键成员 |
| --- | --- | --- |
| `AccountUserDetailsService.java` | 按用户名取账号 | `implements UserDetailsService`，`loadUserByUsername(String)` |
| `AuthService.java` | 登录换令牌 | `login(LoginRequest)` 返回 `LoginResponse` |
| `TaskService.java` | 任务业务与归属判断 | `submit(SubmitTaskRequest, SecurityUser)`、`listMine(Long)`、`listAll()`、`detail(Long, SecurityUser)`、`review(Long, ReviewTaskRequest)` |
| `UserService.java` | 账号管理 | `findByUsername(String)` 返回 `Optional<Account>`、`listProfiles()`、`create(CreateUserRequest)`、`delete(String, String)` |

**controller、dto、vo**

| 文件 | 职责 | 关键成员 |
| --- | --- | --- |
| `AuthController.java` | `/auth` | `login(LoginRequest)`、`me(SecurityUser)` |
| `PublicController.java` | `/public` | `ping()` |
| `TaskController.java` | `/api/tasks` | `submit`、`mine`、`all`、`detail`、`review` |
| `UserAdminController.java` | `/admin/users` | `list()`、`create(CreateUserRequest)`、`delete(String, SecurityUser)` |
| `LoginRequest.java` | 登录入参 | `username`、`password`，都 `@NotBlank` |
| `SubmitTaskRequest.java` | 提交入参 | `title`（`@NotBlank` `@Size(max=100)`）、`content`（`@NotBlank` `@Size(max=2000)`） |
| `ReviewTaskRequest.java` | 批改入参 | `score`（`@NotNull` `@Min(0)` `@Max(100)`）、`comment`（`@Size(max=500)`） |
| `CreateUserRequest.java` | 建账号入参 | `username`（`@Size(min=3,max=32)`）、`password`（`@Size(min=6,max=64)`）、`nickname`、`Set<String> roles` |
| `LoginResponse.java` | 登录出参 | record：`token`、`tokenType`、`expiresAt`、`username`、`nickname`、`Set<String> roles` |
| `UserProfile.java` | 账号出参 | record：`username`、`nickname`、`Set<String> roles`、`List<String> authorities`、`enabled`；`static of(Account)` |
| `TaskView.java` | 任务出参 | record 十二个字段，见 2.2；`static of(TaskRow)` |

## 1.3 前端文件与关键成员

| 文件 | 职责 | 关键成员 |
| --- | --- | --- |
| `src/types/api.ts` | 与后端对齐的类型 | `RoleCode`、`TaskStatusCode`、`ApiResult<T>`、`LoginPayload`、`LoginResponse`、`CurrentUser`、`UserProfile`、`TaskView`、`SubmitTaskPayload`、`ReviewTaskPayload`、`CreateUserPayload` |
| `src/api/request.ts` | axios 与拦截器 | 导出 `http`，含 `get`、`post`、`del` 三个泛型方法 |
| `src/api/index.ts` | 接口清单 | `login`、`me`、`submitTask`、`myTasks`、`taskDetail`、`allTasks`、`reviewTask`、`listUsers`、`createUser`、`deleteUser` |
| `src/store/auth.ts` | 登录态 | `auth`（reactive）、`saveLogin(LoginResponse)`、`saveUser(UserProfile)`、`clearLogin()`、`hasRole(RoleCode)`、`hasAnyRole(roles)` |
| `src/store/toast.ts` | 轻提示 | `toasts`、`notify(String)` |
| `src/router/index.ts` | 路由与守卫 | 见 2.7 |
| `src/App.vue` | 外壳与顶栏 | 按角色显示菜单、挂 toast 容器 |
| `src/views/Login.vue` | 登录页 | 表单、三个快捷账号按钮 |
| `src/views/TaskList.vue` | 学生页 | 列表、提交、详情、统计条 |
| `src/views/ReviewList.vue` | 教师页 | 全部列表、筛选、打分 |
| `src/views/Users.vue` | 账号页 | 列表、新增、删除 |
| `src/main.ts` | 入口 | 挂路由、引样式 |
| `src/style.css` | 全局样式 | 手写，不引组件库 |
| `src/env.d.ts` | 类型声明 | `*.vue` 模块声明 |

前后端字段对应：`Role` 枚举对 `RoleCode`、`TaskStatus` 对 `TaskStatusCode`、`Result<T>` 对 `ApiResult<T>`，`TaskView`、`UserProfile`、`LoginResponse` 三个出参同名同字段。改后端字段时先改 `types/api.ts`，`npm run typecheck` 会把用到的地方全指出来。

---

# 第二部分 · 规格

## 2.1 数据表

四张表，引擎 InnoDB，字符集 utf8mb4。

**sec_role**

| 列 | 类型 | 约束 |
| --- | --- | --- |
| id | BIGINT UNSIGNED | 主键，自增 |
| code | VARCHAR(20) | 非空，唯一，值与 `Role` 枚举同名 |
| name | VARCHAR(50) | 非空，给页面显示 |

**sec_user**

| 列 | 类型 | 约束 |
| --- | --- | --- |
| id | BIGINT UNSIGNED | 主键，自增 |
| username | VARCHAR(50) | 非空，唯一 |
| password | VARCHAR(100) | 非空，BCrypt 密文。定长 60，留 100 以便换更长的算法 |
| nickname | VARCHAR(50) | 非空 |
| enabled | TINYINT | 非空，默认 1。0 表示停用，登录被拒 |
| created_at | DATETIME | 非空，默认当前时间 |

**sec_user_role**

| 列 | 类型 | 约束 |
| --- | --- | --- |
| user_id | BIGINT UNSIGNED | 联合主键 |
| role_id | BIGINT UNSIGNED | 联合主键，另建普通索引 |

**sec_task**

| 列 | 类型 | 约束 |
| --- | --- | --- |
| id | BIGINT UNSIGNED | 主键，自增 |
| title | VARCHAR(100) | 非空 |
| content | VARCHAR(2000) | 非空 |
| owner_id | BIGINT UNSIGNED | 非空，指向 `sec_user.id`，建索引 |
| status | VARCHAR(20) | 非空，默认 `SUBMITTED` |
| score | TINYINT UNSIGNED | 可空，0 到 100 |
| comment | VARCHAR(500) | 可空 |
| submitted_at | DATETIME | 非空，默认当前时间 |
| reviewed_at | DATETIME | 可空 |

`schema.sql` 每次启动先删后建，`data.sql` 灌演示数据。建表顺序反过来删，先删有外键指向的表。

种子数据的时间用 `DATE_SUB` 配合固定时刻，不要用 `NOW()` 减小时数。清晨启动会算出凌晨三五点，页面上一条凌晨的提交记录一眼就假。

## 2.2 接口

十一组。方法、路径、入参、出参、权限都定死。

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| POST | `/auth/login` | `username`、`password` | `LoginResponse` | 放行 |
| GET | `/auth/me` | 无 | `UserProfile` | 已认证 |
| GET | `/public/ping` | 无 | `{"status":"ok"}` | 放行 |
| POST | `/api/tasks` | `title`、`content` | `TaskView` | `task:submit` |
| GET | `/api/tasks/mine` | 无 | `TaskView[]` | 已认证 |
| GET | `/api/tasks/{id}` | 路径 id | `TaskView` | 本人或 `task:review` |
| GET | `/api/tasks` | 无 | `TaskView[]` | `task:review` |
| POST | `/api/tasks/{id}/review` | `score`、`comment` | `TaskView` | `task:review` |
| GET | `/admin/users` | 无 | `UserProfile[]` | ADMIN |
| POST | `/admin/users` | `username`、`password`、`nickname`、`roles` | `UserProfile` | ADMIN 加 `user:write` |
| DELETE | `/admin/users/{username}` | 路径 username | 空 | ADMIN |

`TaskView` 十二个字段：

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| id | Long | 否 | |
| title | String | 否 | |
| content | String | 否 | |
| ownerId | Long | 否 | |
| owner | String | 否 | 提交人登录名 |
| ownerNickname | String | 否 | 提交人姓名 |
| status | String | 否 | `SUBMITTED` 或 `REVIEWED` |
| statusText | String | 否 | 待批改 或 已批改 |
| score | Integer | 是 | |
| comment | String | 是 | |
| submittedAt | LocalDateTime | 否 | |
| reviewedAt | LocalDateTime | 是 | |

`UserProfile` 五个字段：`username`、`nickname`、`roles`（角色名集合）、`authorities`（角色名加权限点，含 `ROLE_` 前缀）、`enabled`。任何接口的响应里都不能出现 `password`。

`LoginResponse` 六个字段：`token`、`tokenType`（固定 `Bearer`）、`expiresAt`、`username`、`nickname`、`roles`。

## 2.3 权限矩阵

三个角色，五个权限点，命名是“资源:动作”。

| 角色 | 权限点 |
| --- | --- |
| STUDENT | `task:submit`、`task:read` |
| TEACHER | `task:review`、`task:read` |
| ADMIN | `user:read`、`user:write`、`task:read`、`task:review` |

接口绑权限点，不绑角色。将来多一类“教务”，能看全部不能批改，配 `task:read` 就够，接口代码不动。

权限点放在 `Role` 枚举里，不建表。理由：角色与用户的绑定是运行期数据，天天变；权限点是代码资产，跟着接口一起评审。要改成配置化，加两张表，把 `Role.authorities()` 换成查库即可。

## 2.4 错误码

| code | 含义 |
| --- | --- |
| 200 | 成功 |
| 400 | 参数校验失败，message 说明哪一项 |
| 401 | 未认证或令牌失效 |
| 403 | 已认证但无权访问 |
| 404 | 资源不存在 |
| 500 | 服务端异常 |
| 30001 | 用户名或密码错误 |
| 30002 | 账号已被禁用 |
| 30003 | 用户不存在 |
| 30004 | 用户名已存在 |
| 30005 | 不能删除当前登录账号 |
| 30010 | 任务不存在 |
| 30011 | 同一任务已提交，等待批改 |
| 30012 | 该任务已批改，不能重复批改 |

`30001` 同时覆盖密码错和用户不存在。分开提示等于告诉攻击者哪些用户名存在。

## 2.5 响应格式

所有接口同一个外壳：

```json
{ "code": 200, "message": "success", "data": {} }
```

业务失败走 HTTP 200 加业务码。只有两种情况用真实状态码：未认证 401，越权 403。

这条要守住。学生看别人的任务如果返回 200 加错误码，前端就得猜什么时候跳登录、什么时候弹提示，拦截器没法写。

## 2.6 三条不变式

实现过程中反复回看这三条，违反了后面一定出问题。

**一、令牌只放身份，不放权限。** 载荷放用户名、签发时间、过期时间、签发者、角色列表。权限每次请求按用户名回库查，改角色立刻生效。代价是每个请求多两条 SQL。

**二、归属判断在服务层。** “这条数据归谁”是业务规则，控制层只收参数、调服务、返结果。写在控制层，多一个入口就得抄一遍。

**三、密码不出现三次。** 库里存密文、实体不出服务层、出参用视图对象。三道关都在，泄漏不了。

## 2.7 前端路由

| 路径 | 组件 | 元信息 |
| --- | --- | --- |
| `/login` | `Login.vue` | `public: true` |
| `/tasks` | `TaskList.vue` | `roles: ['STUDENT']` |
| `/review` | `ReviewList.vue` | `roles: ['TEACHER', 'ADMIN']` |
| `/users` | `Users.vue` | `roles: ['ADMIN']` |
| `/` | 重定向 | 按角色跳到各自首页 |
| `/:pathMatch(.*)*` | 重定向 | 兜底到登录页 |

守卫拦两件事：`public` 为真的页面放行，其余先看有没有令牌，再看角色是否在 `meta.roles` 里。拦下来时跳登录页，把原地址放进 `query.redirect`。

---

# 第三部分 · 二十步

每一步给四样：这一步做什么、发给 AI 的提示词（含产出清单）、产出落到哪些文件、怎么验证。提示词整段复制，只把版本号按本机改掉。

---

## 一、打地基

### 01 · 建模块，把依赖版本定死

六个依赖，哪些版本由父 POM 管。

问 AI：

```text
Maven 多模块工程，父 POM 是 spring-boot-starter-parent 4.1.1，我要加一个模块 09-security。

依赖清单：
- spring-boot-starter-security
- spring-boot-starter-validation
- com.baomidou:mybatis-plus-spring-boot4-starter
- com.mysql:mysql-connector-j
- io.jsonwebtoken:jjwt-api、jjwt-impl、jjwt-jackson

问题：
1. 哪些不用写版本号，谁在管？
2. jjwt 三个包各自的 scope 应该是什么，为什么 impl 和 jackson 不需要编译期可见？
3. 这个工程同时有 Jackson 2 和 Jackson 3，两者会冲突吗？

用一张表回答：groupId、artifactId、版本、scope、谁管版本。

产出：09-security/pom.xml。
```

产出文件：`09-security/pom.xml`。`jjwt.version` 与 `mybatis-plus.version` 写成 properties，其余不写版本。

验证：

```bash
mvn -pl 09-security dependency:tree | grep -E 'security|jjwt|mybatis'
```

看到 Security 7.1.1、jjwt 0.13.0、MyBatis-Plus 3.5.17 才算对。

### 02 · 数据源、建表与种子数据

四张表、十四个账号、二十八条任务。

问 AI：

```text
MySQL 8.4，库 backend_learning，要建四张表，表名前缀 sec_：
sec_role（id、code 唯一、name）、sec_user（id、username 唯一、password、nickname、enabled、created_at）、
sec_user_role（user_id 与 role_id 联合主键）、sec_task（id、title、content、owner_id、status、score、comment、submitted_at、reviewed_at）。
账号密码存 BCrypt 密文，任务状态只有 SUBMITTED 和 REVIEWED。

问题：
1. 角色与账号多对多，关联表主键用联合主键还是自增 id？
2. 权限点（task:submit、task:review 这类）建表存还是写在代码枚举里？从“经常改”和“很少改”两个维度讲代价。
3. Spring Boot 的 sql.init 对 MySQL 这种外部库，mode 为什么必须显式写 always？

产出：
src/main/resources/schema.sql、src/main/resources/data.sql，
application.yml 里的 spring.sql.init 段，mode 写 always，指向这两个文件。
```

产出文件：`schema.sql`、`data.sql`、`application.yml`。

种子数据十四个账号：admin、teacher01、teacher02、student01 到 student10、guest（停用）。密码明文分别是 `Admin#2026`、`Teacher#2026`、`Student#2026`、`Guest#2026`，库里存 BCrypt 密文。任务二十八条，十一条待批改、十七条已批改。

验证：

```sql
SELECT COUNT(*) FROM sec_user;                  -- 14
SELECT LEFT(password, 4) FROM sec_user LIMIT 1; -- $2a$
SELECT status, COUNT(*) FROM sec_task GROUP BY status;
```

### 03 · 统一响应体与错误码

所有接口一个外壳，错误码成表。

问 AI：

```text
前后端分离，令牌认证，Spring Boot 4.1.1。

问题：业务失败（密码错误、任务已批改这类）返回 HTTP 200 加业务码，还是返回对应 4xx？两种做法下前端的响应拦截器分别怎么写？

有个约束：有一种情况无论如何都得用真实状态码，不做成 200 包业务码。你判断是哪种，说明理由。

产出：
common 包下四个类：
- Result<T>，字段 code、message、data，静态方法 ok()、ok(T)、fail(ErrorCode)、fail(Integer, String)
- ErrorCode 枚举，业务错误用 3 开头五位数
- BusinessException，两个构造
- GlobalExceptionHandler，处理七类异常
```

产出文件：`common/Result.java`、`common/ErrorCode.java`、`common/BusinessException.java`、`common/GlobalExceptionHandler.java`。

`ErrorCode` 的 4xx、5xx 与 HTTP 状态码对齐，业务错误另立一段。密码错误与用户不存在共用 30001。

验证：

```bash
curl -i http://127.0.0.1:8009/public/ping --noproxy '*'
```

返回 `{"code":200,"message":"success","data":{"status":"ok"}}`。

---

## 二、认证

### 04 · 表映射与 Mapper

单表 CRUD 好办，联表要选写法。

问 AI：

```text
MyBatis-Plus 3.5.17，Spring Boot 4。两张表 sec_user 和 sec_task，另有关联表 sec_user_role。

要做三件事：
1. 账号与任务的单表增删改查
2. 按用户名查账号，再查它的角色 code 列表
3. 任务列表要 join 出提交人的登录名和姓名

问题：
1. 联表查询在 MyBatis-Plus 里有几种写法？
2. 什么场景宁可写注解 SQL 也不要用条件构造器拼子查询？给个具体判断标准。
3. 表名下划线、实体驼峰，全局配置里哪个开关负责，配错了会出什么现象？

约束：不要用 XML 映射文件。

产出：
entity 包 User、Task 两个实体，字段与表列一一对应，下划线转驼峰靠全局配置；
mapper 包 UserMapper、TaskMapper 继承 BaseMapper，RoleMapper、UserRoleMapper 用 @Select 注解写查询。
TaskMapper 里用 SELECT_COLUMNS 和 FROM_JOIN 两个常量拼公共部分，提供 selectAllRows、selectRowsByOwnerId、selectRowById、updateReviewed 四个方法。
```

产出文件：`entity/User.java`、`entity/Task.java`、`mapper/` 下四个接口。

联表投影用 `model/TaskRow`，十一个字段。

验证：启动后日志里能看到 MyBatis 打印的 SQL，`sql.init` 的两个脚本执行成功。

### 05 · 角色、账号与用户适配

两个决定会影响后面所有代码。

问 AI：

```text
Spring Security 7.1.1。我有账号表 sec_user 和关联表 sec_user_role，三个角色 STUDENT、TEACHER、ADMIN，
五个权限点 task:submit、task:read、task:review、user:read、user:write。

现在要把“账号 + 角色”交给框架。问题：

1. 让账号实体直接实现 UserDetails，还是另写一个类？各自会在什么情况下出问题？
2. getAuthorities() 返回的字符串集合里，角色名和权限点分别用什么形态？为什么角色名要多一个前缀？
3. 认证时需要“账号 + 角色”两样，实体只有一张表的信息。这一层怎么隔开比较干净？

产出：
model/Role.java：枚举，三个常量各带一个 Set<String> 权限点，方法 permissions()、authority()（返回 ROLE_ 前缀加角色名）、静态 authorities(Collection<Role>) 把角色摊平成权限字符串；
model/Account.java：record，字段 id、username、password、nickname、enabled、Set<Role> roles，方法 roleNames()；
security/SecurityUser.java：实现 UserDetails，包着 Account，提供 account()、id()、roleNames()；
service/AccountUserDetailsService.java：实现 UserDetailsService，按用户名查出 Account 再包成 SecurityUser；
service/UserService.java 先只写 findByUsername(String) 返回 Optional<Account>。
```

产出文件：`model/Role.java`、`model/Account.java`、`security/SecurityUser.java`、`service/AccountUserDetailsService.java`，以及 `UserService.findByUsername`。

验证：登录后临时打印一次 `getAuthorities()`，确认里面既有 `ROLE_STUDENT` 也有 `task:submit`。这一步还没有登录接口，可以先在 `loadUserByUsername` 里打日志，跑一次测试看输出。

### 06 · 令牌的签发与解析

载荷里放什么，是个设计决定。

问 AI：

```text
jjwt 0.13.0，HS256。要签发一张令牌，载荷放用户名（sub）、签发时间、过期时间、签发者，以及给前端直接显示的角色列表。

问题：
1. 载荷里要不要放权限点？放与不放，每个请求的代价差在哪？
2. 过期时间设多长合适？依据是什么？
3. HS256 对密钥长度有硬性要求吗，多少位？
4. 0.13.0 里签发和解析的 API 与老版本比有哪些废弃变化，我该避开的坑是什么？

产出：
config/JwtProperties.java：record，绑 app.jwt 前缀，字段 secret、issuer、expireMinutes；
security/JwtTokenService.java：issue(UserDetails) 返回内部 record IssuedToken(token, expiresAt)，parseUsername(String) 返回用户名，
密钥从配置读、支持环境变量覆盖，验签失败要抛异常而不是返回 null。
```

产出文件：`config/JwtProperties.java`、`security/JwtTokenService.java`，`application.yml` 加 `app.jwt` 三段。

配置示例：`secret` 默认值给一串足够长的字符串，生产用 `JWT_SECRET` 环境变量覆盖；`issuer` 写工程名；`expire-minutes` 120。

验证：签一张令牌、解析回来、故意改末位三位确认抛异常。

### 07 · 令牌过滤器与 401、403 的输出

最容易被写出“返回一坨 HTML”的一步。

问 AI：

```text
Spring Security 7.1.1，无状态 JWT。需要一个过滤器：从 Authorization 头取 Bearer 令牌，验签，按用户名查账号，放进 SecurityContext。

问题：
1. 令牌无效时，过滤器里直接写响应返回 401，还是交给后面处理？两种做法对“已认证但越权”的场景分别有什么影响？
2. 框架默认未认证会跳登录页，前后端分离时返回的是 HTML 页面。要改成 JSON，该实现哪个接口？越权那个又该实现哪个？
3. 继承 OncePerRequestFilter 有什么讲究？不继承行不行？
4. 过滤器里抛异常会发生什么？

产出：
security/JwtAuthenticationFilter.java：继承 OncePerRequestFilter，令牌无效时不中断，交后续处理；
security/RestAuthenticationEntryPoint.java：实现 AuthenticationEntryPoint，返回 401 加统一外壳；
security/RestAccessDeniedHandler.java：实现 AccessDeniedHandler，返回 403 加统一外壳；
security/RestErrorWriter.java：一个公共方法 write(response, status, body)，两个处理器共用。
```

产出文件：`security/` 下四个类。

验证：

```bash
curl -i http://127.0.0.1:8009/api/tasks/mine --noproxy '*'
```

401，且 `Content-Type: application/json`。如果是 `text/html`，说明默认登录页还在接管。

### 08 · 把过滤链装起来

规则顺序错了，后面全废。这一步还要把权限点挂到接口上。

问 AI：

```text
Spring Security 7.1.1 组件式配置。接口分组：
/auth/login 和 /public/** 放行，/admin/** 只给管理员，/api/tasks/** 要登录，其余都要登录。

问题：
1. 规则顺序有什么讲究？anyRequest 放前面会发生什么？
2. 我打算 /admin/** 在 URL 规则里拦一次，Controller 类上再标一次权限注解。这样重复的坏处和好处各是什么？
3. 关掉 CSRF 的前提是什么？以后改回 session 认证忘了打开会有什么后果？
4. 无状态这个设置在哪里配，不配会怎样？

答完再回答：
方法级鉴权默认是开还是关？开关在哪？不开会有什么现象——是报错还是不报错？
表达式里比对角色和比对权限点，两个方法有什么差别？混用会出现什么结果？

产出：
config/SecurityConfig.java 一个类，五个东西：
密码编码器 Bean（BCryptPasswordEncoder）；
认证管理器 Bean，构造参数是 UserDetailsService 与 PasswordEncoder，内部 new DaoAuthenticationProvider，不要单独注册 Provider Bean；
跨域配置 Bean，允许 localhost 与 127.0.0.1 任意端口；
过滤链 Bean：csrf 关、cors 开、httpBasic 关、formLogin 关、logout 关、无状态会话，
规则从具体到宽泛排列，异常处理挂上一步的两个处理器，JWT 过滤器加在用户名密码过滤器之前；
配置类上要打开方法级鉴权。
```

产出文件：`config/SecurityConfig.java`。

两步验证。先只写 URL 规则：

```bash
curl -i http://127.0.0.1:8009/public/ping --noproxy '*'     # 200
curl -i http://127.0.0.1:8009/api/tasks/mine --noproxy '*'  # 401
curl -i http://127.0.0.1:8009/admin/users --noproxy '*'     # 401
```

再在 Controller 上标权限注解。这时还没有登录接口，注解效果验不出来，等第 09 步一起验。

这一关有个开关漏了会让全部权限注解静默失效，程序照跑不报错，就是配置类上那个打开方法级鉴权的注解。

### 09 · 登录接口

把前面几步串起来。

问 AI：

```text
接上令牌服务，写 POST /auth/login，收 username、password，返回令牌。

规则：密码错和用户名不存在必须给同一个错误码 30001，账号停用单独给 30002。

问题：
1. 密码比对和停用检查交给框架的认证管理器做，还是自己在 Service 里比对？两种做法的失败提示怎么统一成一个？
2. 认证失败会抛哪几类异常，分别怎么映射到 30001 和 30002？
3. 认证管理器需要哪些 Bean？如果单独注册成 Provider Bean 会有什么副作用？

产出：
dto/LoginRequest.java：record，username、password 都 @NotBlank；
vo/LoginResponse.java：record，token、tokenType、expiresAt、username、nickname、Set<String> roles；
service/AuthService.java：login(LoginRequest) 返回 LoginResponse，内部调认证管理器校验，再调令牌服务签发；
controller/AuthController.java：POST /auth/login 与 GET /auth/me 两个方法，me 从 @AuthenticationPrincipal 取当前用户。
```

产出文件：`dto/LoginRequest.java`、`vo/LoginResponse.java`、`service/AuthService.java`、`controller/AuthController.java`，`vo/UserProfile.java` 也在这一步建好。

`UserProfile` 用静态工厂 `of(Account)` 从 `Account` 转，字段不含密码。`/auth/me` 返回它。

验证认证十条：

```bash
B=http://127.0.0.1:8009
curl -s -o /dev/null -w '01 %{http_code}\n' $B/public/ping --noproxy '*'
curl -s -o /dev/null -w '02 %{http_code}\n' $B/api/tasks/mine --noproxy '*'
curl -s -o /dev/null -w '03 %{http_code}\n' $B/api/tasks --noproxy '*'
curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"student01","password":"Student#2026"}' --noproxy '*'
curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"student01","password":"wrong"}' --noproxy '*'
curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"nobody","password":"x"}' --noproxy '*'
curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"guest","password":"Guest#2026"}' --noproxy '*'
curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"student01"}' --noproxy '*'
```

期望依次是：200、401、401、返回 token 与 roles、`code` 30001、`code` 30001、`code` 30002、`code` 400 与“密码不能为空”。

再验带令牌与篡改：

```bash
TOKEN=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"student01","password":"Student#2026"}' --noproxy '*' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

curl -s -H "Authorization: Bearer $TOKEN" $B/auth/me --noproxy '*' | python3 -m json.tool
curl -s -o /dev/null -w '10 %{http_code}\n' -H "Authorization: Bearer ${TOKEN%???}abc" $B/auth/me --noproxy '*'
```

`/auth/me` 要能看到 `username` 为 student01、`authorities` 里有 `ROLE_STUDENT` 和 `task:submit`，并且没有 `password` 字段。最后一条期望 401。

认证十条全过，再验授权六条：

```bash
S=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"student01","password":"Student#2026"}' --noproxy '*' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
T=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"teacher01","password":"Teacher#2026"}' --noproxy '*' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
A=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"Admin#2026"}' --noproxy '*' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

curl -s -o /dev/null -w '11 %{http_code}\n' -H "Authorization: Bearer $S" $B/api/tasks --noproxy '*'
curl -s -o /dev/null -w '12 %{http_code}\n' -X POST -H "Authorization: Bearer $S" -H 'Content-Type: application/json' -d '{"score":100}' $B/api/tasks/2/review --noproxy '*'
curl -s -o /dev/null -w '13 %{http_code}\n' -H "Authorization: Bearer $T" $B/api/tasks --noproxy '*'
curl -s -o /dev/null -w '14 %{http_code}\n' -X POST -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"title":"t","content":"c"}' $B/api/tasks --noproxy '*'
curl -s -o /dev/null -w '15 %{http_code}\n' -H "Authorization: Bearer $T" $B/admin/users --noproxy '*'
curl -s -H "Authorization: Bearer $A" $B/admin/users --noproxy '*' | python3 -m json.tool
```

期望依次是：403、403、200、403、403，最后一条 200 且元素里没有 `password`。

第 11 条不过而接口却能访问，八成是方法级鉴权的开关没打开。

---

## 三、归属

### 10 · 任务业务与越权判断

前面管“能不能进这个门”，这里管“能碰哪条数据”。

问 AI：

```text
五个任务接口：提交、我的、全部、详情、批改。路径分别是 POST /api/tasks、GET /api/tasks/mine、
GET /api/tasks、GET /api/tasks/{id}、POST /api/tasks/{id}/review。

规则：
- 提交时归属从令牌取，忽略请求体里的同名字段
- 我的：只返回自己的
- 详情：本人 或者 有 task:review 权限
- 批改：同一份只能批一次，重复批返回 30012

问题：
1. “本人或有批改权限”这个判断，放 Controller 还是 Service？放 Service 的话当前登录人怎么传进去最干净——从上下文静态取还是参数传？
2. 静态取安全上下文的写法，对单元测试有什么影响？
3. “只能批一次”用状态判断还是数据库唯一约束？两者差别在哪？

产出：
model/TaskStatus.java：枚举 SUBMITTED("待批改")、REVIEWED("已批改")，方法 text()；
vo/TaskView.java：十二个字段的 record，静态工厂 of(TaskRow)；
service/TaskService.java：submit、listMine、listAll、detail、review 五个方法，归属判断写在 detail 里；
entity/Task.java 加 markReviewed(int, String, LocalDateTime)；
controller/TaskController.java：五个接口，三个标权限点，detail 不标权限点（登录即可，具体在服务层判）。
```

产出文件：`model/TaskStatus.java`、`vo/TaskView.java`、`service/TaskService.java`、`controller/TaskController.java`。

验证六条：

```bash
B=http://127.0.0.1:8009
S=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"student01","password":"Student#2026"}' --noproxy '*' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
T=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"teacher01","password":"Teacher#2026"}' --noproxy '*' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

curl -s -X POST -H "Authorization: Bearer $S" -H 'Content-Type: application/json' -d '{"title":"验收提交","content":"内容"}' $B/api/tasks --noproxy '*' | python3 -m json.tool
curl -s -X POST -H "Authorization: Bearer $S" -H 'Content-Type: application/json' -d '{"title":"伪造归属","content":"内容","owner":"student02"}' $B/api/tasks --noproxy '*' | python3 -m json.tool
curl -s -H "Authorization: Bearer $S" $B/api/tasks/mine --noproxy '*' | python3 -m json.tool
curl -s -o /dev/null -w '20 %{http_code}\n' -H "Authorization: Bearer $S" $B/api/tasks/2 --noproxy '*'
```

第 17 条看返回的 `owner` 是 student01、`statusText` 是“待批改”；第 18 条即使请求体塞了 `owner` 为 student02，返回的 `owner` 仍是 student01；第 19 条列表里每一条 owner 都是 student01；第 20 条是 403。

再验批改与重复批改：

```bash
ID=$(curl -s -X POST -H "Authorization: Bearer $S" -H 'Content-Type: application/json' -d '{"title":"等待批改","content":"内容"}' $B/api/tasks --noproxy '*' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')

curl -s -X POST -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"score":88,"comment":"归属判断对了"}' $B/api/tasks/$ID/review --noproxy '*' | python3 -m json.tool
curl -s -H "Authorization: Bearer $S" $B/api/tasks/$ID --noproxy '*' | python3 -m json.tool
curl -s -X POST -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"score":60}' $B/api/tasks/$ID/review --noproxy '*'
```

依次期望：`status` 为 REVIEWED、`score` 为 88；学生查同一条能看到 88 与评语；第三次返回 `code` 30012。

第 18 条最容易漏，它验证的是“归属不来自请求体”。

### 11 · 账号管理接口

两个细节最容易忽略：出参不能带密码，删除不能删自己。

问 AI：

```text
GET /admin/users、POST /admin/users、DELETE /admin/users/{username}。

规则：列表响应不能出现密码字段；新增时密码入库前加密；不能删除当前登录账号，返回 30005。

问题：
1. 出参用实体还是专门的视图对象？用实体的话，密码字段靠什么挡——注解还是类型？
2. “不能删自己”这个校验放 Controller 还是 Service？判断依据是用户名相等还是 id 相等？
3. 新增账号要同时写账号表和关联表，事务边界在哪？

产出：
dto/CreateUserRequest.java：record，username 长度 3 到 32、password 长度 6 到 64、nickname 非空、Set<String> roles 非空；
service/UserService.java 补 listProfiles()、create(CreateUserRequest)、delete(String username, String operator)；
controller/UserAdminController.java：类上标管理员注解，三个方法，create 额外标 user:write。
```

产出文件：`dto/CreateUserRequest.java`、`service/UserService.java`、`controller/UserAdminController.java`。

删除时操作人从 `@AuthenticationPrincipal` 取，传进服务层比较。

验证：

```bash
B=http://127.0.0.1:8009
A=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"Admin#2026"}' --noproxy '*' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

curl -s -X POST -H "Authorization: Bearer $A" -H 'Content-Type: application/json' \
  -d '{"username":"student99","password":"NewStu#2026","nickname":"新加的同学","roles":["STUDENT"]}' \
  $B/admin/users --noproxy '*'

curl -s -X POST $B/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"student99","password":"NewStu#2026"}' --noproxy '*'

curl -s -X DELETE -H "Authorization: Bearer $A" $B/admin/users/admin --noproxy '*'
```

第一条返回新账号的 `UserProfile`，`roles` 含 STUDENT，无 `password`；第二条能登录成功，说明密码加密和角色关联都写对了；第三条返回 `code` 30005。

跑完库里会多一个 student99，想恢复干净数据就重启后端。

### 12 · 把规则写成测试

接口能用手测通，不代表规则成立。测试是把规则固定下来。

问 AI：

```text
要给这套接口写集成测试，覆盖这些行为：

认证：放行接口不带令牌 200；不带令牌访问 /api/tasks/mine 返回 401；登录成功返回令牌、类型、过期时间、角色；
密码错与用户不存在都返回 30001；停用账号返回 30002；缺密码返回 400 与“密码不能为空”；
带令牌换当前用户且响应无 password；令牌改过就失效。

授权：学生看全部提交 403；学生批改 403；教师看全部 200；教师提交 403；教师进管理接口 403；管理员进管理接口 200 且无 password。

归属：学生提交后 owner 是当前登录人；请求体塞别人用户名不生效；我的提交只有自己的；
看别人的任务 403；看自己的 200；教师批改后学生看到分数，重复批返回 30012。

账号：新增账号能登录且我的提交为空；不能删除当前登录账号返回 30005。

问题：
1. MockMvc 和真实启动服务发请求，各适合测什么？
2. 这些用例之间有依赖：新增账号、提交任务都会留下数据，重跑会不会冲突？怎么让每个用例独立？
3. 断言按什么顺序组织，出问题时能一眼看出卡在哪一步？
4. 越权场景断言 403 时，除了状态码还要断言什么？

产出：
src/test/java/top/mqxu/security/SecurityApiTest.java，
分成四组：登录拿到身份、学生只能碰自己的数据、教师的权限点与批改、管理员管账号。
用 @SpringBootTest 加 @AutoConfigureMockMvc，写几个私有辅助方法抽掉登录、带令牌、构造请求体的重复代码。
```

产出文件：`src/test/java/top/mqxu/security/SecurityApiTest.java`。

验证：

```bash
mvn -pl 09-security test
```

全绿。有一条红就先修，别继续。

---

## 四、前端

### 13 · 前端工程与代理

三条代理前缀，少一条页面就没数据。

问 AI：

```text
Vue 3.5 + Vite 8 + TypeScript 5.9，不引 UI 组件库。后端在 http://127.0.0.1:8009，
接口前缀有 /api、/auth、/admin 三组。

问题：
1. 代理前缀和实际路径怎么对应？漏配一条会出现什么现象——报错还是静默失败？
2. 开发时用了代理，后端的 CORS 还需要配吗？什么情况下必须配？
3. Vite 8 的监听地址有什么变化？为什么用 127.0.0.1 可能打不开而 localhost 可以？

产出：
web/package.json：脚本 dev、build（先 vue-tsc --noEmit 再 vite build）、typecheck、preview，
依赖 vue、vue-router、axios，开发依赖 typescript、vite、@vitejs/plugin-vue、vue-tsc；
web/vite.config.ts：端口 5173，proxy 三条前缀都指向后端，都开 changeOrigin；
web/tsconfig.json 与 tsconfig.node.json：strict 模式，include 覆盖 src 下的 ts 与 vue；
web/index.html、web/src/main.ts、web/src/env.d.ts：入口与 *.vue 的模块声明。
```

产出文件：`web/package.json`、`vite.config.ts`、`tsconfig.json`、`tsconfig.node.json`、`index.html`、`src/main.ts`、`src/env.d.ts`。

验证：

```bash
cd 09-security/web && npm install && npm run dev
```

浏览器打开 `localhost:5173`，页面能出来。此时接口还不通，正常。

### 14 · 与后端对齐的类型

字段对不齐，后面每一步都在猜。

问 AI：

```text
要把后端出参写成 TypeScript 类型。有两组枚举：任务状态 SUBMITTED/REVIEWED，角色 STUDENT/TEACHER/ADMIN。
任务对象里 score、comment、reviewedAt 在待批改时是 null。

问题：
1. 可能为 null 的字段，类型标成 `number | null` 还是 `number?`？区别在这里有什么影响？
2. 状态用字符串联合类型还是 enum？在类型收窄和编译产物上分别有什么差异？
3. 后端 LocalDateTime 序列化出来的字符串，前端写 string 够用吗？

产出：
src/types/api.ts 一个文件，导出：
RoleCode、TaskStatusCode 两个联合类型；
ApiResult<T>（code、message、data）；
LoginPayload、LoginResponse、CurrentUser、UserProfile、TaskView、
SubmitTaskPayload、ReviewTaskPayload、CreateUserPayload。
TaskView 十二个字段，score、comment、reviewedAt 标为可空。
```

产出文件：`src/types/api.ts`。

验证：

```bash
cd 09-security/web && npm run typecheck
```

此时只有类型文件没有页面，能过说明 tsconfig 和字段都对。

### 15 · 令牌存在哪、怎么清

一个决定，影响三种场景。

问 AI：

```text
无状态 JWT，前端要把令牌存起来。候选：localStorage、sessionStorage、内存变量。

问题：三种在刷新页面、多标签页、XSS 攻击这三个场景下分别有什么后果？教学项目该选哪个？

约束：不引状态管理库。

产出：
src/store/auth.ts：导出 auth（reactive 对象，含 token、username、nickname、roles）、
saveLogin(LoginResponse)、saveUser(UserProfile)、clearLogin()、hasRole(RoleCode)、hasAnyRole(roles)；
令牌与当前用户存本地存储，模块加载时读出来恢复；
src/store/toast.ts：导出 toasts 与 notify(String)，用于轻提示。
```

产出文件：`src/store/auth.ts`、`src/store/toast.ts`。

验证：登录后刷新页面，还在登录状态。

### 16 · 路由与守卫

菜单和守卫是两件事。

问 AI：

```text
四个页面：登录 /login、任务 /tasks、批改 /review、账号 /users。三种角色。

要求：未登录进不了业务页；角色不对进不了别人那页；刷新后保持登录态。

问题：
1. 守卫里拦角色，和页面上隐藏菜单，是一件还是两件事？只做菜单不做守卫会怎样？
2. 地址栏直接输越权页面，是跳走还是显示无权限？哪种对用户更清楚？
3. 登录页要不要做守卫拦截？已经登录的人再访问登录页该怎么办？

产出：
src/router/index.ts：四条路由，登录页 meta.public 为 true，任务页 roles 是 ['STUDENT']，
批改页 ['TEACHER','ADMIN']，账号页 ['ADMIN']；
根路径按角色重定向到各自首页，通配路径兜底到登录页；
全局前置守卫：public 放行，否则先看令牌，再看角色，拦下来时跳登录页并把原地址放进 query.redirect。
```

产出文件：`src/router/index.ts`。

验证：不登录直接访问 `localhost:5173/tasks`，应该跳登录页，地址栏带上 `redirect=/tasks`。

### 17 · 请求封装与接口清单

后端三种失败形态，前端要分得开。

问 AI：

```text
axios 实例，两个拦截器。后端约定：业务失败是 HTTP 200 带 code，未认证是真实 401，越权是真实 403。

要求：401 清登录态、跳登录页、记住来处；403 只弹提示，人留在原地。

问题：
1. 哪些逻辑放请求侧，哪些放响应侧？
2. 响应侧这两类该怎么分？如果都用同一个分支处理，用户会看到什么？
3. 记住来处要存哪里，登录成功后怎么跳回去？

产出：
src/api/request.ts：axios 实例，baseURL 留空走代理；
请求拦截器加 Authorization: Bearer 头；
响应拦截器先看 HTTP 错误：401 清登录态跳登录页，403 弹提示；
再看业务码：code 不为 200 时弹 message 并抛出；
导出 http 对象，含 get、post、del 三个泛型方法，返回拆过壳的 data；
src/api/index.ts：十个接口方法，每个带返回类型注释。
```

产出文件：`src/api/request.ts`、`src/api/index.ts`。

验证：登录后把 localStorage 里的令牌改几个字符再刷新，应该跳登录页而不是白屏。

### 18 · 页面外壳与登录页

顶栏菜单按角色显示，登录页放三个快捷账号按钮方便演示。

问 AI：

```text
四个页面共用一套外壳，顶栏菜单按角色显示。登录页除了表单，还要三个快捷账号按钮，点了自动填账号密码。

问题：
1. 菜单按角色显示，和路由守卫重复吗？只做其中一个会出什么问题？
2. 登录页的错误提示，和别的页面的错误提示，应该共用一套样式还是各写一套？

产出：
src/App.vue：顶栏含工程名、按角色显示的菜单、当前账号昵称与退出按钮，下方是 router-view，末尾挂 toast 容器；
src/style.css：全局样式，手写一套按钮、输入框、表格、卡片的样式，配色克制；
src/views/Login.vue：左侧说明右侧表单，三个快捷账号按钮分别是学生、教师、管理员，
点击后自动填账号密码，登录成功后按 query.redirect 或角色首页跳转。
```

产出文件：`src/App.vue`、`src/style.css`、`src/views/Login.vue`。

验证：登录成功后进得去，顶栏显示当前账号与昵称，退出后回到登录页。

### 19 · 三个业务页面

页面本身不难，难在三种状态的处理一致。

问 AI：

```text
三个页面：学生任务页（列表 + 提交 + 详情）、教师批改页（全部列表 + 打分）、账号管理页（列表 + 新增 + 删除）。

问题：
1. 加载中、空列表、请求失败，这三种状态怎么用同一套模式处理，避免每个页面各写一遍？
2. 统计数字（总数、待批改数、平均分）在页面里算还是要后端加接口？判断依据是什么？
3. 三个页面的表格结构差别不小，该抽公共组件还是各写一份？

不引入 UI 组件库，样式手写。

产出：
src/views/TaskList.vue：顶部统计条（总提交数、待批改数、平均分，在前端算），
列表展示标题、状态、分数、提交时间，新提交的表单，点行展开详情；
src/views/ReviewList.vue：全部提交列表，按状态与关键字筛选，点行可以打分写评语，已批改的行不给批改入口；
src/views/Users.vue：账号列表（不显示密码）、新增表单、删除按钮，删除按钮在操作自己那条时置灰。
```

产出文件：`src/views/TaskList.vue`、`src/views/ReviewList.vue`、`src/views/Users.vue`。

验证：三个角色各登一次。student01 只看到任务页；teacher01 看到任务页与批改页；admin 三个都看到。

### 20 · 构建与联调

最后一步，把整条链路走一遍。

问 AI：

```text
前端本地开发一切正常，要从“点登录”到“列表有数据”完整走通，中间有哪几个环节可能导致“页面没数据但控制台没报错”？按可能性从高到低排序，每个给一个排查动作。
```

这个问题比“帮我看看为什么没数据”有用得多，它给你一张排查顺序表。

验证：

```bash
cd 09-security/web
npm run typecheck
npm run build
```

两个都过，产物在 `dist/`。再按第四部分的前端走查清单过十项。

---

# 第四部分 · 验收

## 4.1 后端二十四条

把下面整段存成 `accept.sh` 放任意目录，先起后端，再执行 `bash accept.sh`。全部通过会打印“通过 24 条”。

```bash
#!/usr/bin/env bash
set -uo pipefail
B="${B:-http://127.0.0.1:8009}"
TMP=$(mktemp); PASS=0; FAIL=0

call() { curl -s -o "$TMP" -w '%{http_code}' --noproxy '*' "$@" 2>/dev/null || echo 000; }
f() { python3 - "$1" "$TMP" <<'PY'
import json,sys
p,path=sys.argv[1],[x for x in sys.argv[1].split('.') if x]
try: d=json.load(open(sys.argv[2]))
except Exception: print(''); raise SystemExit
for k in path:
    if isinstance(d,list):
        try: d=d[int(k)]
        except Exception: print(''); raise SystemExit
    elif isinstance(d,dict) and k in d: d=d[k]
    else: print(''); raise SystemExit
print(json.dumps(d,ensure_ascii=False) if isinstance(d,(dict,list)) else d)
PY
}
has() { python3 - "$1" "$TMP" <<'PY'
import json,sys
p=[x for x in sys.argv[1].split('.') if x]
try: d=json.load(open(sys.argv[2]))
except Exception: print('no'); raise SystemExit
for k in p:
    if isinstance(d,list):
        try: d=d[int(k)]
        except Exception: print('no'); raise SystemExit
    elif isinstance(d,dict) and k in d: d=d[k]
    else: print('no'); raise SystemExit
print('yes')
PY
}
login() { call -X POST "$B/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"$2\"}" >/dev/null; f data.token; }
ok() { PASS=$((PASS+1)); echo "  PASS  $1"; }
no() { FAIL=$((FAIL+1)); echo "  FAIL  $1  ${2:-}"; }

S=$(login student01 'Student#2026'); T=$(login teacher01 'Teacher#2026'); A=$(login admin 'Admin#2026')
STAMP=$(date +%H%M%S)

[ "$(call $B/public/ping)" = 200 ] && [ "$(f data.status)" = ok ] && ok "01 放行接口 200" || no "01 放行接口 200"
[ "$(call $B/api/tasks/mine)" = 401 ] && ok "02 无令牌访问 mine 401" || no "02 无令牌访问 mine 401"
[ "$(call $B/api/tasks)" = 401 ] && ok "03 无令牌访问全部 401" || no "03 无令牌访问全部 401"
call -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"student01","password":"Student#2026"}' >/dev/null
if [ -n "$(f data.token)" ] && [ "$(f data.tokenType)" = Bearer ] && [ -n "$(f data.expiresAt)" ]; then
  ok "04 登录返回令牌"; else no "04 登录返回令牌"; fi
call -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"student01","password":"w"}' >/dev/null
[ "$(f code)" = 30001 ] && ok "05 密码错 30001" || no "05 密码错 30001"
call -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"nobody","password":"x"}' >/dev/null
[ "$(f code)" = 30001 ] && ok "06 用户不存在也 30001" || no "06 用户不存在也 30001"
call -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"guest","password":"Guest#2026"}' >/dev/null
[ "$(f code)" = 30002 ] && ok "07 停用账号 30002" || no "07 停用账号 30002"
call -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"student01"}' >/dev/null
[ "$(f code)" = 400 ] && ok "08 缺密码 400" || no "08 缺密码 400"
call $B/auth/me -H "Authorization: Bearer $S" >/dev/null
[ "$(f data.username)" = student01 ] && [ "$(has data.password)" = no ] && ok "09 me 无密码" || no "09 me 无密码"
[ "$(call $B/auth/me -H "Authorization: Bearer ${S%???}abc")" = 401 ] && ok "10 篡改令牌 401" || no "10 篡改令牌 401"

[ "$(call $B/api/tasks -H "Authorization: Bearer $S")" = 403 ] && ok "11 学生看全部 403" || no "11 学生看全部 403"
[ "$(call -X POST $B/api/tasks/2/review -H "Authorization: Bearer $S" -H 'Content-Type: application/json' -d '{"score":100}')" = 403 ] && ok "12 学生批改 403" || no "12 学生批改 403"
[ "$(call $B/api/tasks -H "Authorization: Bearer $T")" = 200 ] && ok "13 教师看全部 200" || no "13 教师看全部 200"
[ "$(call -X POST $B/api/tasks -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"title":"t","content":"c"}')" = 403 ] && ok "14 教师提交 403" || no "14 教师提交 403"
[ "$(call $B/admin/users -H "Authorization: Bearer $T")" = 403 ] && ok "15 教师进管理 403" || no "15 教师进管理 403"
call $B/admin/users -H "Authorization: Bearer $A" >/dev/null
[ "$(has data.0.username)" = yes ] && [ "$(has data.0.password)" = no ] && ok "16 管理员列表无密码" || no "16 管理员列表无密码"

call -X POST $B/api/tasks -H "Authorization: Bearer $S" -H 'Content-Type: application/json' -d "{\"title\":\"验收提交 $STAMP\",\"content\":\"内容\"}" >/dev/null
NEW=$(f data.id); [ "$(f data.owner)" = student01 ] && [ "$(f data.statusText)" = 待批改 ] && ok "17 归属是登录人" || no "17 归属是登录人"
call -X POST $B/api/tasks -H "Authorization: Bearer $S" -H 'Content-Type: application/json' -d "{\"title\":\"伪造归属 $STAMP\",\"content\":\"内容\",\"owner\":\"student02\"}" >/dev/null
[ "$(f data.owner)" = student01 ] && ok "18 请求体 owner 不生效" || no "18 请求体 owner 不生效"
call $B/api/tasks/mine -H "Authorization: Bearer $S" >/dev/null
python3 -c "import json;d=json.load(open('$TMP'))['data'];assert all(r['owner']=='student01' for r in d)" 2>/dev/null \
  && ok "19 mine 只有自己的" || no "19 mine 只有自己的"
[ "$(call $B/api/tasks/2 -H "Authorization: Bearer $S")" = 403 ] && ok "20 越权详情 403" || no "20 越权详情 403"
[ "$(call $B/api/tasks/$NEW -H "Authorization: Bearer $S")" = 200 ] && ok "21 自己详情 200" || no "21 自己详情 200"
call -X POST $B/api/tasks/$NEW/review -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"score":88,"comment":"不错"}' >/dev/null
R1=$(f data.score)
call $B/api/tasks/$NEW -H "Authorization: Bearer $S" >/dev/null; R2=$(f data.score)
call -X POST $B/api/tasks/$NEW/review -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"score":60}' >/dev/null
[ "$R1" = 88 ] && [ "$R2" = 88 ] && [ "$(f code)" = 30012 ] && ok "22 批改与防重复" || no "22 批改与防重复"

if [ -n "$(login student99 'NewStu#2026')" ]; then echo "  跳过 23：student99 已存在"; else
  call -X POST $B/admin/users -H "Authorization: Bearer $A" -H 'Content-Type: application/json' \
    -d '{"username":"student99","password":"NewStu#2026","nickname":"新加的同学","roles":["STUDENT"]}' >/dev/null
  N=$(login student99 'NewStu#2026'); call $B/api/tasks/mine -H "Authorization: Bearer $N" >/dev/null
  [ -n "$N" ] && [ "$(f data)" = "[]" ] && ok "23 新增账号能登录" || no "23 新增账号能登录"
fi
call -X DELETE $B/admin/users/admin -H "Authorization: Bearer $A" >/dev/null
[ "$(f code)" = 30005 ] && ok "24 不能删自己" || no "24 不能删自己"

echo; echo "通过 $PASS 条，失败 $FAIL 条"; [ "$FAIL" = 0 ] || exit 1
```

第 23 条依赖新建 `student99`。库里已有这个账号时会跳过并提示，重启后端即可恢复干净的种子数据。

## 4.2 前端十项走查

| 场景 | 期望 |
| --- | --- |
| 不登录直接访问 `/tasks` | 跳登录页，地址带 `redirect` |
| 登录页输入错密码 | 提示“用户名或密码错误”，不跳转 |
| 登录成功后刷新页面 | 还是登录状态 |
| student01 登录 | 只看得到任务页 |
| teacher01 登录 | 看得到任务页与批改页 |
| admin 登录 | 三个页面都看得到 |
| student01 在地址栏输 `/users` | 被挡回，不白屏 |
| 改掉 localStorage 里的令牌再刷新 | 跳回登录页 |
| 教师批改后学生刷新 | 看到分数和评语 |
| 浏览器控制台 | 没有红色报错 |

---

# 第五部分 · 常见错误

这些是让 AI 写这套代码时反复出现的错误。共同点是代码能编译、服务能起，但安全边界是假的。第四部分能发现大部分，剩下的靠右列的“怎么确认”。

## 认证

| 现象 | 原因 | 怎么确认 |
| --- | --- | --- |
| 不带令牌返回一片 HTML 或 500 | 没配 `AuthenticationEntryPoint`，被默认登录页接管 | `curl -i` 看不带令牌的响应，`Content-Type` 应该是 `application/json` |
| 登录接口本身 401 | 过滤链里没放行 `/auth/login` | 看 authorizeHttpRequests，登录路径要在前面 |
| 库里密码是明文 | 建账号时存了原字符串 | `SELECT password FROM sec_user LIMIT 1`，以 `$2a$` 开头才对 |
| 令牌改几位还能用 | 只做了 Base64 解码，没验签 | 改末三位再请求，应该 401 |
| POST 一律 403，GET 正常 | 保留了 CSRF 校验 | 配置里搜 csrf，纯令牌认证要 disable |
| 启动日志警告 UserDetailsService 不用于自动配置 | 把 `AuthenticationProvider` 单独注册成 Bean | 搜 `DaoAuthenticationProvider`，应该只在 `AuthenticationManager` 里 new |
| 管理员权限里没有 `ROLE_ADMIN` | `getAuthorities()` 只返回了权限点，漏了角色名 | 看 `/auth/me` 的 `authorities`，`ROLE_ADMIN` 与 `user:write` 都要在 |

## 授权

| 现象 | 原因 | 怎么确认 |
| --- | --- | --- |
| 该 403 的接口返回 200 | 配置类上没打开方法级鉴权，注解被当成注释，且不报错 | 找那个注解。没有它，所有权限注解全部失效 |
| 配了管理员却一直 403 | `hasRole` 比对的是 `ROLE_ADMIN`，写 `hasAuthority('ADMIN')` 永远不匹配 | 角色权限字符串带不带前缀，要和表达式用的方法对上 |
| 改了角色权限没变 | 权限点写进了 JWT，令牌没到期一直用旧的 | 改库里的 `sec_user_role`，用旧令牌请求，行为应该立刻变 |
| 过滤链后面的规则不生效 | `anyRequest()` 写在具体规则前，把后面全截住 | 规则要从具体到宽泛，`anyRequest` 放最后 |
| 教师能进 `/admin/users` | 只靠方法注解拦，URL 规则写的是 `authenticated()` | 两处都要有 |

## 归属

| 现象 | 原因 | 怎么确认 |
| --- | --- | --- |
| 学生能看到别人的任务 | 详情只查了 `findById`，没比对 owner | 第四部分第 20 条 |
| 判断写在 Controller，换入口就绕过 | Service 没拦 | 归属要么在 Service 里，要么传一个已校验的对象进去 |
| 提交时请求体 `owner` 生效 | 直接拿 DTO 字段存库 | 第 18 条 |
| `/api/tasks/mine` 返回了全部 | 查询条件漏了 owner，或信了前端传的用户名 | 用户名只能从令牌来 |
| 教师看学生详情也 403 | 只写了 owner 相等，忘了 `task:review` 豁免 | 第 21 条通过后让 teacher01 看同一条，应该 200 |
| Service 里出现 `SecurityContextHolder` | 静态上下文耦合，单测要伪造线程状态 | 搜这个类名，Service 层不该有 |

## 出参与数据

| 现象 | 原因 | 怎么确认 |
| --- | --- | --- |
| 响应里带了 `password` | 实体直接当出参 | 第 09、16 条都查这个 |
| `score` 传 120 也存进去了 | 漏了 `@Max(100)`，或没加 `@Valid` | 只加约束注解不加 `@Valid`，校验不触发 |
| 同一份被批改两次 | 只判断任务存在，没判断状态 | 第 22 条第三个断言 |
| 新增账号后登录不上 | 密码没加密，或只写了 `sec_user` 没写 `sec_user_role` | 新增后立刻登录一次，再查关联表 |

## 前端

| 现象 | 原因 | 怎么确认 |
| --- | --- | --- |
| 页面“没有数据”，控制台看不到错误 | 代理漏了前缀，请求打到 Vite 自己身上返回 index.html | 看 proxy 三条都在 |
| 令牌过期后白屏而不是跳登录 | 401 与 403 用了同一个分支 | 响应拦截器里搜状态码判断 |
| 刷新就退出登录 | 令牌只存内存，或没在启动时恢复 | 刷新后看请求头有没有 `Authorization` |
| `typecheck` 报一堆错 | 后端可空字段在前端写成了非空 | `score`、`comment`、`reviewedAt` 都要标可空 |
| 用 `127.0.0.1:5173` 打不开 | Vite 8 只监听 `::1` | 用 `localhost:5173` |

---

# 走完之后

二十步都过了，把做出来的代码和本仓库的 `09-security` 对照。重点看三处：

- 你的归属判断写在哪一层
- 你的令牌载荷里有什么
- 你的过滤器怎么处理异常

这三处是这套案例的分水岭。差异背后的理由，就是这一章要学的东西。


