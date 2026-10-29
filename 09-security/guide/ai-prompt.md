# 怎么问 AI

AI 给出的代码质量，八成取决于你怎么问。同一个问题，问法不同，拿到的可能是能跑的工程代码，也可能是过时的示例片段。

## 一次提问包含四部分

**背景**：技术栈和版本。Spring Security 5 和 7 的配置写法完全不同，不说版本，AI 很可能给你 `WebSecurityConfigurerAdapter`，那个类在 6 之后已经删了。

**现状**：你手上已经有什么类、哪些方法。AI 看不见你的工程，你报出类名和方法签名，它才能接着写而不是另起一套。

**要解决的问题**：一句话说清。一次只问一个决策点。

**约束**：不能改什么，必须符合什么。比如“不能用 session，必须无状态”“出参不能含实体”。

示例：

> 环境是 Spring Boot 4.1.1 + Spring Security 7.1.1，无状态 JWT 认证，不用 session。
> 已有 `UserMapper`（MyBatis-Plus BaseMapper）返回 `User` 实体，`RoleMapper` 查角色，我需要把两者合成一个 `Account` 对象交给 `UserDetailsService`。
> 问题：`UserDetails` 的 `getAuthorities()` 应该返回什么？我的权限点分成角色名和权限点两类。
> 约束：不要引入新依赖，不要用 `@Component` 注册 `AuthenticationProvider`。

## 坏提问长什么样

| 坏提问 | 问题在哪 |
| --- | --- |
| 帮我写一个 Spring Security 的登录接口 | 没有版本、没有现有类、没有约束，AI 只能猜，猜出来的多半是 5.x 写法 |
| 我这个 403 是怎么回事 | 没有代码、没有请求、没有响应，AI 只能列一遍所有可能原因 |
| 把整个模块的代码给我 | 你放弃了设计，后面出问题也定位不了 |
| 这样写对不对 | 没说自己为什么这么写，AI 只能泛泛评价 |

改最后一条：把“这样写对不对”换成“我把归属判断放在 Service 里，因为 Controller 不该知道表结构，这样安排的代价是 Service 要注入 TaskMapper。这个取舍合理吗”。后者 AI 能给出有价值的反驳。

## 拿到回答之后做三件事

**第一，先跑起来再读代码。** 编译不过、接口不通，读代码毫无意义。类名对不上、包名不对、方法不存在，都是常见情况。

**第二，找它的假设。** AI 常默认一些你没说的东西，比如默认你有 session、默认角色直接叫 `ADMIN` 而不用权限点、默认错误用异常往外抛。这些假设和你的 SPEC 冲突时，要改的是代码不是 SPEC。

**第三，把 AI 说错的地方记下来。** 这些就是 `pitfalls.md` 里那些条目。你自己踩过一次，比看十遍文档记得住。

## 卡住时怎么追问

不要原地重复问同一个问题。换个角度：

- 问“为什么”而不是“怎么写”：让 AI 解释这个配置项的作用，你自己判断要不要
- 问反例：“这段代码在什么情况下会失效”
- 给它失败信息：把完整的异常栈和请求响应贴进去
- 缩小范围：从“这个模块怎么搭”缩到“这个方法返回 403，帮我看授权表达式对不对”

## 分关卡的示例提问

### 认证

> Spring Boot 4.1.1 + Spring Security 7.1.1，要做一个无状态 JWT 登录。
> 登录接口 POST /auth/login 收用户名密码，返回 HS256 令牌。账号表 `sec_user` 有 `enabled` 字段控制停用。
> 现在的问题：密码校验和“账号停用”检查，交给 `DaoAuthenticationProvider` 做，还是我自己在 Service 里比对 BCrypt？
> 约束：两种做法各自会带来什么后果，说清楚再给建议。

这个问法能拿到对比分析。如果直接问“登录接口怎么写”，AI 会把整条链路写完，你就失去了理解 `DaoAuthenticationProvider` 的机会。

### 授权

> 我的权限点有五个：`task:submit`、`task:read`、`task:review`、`user:read`、`user:write`。三个角色各自持有一部分。
> 接口要绑权限点不绑角色。Security 7 里 `hasRole('ADMIN')` 和 `hasAuthority('ADMIN')` 行为差别在哪，我要拦“管理员才能进的 /admin/**”，该用哪个？

### 归属

> 接口 `GET /api/tasks/{id}` 的规则是：本人能看，有 `task:review` 权限的教师能看，其他人 403。
> 我打算在 Service 里判断，因为要看这一行的 owner_id。但这样 Service 需要知道当前登录人，传入 `SecurityUser` 还是传用户名？
> 约束：Service 不依赖 Security 的上下文静态方法。

最后那条约束很重要。不说的话，AI 很可能给你 `SecurityContextHolder.getContext()` 写在 Service 里，那个静态调用会让单元测试难写。

### 前端

> Vue 3 + TypeScript，axios 拦截器统一处理响应。后端约定：业务失败返回 HTTP 200 加 `code`，未认证返回真实 401，越权返回真实 403。
> 401 要清登录态跳登录页并记住来处，403 只弹提示。这两个分支怎么在同一个响应拦截器里分开？
> 约束：不要引入状态管理库，令牌存 localStorage。
