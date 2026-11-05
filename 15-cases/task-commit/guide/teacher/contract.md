# 案例契约

这份文件是实现的依据。里面只写系统要对外的行为，不涉及任何实现方式。技术选型、类结构、过滤器怎么写，都由你自己定。

## 案例是什么

一个实训任务提交与批改系统。学生交任务，教师看全部并打分，管理员管账号。三种人能做的事完全不同，案例的重点是把这三条边界立住。

## 数据表

四张表，表名前缀 `sec_`，与 05-mybatis 共用一个库。

| 表 | 字段 | 说明 |
| --- | --- | --- |
| `sec_role` | id、code、name | 角色字典，三条固定数据 |
| `sec_user` | id、username、password、nickname、enabled | 账号。password 存 BCrypt 密文 |
| `sec_user_role` | user_id、role_id | 多对多关联，主键是这两列 |
| `sec_task` | id、owner_id、title、content、status、score、comment、submitted_at、reviewed_at | 任务。owner_id 指向 `sec_user` |

`sec_task.status` 只有两个值：`SUBMITTED`、`REVIEWED`。

## 角色与权限点

三个角色，五个权限点。权限点命名是“资源:动作”。

| 角色 | 权限点 |
| --- | --- |
| STUDENT | `task:submit`、`task:read` |
| TEACHER | `task:review`、`task:read` |
| ADMIN | `user:read`、`user:write`、`task:read`、`task:review` |

接口绑权限点，不绑角色。这样以后多一类“教务”，能看全部但不能批改，配 `task:read` 就够，接口代码不用动。

## 接口

十一个接口。方法、路径、入参、出参、需要什么权限，都定死。

| 方法 | 路径 | 入参 | 出参 | 权限要求 |
| --- | --- | --- | --- | --- |
| POST | `/auth/login` | `username`、`password` | 令牌与身份信息 | 放行 |
| GET | `/auth/me` | 无 | 当前账号信息 | 已认证 |
| GET | `/public/ping` | 无 | `{"status":"ok"}` | 放行 |
| POST | `/api/tasks` | `title`、`content` | 新建的任务 | `task:submit` |
| GET | `/api/tasks/mine` | 无 | 任务数组 | 已认证 |
| GET | `/api/tasks/{id}` | 路径 id | 单条任务 | 本人或有 `task:review` |
| GET | `/api/tasks` | 无 | 全部任务数组 | `task:review` |
| POST | `/api/tasks/{id}/review` | `score`、`comment` | 批改后的任务 | `task:review` |
| GET | `/admin/users` | 无 | 账号数组 | ADMIN |
| POST | `/admin/users` | `username`、`password`、`nickname`、`roles` | 新建的账号 | ADMIN 且有 `user:write` |
| DELETE | `/admin/users/{username}` | 路径 username | 空 | ADMIN |

### 出入参字段

登录请求：`username` 不能为空，`password` 不能为空。

登录响应：`token`、`tokenType`（固定 `Bearer`）、`expiresAt`、`username`、`nickname`、`roles`（角色名数组）。

账号视图：`username`、`nickname`、`roles`、`authorities`、`enabled`。任何接口的响应里都不能出现 `password` 字段。

任务视图：`id`、`title`、`content`、`ownerId`、`owner`、`ownerNickname`、`status`、`statusText`、`score`、`comment`、`submittedAt`、`reviewedAt`。

`statusText` 是后端给的中文，`SUBMITTED` 对应“待批改”，`REVIEWED` 对应“已批改”。前端直接用，不再自己映射一遍。

提交任务请求：`title` 非空、最长 100 字；`content` 非空、最长 2000 字。

批改请求：`score` 非空、0 到 100；`comment` 最长 500 字。

新增账号请求：`username` 长度 3 到 32，`password` 长度 6 到 64，`nickname` 非空，`roles` 非空。

## 响应格式

所有接口返回同一个外壳：

```json
{ "code": 200, "message": "success", "data": {} }
```

业务失败也走 HTTP 200，靠 `code` 区分。只有两种情况用真实 HTTP 状态码：未认证给 401，越权给 403。

这条要守住。学生看别人的任务，如果后端返回 200 加一个错误码，前端就得猜什么时候跳登录、什么时候弹提示，拦截器没法写。

## 错误码

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

`30001` 同时覆盖“密码错”和“用户不存在”。分开提示等于告诉攻击者哪些用户名存在。

## 要成立的行为

以下 24 条是要验收的行为。每一条都对应一次 HTTP 请求和一个期望结果，用 `check.sh` 跑。编号与脚本输出一致。

**认证**

1. `/public/ping` 不带令牌返回 200，`data.status` 为 `ok`
2. 不带令牌访问 `/api/tasks/mine` 返回 401，`code` 为 401
3. 不带令牌访问 `/api/tasks` 返回 401
4. 登录成功返回非空 `token`，`tokenType` 为 `Bearer`，`expiresAt` 非空，`roles` 含 `STUDENT`
5. 密码错误返回 `code` 30001
6. 用户名不存在也返回 `code` 30001
7. `guest` 账号登录返回 `code` 30002
8. 只传用户名不传密码，返回 `code` 400，message 为“密码不能为空”
9. 带令牌访问 `/auth/me` 返回 200，`username` 为 `student01`，响应里没有 `password`
10. 令牌改动几位后访问 `/auth/me` 返回 401

**授权**

11. 学生访问 `GET /api/tasks` 返回 403
12. 学生调用批改接口返回 403
13. 教师访问 `GET /api/tasks` 返回 200，结果是数组，元素含 `owner`
14. 教师调用提交接口返回 403
15. 教师访问 `/admin/users` 返回 403
16. 管理员访问 `/admin/users` 返回 200，元素不含 `password`

**归属**

17. 学生提交任务，返回的 `owner` 是当前登录人，`status` 为 `SUBMITTED`，`statusText` 为“待批改”
18. 提交时请求体里塞 `owner` 为别人，返回的 `owner` 仍是当前登录人
19. `/api/tasks/mine` 返回的每一条 `owner` 都是当前登录人
20. 学生访问别人的任务 `GET /api/tasks/2` 返回 403
21. 学生访问自己刚提交的任务返回 200
22. 教师批改后返回 `status` 为 `REVIEWED`、`score` 为 88；学生再查能看到同样的分数和评语；同一份再批一次返回 `code` 30012

**账号管理**

23. 管理员新增 `student99` 后，该账号能登录，`/api/tasks/mine` 返回空数组
24. 管理员删除自己返回 `code` 30005

## 演示账号

密码在库里是 BCrypt 密文，明文如下。

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | Admin#2026 | ADMIN |
| teacher01 | Teacher#2026 | TEACHER |
| teacher02 | Teacher#2026 | TEACHER |
| student01 到 student10 | Student#2026 | STUDENT |
| guest | Guest#2026 | STUDENT，已停用 |

`student01` 是朱彦清，`student02` 是李思远。种子数据里 `student02` 有一条 `id=2` 的任务，第 20 条越权用例就是拿它测的。
