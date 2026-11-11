# 校园活动小程序

学生发现活动、报名、签到，组织者发布与核销，团委审核与统计的一条完整链路。

- 小程序端：uni-app + Vue3 + Vite
- 后端：Spring Boot 4.1.1 + MyBatis-Plus 3.5.18.1 + MySQL 8 + Spring Security 7 + Spring Data Redis
- 管理后台：Vue3 + Vite + Element Plus
- 可选能力：短信验证码（Mock / 容联云）、文件上传（本地磁盘 / 阿里云 OSS）

> 📖 课程教学包（4 次课 / 16 学时）：[docs/教学包/README.md](docs/教学包/README.md)

## 目录结构

```
campus-activity/
├── server/          Spring Boot 后端
│   ├── src/main/resources/db/    schema.sql、data.sql
│   └── uploads/     本地上传目录（运行后生成，已 gitignore）
├── app/             uni-app 小程序
├── admin/           管理后台
├── scripts/
│   └── smoke.sh     端到端冒烟测试（33 项断言）
└── docs/            分步教程；项目文档在 docs/项目文档/
```

## 技术版本

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | Spring Boot 4 要求 17 以上 |
| Spring Boot | 4.1.1 | |
| MyBatis-Plus | 3.5.18.1 | 必须用 `mybatis-plus-spring-boot4-starter` |
| Spring Security | 7.x | 由 Boot 4.1.1 管理，负责过滤器链、方法级鉴权与后台登录 |
| Spring Data Redis | 4.x | 由 Boot 4.1.1 管理，缓存、短信验证码、JWT 黑名单、定时任务锁 |
| Redis | 6.2+（实测 8.2.2） | 本机 6379，密码 `abc123`，database 11 |
| 容联云短信 SDK | 1.0.4 | 可选，`campus.sms.provider=cloopen` 时启用 |
| 阿里云 OSS SDK | 3.18.5 | 可选，`campus.file.storage-type=oss` 时启用 |
| MySQL | 8.0+ | 本机 3306，库名 `campus_activity` |
| Node | 18+ | 教学环境实测 v24.20.0，建议 20/22 LTS |
| uni-app | 3.0.0-5020620260917001 | Vue3 版 |
| TypeScript | 5.9 | 前端统一使用 TS，`vue-tsc` 做类型检查 |

## 快速开始

### 1. 启动 MySQL

```bash
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS campus_activity DEFAULT CHARSET utf8mb4"
```

演示数据与 01-14 的模块分开存在 `campus_activity` 库，避免表名冲突。
连接信息在 `server/src/main/resources/application.yml`，账号密码可用 `MYSQL_USERNAME` / `MYSQL_PASSWORD` 覆盖。

### 2. 启动 Redis

短信验证码、JWT 登出黑名单、分类/热门活动缓存和定时任务分布式锁都依赖 Redis（database 11，与 01-14 其他案例隔离）：

```bash
redis-server --port 6379 --requirepass abc123 --daemonize yes
redis-cli -a abc123 --no-auth-warning -n 11 ping
# PONG
```

连接信息在 `server/src/main/resources/application.yml` 的 `spring.data.redis`，可用 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE` 覆盖。Redis 不可用时分类/热门活动、短信登录与登出会失败，定时任务也不会推进。

### 3. 初始化表与演示数据

```bash
cd server/src/main/resources/db
mysql -h127.0.0.1 -P3306 -uroot -p123456 campus_activity < schema.sql
mysql -h127.0.0.1 -P3306 -uroot -p123456 campus_activity < data.sql
```

`data.sql` 里的时间以执行当天为基准，活动排期落在 08:00–22:00（如 14:00–17:00），不会出现凌晨开场的时间。

### 4. 启动后端

```bash
cd server
mvn spring-boot:run
```

启动前确认 MySQL 与 Redis 都已可用。后端默认监听 8016，Swagger 在 `http://127.0.0.1:8016/swagger-ui.html`。

文件上传默认落本地磁盘 `server/uploads/`（相对启动目录 `./uploads`，启动时自动创建，已 gitignore），通过 `http://127.0.0.1:8016/uploads/<objectKey>` 直接访问；切阿里云 OSS 时把 `campus.file.storage-type` 改为 `oss` 并配置 `OSS_ENDPOINT` / `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` / `OSS_BUCKET`（可选 `OSS_DOMAIN`）。

短信默认走 Mock 通道，验证码只打日志、不回真实短信。要拿验证码有两种方式：

1. 直接调发送接口，`campus.sms.expose-code=true`（默认）时响应里带 `mockCode`：

```bash
curl -s -X POST http://127.0.0.1:8016/api/app/auth/sms/send-code \
  -H 'Content-Type: application/json' -d '{"phone":"13800000003"}'
# {"code":0,"message":"ok","data":{"phone":"13800000003","expireSeconds":300,"intervalSeconds":60,"mockCode":"123456"}}
```

2. 看后端控制台日志：`[短信模拟] 向 138****0003 发送验证码：123456`。

