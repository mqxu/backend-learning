# AI 容易写错的地方

这些是让 AI 写这套代码时反复出现的错误。共同点是：代码能编译、服务能起，但安全边界是假的。跑一遍 `check.sh` 能发现大部分，剩下的靠下面每条的“怎么确认”。

## 认证

| 现象 | 原因 | 怎么确认 |
| --- | --- | --- |
| 不带令牌访问返回一片 HTML，或者 500 | 没有配置 `AuthenticationEntryPoint`，请求被 Security 默认的登录页接管 | 用 `curl -i` 看不带令牌的响应，`Content-Type` 应该是 `application/json` |
| 登录接口本身返回 401，拿不到令牌 | 过滤链里没有 `permitAll` 放行 `/auth/login` | 看 authorizeHttpRequests 的规则列表，登录路径要在前面 |
| 密码在库里是明文 | 建账号时直接存了原字符串，没走 `PasswordEncoder` | `SELECT password FROM sec_user LIMIT 1`，以 `$2a$` 开头才是 BCrypt |
| 令牌改几位还能用 | 只做了 Base64 解码，没验签 | 把令牌最后三位改掉再请求，应该 401 |
| POST 请求一律 403，GET 正常 | 保留了 CSRF 校验 | 配置里搜 `csrf`，纯令牌认证应该是 disable |
| 启动日志warning：UserDetailsService 不用于自动配置 | 把 `AuthenticationProvider` 单独注册成了 Bean | 搜 `@Bean` 附近的 `DaoAuthenticationProvider`，应该只在 `AuthenticationManager` 里 new |
| 管理员登录后拿到的权限里没有 `ROLE_ADMIN` | `getAuthorities()` 只返回了权限点，漏了角色名 | 看 `/auth/me` 的 `authorities` 字段，`ROLE_ADMIN` 和 `user:write` 都应该在 |

## 授权

| 现象 | 原因 | 怎么确认 |
| --- | --- | --- |
| 该 403 的接口返回 200，`check.sh` 第 11 条不过 | 忘了 `@EnableMethodSecurity`，`@PreAuthorize` 完全不生效，注解成了注释 | 配置类上找这个注解。没有它，所有方法级鉴权全部失效且不报错 |
| 明明配了 `hasRole('ADMIN')` 却一直 403 | `hasRole` 比对的是 `ROLE_ADMIN`，写 `hasAuthority('ADMIN')` 就永远不匹配 | 看角色权限字符串，带不带 `ROLE_` 前缀要和表达式用的方法对上 |
| 改了角色但权限没变 | 权限点写进了 JWT 载荷，令牌没到期就一直用旧的 | 改库里的 `sec_user_role`，用旧令牌请求，行为应该立刻变 |
| 过滤链里后面的规则不生效 | `anyRequest()` 写在了具体规则前面，把后面的全截住了 | 规则要从具体到宽泛排，`anyRequest` 放最后 |
| 教师能进 `/admin/users` | 只靠方法注解拦，URL 规则里写的是 `authenticated()` | 两处都要有：URL 规则 `hasRole('ADMIN')`，Controller 类上再标一次 |

## 归属

| 现象 | 原因 | 怎么确认 |
| --- | --- | --- |
| 学生能看到别人的任务 | 详情接口只查了 `findById`，没比对 owner | `check.sh` 第 20 条 |
| 判断写在 Controller 里，Service 没拦 | 换个入口调用就绕过去了 | 看 Service 方法签名，归属判断要么在 Service 里，要么传进去一个已校验的对象 |
| 提交时请求体里的 `owner` 生效了 | 直接拿 DTO 的字段存库，没覆盖成当前登录人 | `check.sh` 第 18 条 |
| `/api/tasks/mine` 返回了全部 | 查询条件漏了 owner，或者信任了前端传的用户名 | 走查这条链路，用户名只能从令牌来 |
| 教师看学生的详情也被 403 | 归属判断只写了 owner 相等，忘了 `task:review` 豁免 | `check.sh` 第 21 条通过后，让 teacher01 访问同一条，应该是 200 |
| Service 里写了 `SecurityContextHolder.getContext()` | 静态上下文耦合进来，单测需要伪造线程状态 | 搜这个类名，Service 层不该出现 |

## 出参与数据

| 现象 | 原因 | 怎么确认 |
| --- | --- | --- |
| 接口响应里带了 `password` | 实体直接当出参返回 | `check.sh` 第 9、16 条都查这个字段 |
| `score` 传 120 也存进去了 | 漏了 `@Max(100)`，或者没在参数上加 `@Valid` | 只加约束注解不加 `@Valid`，校验不会触发 |
| 同一份任务被批改两次 | 只判断了任务存在，没判断状态 | `check.sh` 第 22 条的第三个断言 |
| 新增账号后登录不上 | 密码入库前没加密，或者角色关联只写了 `sec_user` 没写 `sec_user_role` | 新增后立刻登录一次，再查 `sec_user_role` 有没有这条 |

## 前端

| 现象 | 原因 | 怎么确认 |
| --- | --- | --- |
| 页面显示“没有数据”，控制台看不到接口错误 | Vite 代理漏了前缀，请求打到 Vite 自己身上返回了 index.html | 看 `vite.config.ts` 的 proxy，`/api`、`/auth`、`/admin` 三条都要有 |
| 令牌过期后页面白屏而不是跳登录 | 401 和 403 用了同一个处理分支 | 响应拦截器里搜状态码判断，两个分支要分开 |
| 刷新页面就退出登录 | 只把用户名存在内存里，没存令牌，或者没在应用启动时恢复 | 刷新后看请求头有没有 `Authorization` |
| `npm run typecheck` 报一堆错 | 后端的 `null` 字段在前端类型里写成了非空 | `score`、`comment`、`reviewedAt` 都可空，类型要写 `| null` |
| 用 `127.0.0.1:5173` 打不开 | Vite 8 只监听 `::1` | 用 `localhost:5173` |
