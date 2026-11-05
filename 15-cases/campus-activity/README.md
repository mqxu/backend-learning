# 校园活动小程序

学生发现活动、报名、签到，组织者发布与核销，团委审核与统计的一条完整链路。

- 小程序端：uni-app + Vue3 + Vite
- 后端：Spring Boot 4.1.1 + MyBatis-Plus 3.5.18.1 + MySQL 8
- 管理后台：Vue3 + Vite + Element Plus

> 📖 详细教程：[docs/15-campus-activity.md](../../docs/15-campus-activity.md)

## 目录结构

```
campus-activity/
├── server/          Spring Boot 后端
│   └── src/main/resources/db/    schema.sql、data.sql
├── app/             uni-app 小程序
├── admin/           管理后台
├── scripts/
│   └── smoke.sh     端到端冒烟测试
└── docs/            分步教程
```

## 技术版本

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | Spring Boot 4 要求 17 以上 |
| Spring Boot | 4.1.1 | |
| MyBatis-Plus | 3.5.18.1 | 必须用 `mybatis-plus-spring-boot4-starter` |
| MySQL | 8.0+ | 本机 3306，库名 `campus_activity` |
| Node | 18+ | 实测 22 |
| uni-app | 3.0.0-5020620260917001 | Vue3 版 |
| TypeScript | 5.9 | 前端统一使用 TS，`vue-tsc` 做类型检查 |

## 快速开始

### 1. 启动数据库

```bash
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS campus_activity DEFAULT CHARSET utf8mb4"
```

演示数据与 01-14 的模块分开存在 `campus_activity` 库，避免表名冲突。
连接信息在 `server/src/main/resources/application.yml`，账号密码可用 `MYSQL_USERNAME` / `MYSQL_PASSWORD` 覆盖。

### 2. 初始化表与演示数据

```bash
cd server/src/main/resources/db
mysql -h127.0.0.1 -P3306 -uroot -p123456 campus_activity < schema.sql
mysql -h127.0.0.1 -P3306 -uroot -p123456 campus_activity < data.sql
```

`data.sql` 里的时间以执行当天为基准，活动排期落在 08:00–22:00（如 14:00–17:00），不会出现凌晨开场的时间。

### 3. 启动后端

```bash
cd server
mvn spring-boot:run
```

打开 `http://127.0.0.1:8016/swagger-ui.html` 可以看到全部接口。

### 4. 启动小程序（H5 预览）

```bash
cd app
pnpm install
pnpm run dev:h5
```

访问 `http://127.0.0.1:5174`。微信小程序真机调试见 `docs/05-小程序开发.md`。

### 5. 启动管理后台

```bash
cd admin
pnpm install
pnpm run dev
```

访问 `http://127.0.0.1:5173`，账号 `admin` / `Admin@123`。

## 演示账号

| 身份 | 登录方式 | 说明 |
| --- | --- | --- |
| 学生 | 小程序自动登录 | 开发模式默认 `mock-student-1`，在「我的 → 切换测试身份」可换 |
| 组织者 | 小程序自动登录 | `mock-organizer-1`，所属组织为计算机学院学生会 |
| 管理员 | 后台账号密码 | `admin` / `Admin@123` |

学籍白名单：2023001 张小明、2023002 李小红、2023003 王大力、2024001 赵静、2024002 陈晨。

## 验收

```bash
./scripts/smoke.sh
```

脚本覆盖登录、绑定、建活动、提审、审核、报名、满员候补、扫码签到、重复签到拦截、伪造签到码拦截、统计共 20 项断言，全部通过即为部署成功。

## 当前验证状态

| 部分 | 状态 |
| --- | --- |
| 后端 | 已启动并跑通，`scripts/smoke.sh` 20 项断言全部通过（含名额并发、候补、重复签到、伪造签到码） |
| 数据库 | 12 张表已建，演示数据可重复导入 |
| 小程序 | 已跑通：13 个页面在 H5 下正常渲染（浏览器截图核对），tabBar 四组图标已接入，`pnpm type-check` 零错误，`pnpm build:h5` 通过 |
| 管理后台 | 已跑通：实际登录后进入工作台，指标卡与热门活动表格显示后端真实数据；`pnpm build`、`pnpm type-check` 均通过 |

前端依赖用 pnpm 安装（npm 在本机受代理限制会超时）。

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
| `app-mine-signups.png` | 我的报名（含签到凭证） |
| `app-checkin-scan.png` | 扫码签到 |
| `app-mine-organizer.png` | 我的（组织者身份） |
| `app-org-index.png` | 组织者工作台 |
| `app-org-edit.png` | 发布活动 |
| `app-org-signups.png` | 报名名单 |
| `app-org-checkin.png` | 签到核销（签到码 + 到场统计） |
| `admin-login.png` | 后台登录 |
| `admin-dashboard.png` | 工作台 |
| `admin-audit.png` | 活动审核 |
| `admin-activity.png` | 活动管理 |
| `admin-user.png` | 用户管理 |
| `admin-log.png` | 操作日志 |
| `server-swagger.png` | 后端 OpenAPI 接口文档 |

tabBar 图标由 `scripts/gen-tabbar-icons.mjs` 生成：24 栅格线性图标，常态 `#8a8f99` 描边、选中 `#1f6feb` 填充，输出 81×81 PNG 到 `app/src/static/tabbar/`。改图标改脚本里的路径数据后重跑即可。

## 分步教程

1. `docs/01-环境准备.md`
2. `docs/02-数据库设计.md`
3. `docs/03-后端工程搭建.md`
4. `docs/04-核心业务实现.md`
5. `docs/05-小程序开发.md`
6. `docs/06-管理后台.md`
7. `docs/07-联调与排错.md`
8. `docs/08-部署上线.md`