切容联云真实通道：把 `campus.sms.provider` 改为 `cloopen`，并配置 `CLOOPEN_ACCOUNT_SID` / `CLOOPEN_ACCOUNT_TOKEN` / `CLOOPEN_APP_ID`（模板 ID 用 `CLOOPEN_TEMPLATE_ID`，默认 1），同时把 `expose-code` 改回 `false`。

### 5. 启动小程序（H5 预览）

```bash
cd app
pnpm install
pnpm run dev:h5
```

访问 `http://127.0.0.1:5174`。启动前确认后端（8016）与 Redis 已就绪；H5 通过 Vite 代理访问 `/api` 与 `/uploads`。微信小程序真机调试可参考 uni-app 官方文档（需自行配置 AppID 与合法域名）。

演示手机号登录：在「我的 → 手机号验证码登录」输入 `13800000003`，点「获取验证码」后 Mock 通道会把验证码随接口返回并自动填入页面，直接点「登录」即可；未绑定学号时会跳到绑定页。

### 6. 启动管理后台

```bash
cd admin
pnpm install
pnpm run dev
```

访问 `http://127.0.0.1:5173`，账号 `admin` / `Admin@123`。启动前确认后端已就绪，后台通过 Vite 代理访问 `/api` 与 `/uploads`。

## 前端功能

小程序与管理后台已接入后端新增能力：

- 小程序「我的 → 手机号验证码登录」：手机号 + 短信验证码登录，发码后 60 秒倒计时；Mock 通道下验证码随接口返回并自动填入，登录成功且 `needBind=true` 时跳绑定页，否则回首页。
- 小程序组织者「发布活动」新增活动封面：选图后调用 `POST /api/files/upload?bizType=activity-cover` 上传，支持预览、重新选择与移除，保存时携带 `coverUrl`。
- 两端退出登录都会先 best-effort 调用后端登出接口（`POST /api/app/auth/logout`、`POST /api/admin/auth/logout`）把 token 拉黑，再清理本地登录态。
- 管理后台新增「素材上传」页（`/file` 路由，菜单在「操作日志」前）：展示当前存储配置，支持 `common` / `activity-cover` / `org-logo` / `avatar` 四类 bizType 上传、图片预览与复制访问地址。
- 小程序 H5 与管理后台的 Vite 配置均把 `/api`、`/uploads` 代理到 `http://127.0.0.1:8016`，接口与上传后的图片不再需要改端口。

## 演示账号

| 身份 | 登录方式 | 说明 |
| --- | --- | --- |
| 学生 | 小程序自动登录 | 开发模式默认 `mock-student-1`，在「我的 → 切换测试身份」可换 |
| 学生 | 手机号 + 短信验证码 | `13800000003` 张小明、`13800000004` 李小红、`13800000005` 王大力；默认 Mock 通道，验证码从响应 `mockCode` 或日志获取，小程序登录页会自动填入 |
| 组织者 | 小程序自动登录 | `mock-organizer-1`，所属组织为计算机学院学生会 |
| 管理员 | 后台账号密码 | `admin` / `Admin@123` |

手机号验证码登录调用 `POST /api/app/auth/sms/send-code` 再调 `POST /api/app/auth/sms/login`，小程序「手机号验证码登录」页会自动填入 Mock 通道返回的验证码；未注册手机号首次登录会自动建号，未绑定学号时返回 `needBind=true`，仍走原来的绑定接口。学籍白名单：2023001 张小明、2023002 李小红、2023003 王大力、2024001 赵静、2024002 陈晨。

## 验收

```bash
./scripts/smoke.sh
```

脚本默认 `BASE=http://127.0.0.1:8016/api`，覆盖 7 个断言章节共 33 项断言：健康检查、学生登录与绑定、组织者建活动、提审与审核、报名与满员候补、扫码签到与重复/伪造拦截、统计，以及扩展能力（短信验证码登录、登出黑名单、文件上传与静态访问、未登录上传拦截）。全部通过输出「全部通过：33 项」。

第 7 节扩展能力断言依赖本机 `curl`、`jq` 与 `redis-cli`，会在运行前清理演示手机号的短信限流 key（默认密码 `abc123`、database `11`，可用 `REDIS_PASSWORD` / `REDIS_DATABASE` 覆盖）。后端单元/接口测试另执行：

```bash
cd server
mvn test
```

共 5 个测试类 20 个用例（ApiSmokeTest 5、SecurityAccessTest 5、FileUploadTest 5、SmsAuthTest 3、RedisCacheTest 2），同样依赖本地 MySQL 与 Redis。

## 当前验证状态

| 部分 | 状态 |
| --- | --- |
| 后端 | 已启动并跑通，`mvn test` 20 个用例全部通过；`scripts/smoke.sh` 33 项断言全部通过（含名额并发、候补、重复签到、伪造签到码、短信登录、登出黑名单、文件上传） |
| 数据库 | 12 张表已建，演示数据可重复导入 |
| Redis | 缓存、短信验证码、JWT 黑名单、定时任务分布式锁已接入（database 11） |
| 小程序 | 已跑通：14 个页面在 H5 下正常渲染（浏览器截图核对），tabBar 四组图标已接入；新增「手机号验证码登录」页，组织者发布活动可上传封面，退出登录会调用后端登出接口；`pnpm type-check` 零错误，`pnpm build:h5` 通过 |
| 管理后台 | 已跑通：实际登录后进入工作台，指标卡与热门活动表格显示后端真实数据；新增「素材上传」页，退出登录调用后端登出接口；`pnpm build`、`pnpm type-check` 均通过 |

