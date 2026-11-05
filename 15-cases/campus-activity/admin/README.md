# 校园活动管理后台

Vue3 + Vite + Element Plus + TypeScript，负责活动审核、活动管理、用户与角色、操作日志。

## 启动

```bash
pnpm install
pnpm dev
```

默认 `http://localhost:5173`（端口被占用时 vite 会自动换到 5174 / 5175，以终端输出为准）。

账号：`admin` / `Admin@123`。

## 目录

```
src/
├── main.ts                      入口，注册 Element Plus 与中文语言包
├── App.vue
├── env.d.ts                     vue 模块声明
├── types/api.ts                 接口数据结构（ApiResult、PageResult、ActivityItem 等）
├── api/
│   ├── request.ts               axios 实例、token 注入、401 跳登录
│   └── index.ts                 接口函数，带泛型返回
├── router/index.ts              路由与登录守卫（hash 模式）
├── layout/index.vue             侧边栏 + 顶栏
├── components/
│   └── ActivityDetailDrawer.vue 活动详情抽屉（审核时查看）
├── utils/dict.ts                状态与角色文案
└── views/
    ├── login/index.vue          登录页
    ├── dashboard/index.vue      工作台：6 个指标 + 热门活动
    ├── audit/index.vue          活动审核：通过 / 驳回（必填理由）
    ├── activity/index.vue       活动管理：搜索、筛选、下架
    ├── user/index.vue           用户管理：角色分配、绑定组织
    └── log/index.vue            操作日志
```

## 接口

请求前缀 `/api`，开发环境由 vite 代理到 `http://127.0.0.1:8080`，见 `vite.config.ts`。

| 页面 | 接口 |
| --- | --- |
| 登录 | `POST /api/admin/auth/login` |
| 工作台 | `GET /api/admin/dashboard` |
| 活动审核 | `GET /api/admin/activities/audit-list`、`POST /api/admin/activities/{id}/audit` |
| 活动管理 | `GET /api/admin/activities`、`POST /api/admin/activities/{id}/offline` |
| 用户管理 | `GET /api/admin/users`、`POST /api/admin/users/{id}/roles`、`GET /api/admin/organizations` |
| 操作日志 | `GET /api/admin/logs` |
| 活动详情 | `GET /api/app/activities/{id}`（公开接口，无需 token） |

## 检查与构建

```bash
pnpm type-check   # vue-tsc 类型检查
pnpm build        # 产物在 dist/
```

两项均已通过，类型检查零错误。

## 已验证

在 1440×900 视口下实际登录并进入工作台，指标卡与热门活动表格数据来自后端真实数据（用户数 5、活动总数 8、待审核 1、报名总数 8、签到总数 4）。
