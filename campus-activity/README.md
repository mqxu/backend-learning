# 校园活动小程序

学生发现活动、报名、签到，组织者发布与核销，团委审核与统计的一条完整链路。

- 小程序端：uni-app + Vue3 + Vite
- 后端：Spring Boot 4.1.1 + MyBatis-Plus 3.5.18.1 + MySQL 8
- 管理后台：Vue3 + Vite + Element Plus

需求文档见仓库根目录的 `校园活动小程序-PRD.html`。

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
| MySQL | 8.0+ | 开发库端口 3308 |
| Node | 18+ | 实测 22 |
| uni-app | 3.0.0-5020620260917001 | Vue3 版 |
| TypeScript | 5.9 | 前端统一使用 TS，`vue-tsc` 做类型检查 |

## 快速开始

### 1. 启动数据库

```bash
docker run -d --name campus-mysql \
  -p 3308:3306 \
  -e MYSQL_ROOT_PASSWORD='Campus@2026' \
  -e MYSQL_DATABASE=campus_activity \
  -e TZ=Asia/Shanghai \
  mysql:8 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci
```

已有 MySQL 实例时跳过这步，改 `server/src/main/resources/application.yml` 里的连接串即可。

### 2. 初始化表与演示数据

```bash
cd server/src/main/resources/db
mysql -h127.0.0.1 -P3308 -uroot -p'Campus@2026' campus_activity < schema.sql
mysql -h127.0.0.1 -P3308 -uroot -p'Campus@2026' campus_activity < data.sql
```

### 3. 启动后端

```bash
cd server
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"
```

打开 `http://127.0.0.1:8080/swagger-ui.html` 可以看到全部接口。

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
| 小程序 | 已跑通：13 个页面在 H5 下正常渲染（浏览器截图核对），`pnpm type-check` 零错误，`pnpm build:h5` 通过 |
| 管理后台 | 已跑通：实际登录后进入工作台，指标卡与热门活动表格显示后端真实数据；`pnpm build`、`pnpm type-check` 均通过 |

前端依赖用 pnpm 安装（npm 在本机受代理限制会超时）。

## 分步教程

1. `docs/01-环境准备.md`
2. `docs/02-数据库设计.md`
3. `docs/03-后端工程搭建.md`
4. `docs/04-核心业务实现.md`
5. `docs/05-小程序开发.md`
6. `docs/06-管理后台.md`
7. `docs/07-联调与排错.md`
8. `docs/08-部署上线.md`
