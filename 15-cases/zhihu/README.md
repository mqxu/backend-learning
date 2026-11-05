# zhihu · 知乎问答社区

> 📖 详细教程：[docs/15-zhihu.md](../../docs/15-zhihu.md)

从只有一张专栏表演进成完整的问答社区：登录、提问、回答、评论、投票、关注、动态流。

## 接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | — | 用户名密码换 token |
| POST | `/api/v1/auth/register` | — | 注册并直接返回 token |
| GET | `/api/v1/auth/me` | ✅ | 当前用户 |
| GET | `/api/v1/questions` | — | 分页 + 话题 + 关键词 + 排序 |
| GET | `/api/v1/questions/{id}` | — | 详情，浏览数 +1 |
| POST | `/api/v1/questions` | ✅ | 提问 |
| PUT | `/api/v1/questions/{id}` | ✅ | 修改，仅作者 |
| DELETE | `/api/v1/questions/{id}` | ✅ | 逻辑删除，仅作者 |
| GET | `/api/v1/questions/{id}/answers` | — | 回答列表，采纳优先 |
| POST | `/api/v1/questions/{id}/answers` | ✅ | 写回答 |
| POST | `/api/v1/answers/{id}/accept` | ✅ | 采纳，仅提问者 |
| DELETE | `/api/v1/answers/{id}` | ✅ | 删除，仅作者 |
| POST | `/api/v1/answers/{id}/vote` | ✅ | 投票，`value=1/-1/0` |
| GET | `/api/v1/answers/{id}/comments` | — | 评论列表 |
| POST | `/api/v1/answers/{id}/comments` | ✅ | 发表评论 |
| DELETE | `/api/v1/comments/{id}` | ✅ | 删除评论，仅作者 |
| POST | `/api/v1/follows` | ✅ | 关注人 / 问题 / 话题 |
| DELETE | `/api/v1/follows` | ✅ | 取消关注 |
| POST | `/api/v1/questions/{id}/vote` | ✅ | 给问题投票 |
| GET | `/api/v1/feed` | ✅ | 关注动态流 |
| GET | `/api/v1/topics` | — | 话题列表 |
| GET | `/api/v1/users/{id}` | — | 个人主页 |
| GET | `/api/v1/special/all` | — | 专栏（原接口，前端在用） |

## 演示账号

| 账号 | 密码 | 昵称 |
| --- | --- | --- |
| alice | Zhihu#2026 | 林知远 |
| bob | Zhihu#2026 | 周可欣 |
| carol | Zhihu#2026 | 陈默 |
| dave | Zhihu#2026 | 苏航 |
| teacher | Teacher#2026 | 陶老师 |

## 运行

```bash
# 建库（表与数据由应用启动时自动导入）
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS zhihu DEFAULT CHARSET utf8mb4"

mvn -pl 15-cases/zhihu spring-boot:run
node tools/run-cases.mjs 15-cases/zhihu
```

端口 8080。改端口要同步改 `web/src/components/ZhiHu.vue` 里的地址。

## 测试

```bash
mvn -pl 15-cases/zhihu test
```

22 个用例：

| 测试类 | 用例 | 覆盖 |
| --- | --- | --- |
| `JwtServiceTest` | 5 | 签发解析、篡改、换密钥、过期、乱字符串 |
| `QuestionApiTest` | 8 | 分页、筛选、详情、404、未登录、发布、越权、校验 |
| `AnswerApiTest` | 8 | 采纳、计数、投票三态、评论增删、动态流 |
| `SpecialServiceTest` | 1 | 原专栏接口仍可用 |

## 目录结构

```
zhihu
├── src/main/java/top/mqxu/zhihu
│   ├── common/      Result、ErrorCode、PageResult、UserContext、异常处理
│   ├── config/      WebConfig、MybatisPlusConfig
│   ├── security/    JwtService、AuthInterceptor
│   ├── entity/      八张表的实体
│   ├── mapper/      BaseMapper
│   ├── dto/         入参，带校验注解
│   ├── vo/          出参 record
│   ├── service/     十个业务类
│   └── controller/  八个控制器
├── src/main/resources
│   ├── application.yml
│   ├── schema.sql
│   └── data.sql
└── web/             Vue3 前端
```

## 要点

- 拦截器只解析 token，不决定「能不能访问」；写接口用 `UserContext.requireUserId()` 拦
- 列表查名字统一走 `LookupService`，一次 `IN` 查询，避免 N+1
- 计数列用 `SET x = x + 1`，不用「读出来加一再写回」
- 投票三种分支：没投过、再点一次取消、换按钮改票
- 逻辑删除靠 `@TableLogic`，`deleteById` 实际是 `UPDATE deleted = 1`