前端依赖用 pnpm 安装（npm 在本机受代理限制会超时）。

## 扩展能力

Spring Security 鉴权、Redis 缓存与分布式锁、短信验证码、文件上传四项扩展能力的代码位置、配置项、接口清单、切换方法与排错步骤，见 [docs/项目文档/12-功能扩展说明.md](docs/项目文档/12-功能扩展说明.md)。小程序与管理后台已完成接入（手机号验证码登录、真实登出、活动封面上传、后台素材上传页），前端接入点与浏览器联调结果见该文档 9.4、9.5 节。

## 界面截图

`docs/screenshots/` 下是三个端跑起来后的实际画面。

| 文件 | 内容 |
| --- | --- |
| `tabbar-icons.png` | 小程序 tabBar 图标（常态线性 / 选中实心） |
| `app-home.png` | 首页：问候、分类、热门活动、即将开始 |
| `app-activity-list.png` | 全部活动：搜索、分类与时间筛选 |
| `app-activity-detail.png` | 活动详情：时间地点、名额进度、报名入口 |
| `app-activity-signup.png` | 填写报名信息 |
| `app-message-list.png` | 消息通知 |
| `app-mine.png` | 我的（学生身份） |
| `app-sms-login.png` | 手机号验证码登录（Mock 验证码自动填入） |
| `app-mine-signups.png` | 我的报名（含签到凭证） |
| `app-checkin-scan.png` | 扫码签到 |
| `app-mine-organizer.png` | 我的（组织者身份） |
| `app-org-index.png` | 组织者工作台 |
| `app-org-edit.png` | 发布活动 |
| `app-org-cover.png` | 发布活动（已上传封面） |
| `app-org-signups.png` | 报名名单 |
| `app-org-checkin.png` | 签到核销（签到码 + 到场统计） |
| `admin-login.png` | 后台登录 |
| `admin-dashboard.png` | 工作台 |
| `admin-audit.png` | 活动审核 |
| `admin-activity.png` | 活动管理 |
| `admin-user.png` | 用户管理 |
| `admin-file-upload.png` | 素材上传（存储配置、上传预览、复制地址） |
| `admin-log.png` | 操作日志 |
| `server-swagger.png` | 后端 OpenAPI 接口文档 |

tabBar 图标由 `scripts/gen-tabbar-icons.mjs` 生成：24 栅格线性图标，常态 `#8a8f99` 描边、选中 `#1f6feb` 填充，输出 81×81 PNG 到 `app/src/static/tabbar/`。改图标改脚本里的路径数据后重跑即可。

## 教学包（4 次课 × 4 学时，共 16 学时）

面向课程教学：从零开发、测试、部署到云效平台小组协作的完整教学包，含 4 次课的教案与可复现教程。

| 文档 | 内容 |
| --- | --- |
| [教学包导览](docs/教学包/README.md) | 文件地图、教师备课路径、学生自学路径 |
| [从零构建教程（65 个真实提交）](docs/教学包/从零构建/README.md) | 从三端空项目按 commit 回放：后端 41 / 小程序 11 / 后台 9 / 共享 4 |
| [00 课程总览与教师手册](docs/教学包/00-课程总览与教师手册.md) | 课程设计、分组与角色、评价量规、课堂组织、风险预案 |
| [01 第 1 次课：环境搭建与后端跑通](docs/教学包/01-第1次课-环境搭建与后端跑通.md) | 环境、云效立项、数据库、后端启动 |
| [02 第 2 次课：核心业务与安全缓存](docs/教学包/02-第2次课-核心业务与安全缓存.md) | 登录绑定、活动审核、报名候补、签到、Security/Redis/短信 |
| [03 第 3 次课：双端前端与扩展能力](docs/教学包/03-第3次课-双端前端与扩展能力.md) | 小程序/后台联调、文件上传、短信登录、登出 |
| [04 第 4 次课：测试流水线与部署答辩](docs/教学包/04-第4次课-测试流水线与部署答辩.md) | 测试、云效 Flow、ECS 部署、答辩与复盘 |
| [05 云效平台操作手册（学生版）](docs/教学包/05-云效平台操作手册（学生版）.md) | 项目/需求/任务/缺陷、Codeup、Flow、Testhub |
| [06 ECS 部署与运维实操手册](docs/教学包/06-ECS部署与运维实操手册.md) | 服务器初始化、Nginx、systemd、云效主机部署 |
| [07 可复现事实清单与验收表](docs/教学包/07-附录-可复现事实清单与验收表.md) | 版本/端口/账号/命令/验收口径的唯一来源 |
| [08 可复现性验证报告](docs/教学包/08-可复现性验证报告.md) | 独立复跑记录、未验证项与风险 |

> 项目设计/需求/测试/部署等课程设计文档（00–12 共 13 份）见 [docs/项目文档/](docs/项目文档/)。
