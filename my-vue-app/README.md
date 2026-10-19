# my-vue-app · 短信登录前端

> 📖 详细教程：[docs/sms-login.md](../docs/sms-login.md)

用 create-vue 生成的 Vue 3 + Vite + TypeScript 工程，实现短信验证码登录页与首页，对接 `test-redis1` 模块的接口。

## 环境

Node.js 22.18.0 及以上，包管理器用 pnpm（也可以换成 npm）。

## 启动

```bash
pnpm install
pnpm dev
```

开发服务器默认 5173，端口被占用时会自动顺延。后端地址在 `.env` 里配置，默认 `http://localhost:8006`。

```bash
pnpm dev          # 开发服务器
pnpm build        # 类型检查 + 打包到 dist
pnpm type-check   # 只做类型检查
pnpm preview      # 预览打包产物
```

`pnpm build` 会先跑 `vue-tsc -b`，类型有错会直接失败。

## 目录结构

```
my-vue-app
├── .env                        后端地址
├── env.d.ts                    环境变量类型
├── index.html
├── package.json
├── tsconfig.json               工程引用根
├── tsconfig.app.json           应用侧编译选项
├── tsconfig.node.json          构建脚本侧编译选项
├── vite.config.ts
└── src
    ├── App.vue
    ├── main.ts                 应用入口
    ├── api/auth.ts             接口定义
    ├── config/index.ts         前后端要对齐的常量
    ├── router/index.ts         路由与守卫
    ├── types/api.ts            接口类型
    ├── utils/auth.ts           登录态读写
    ├── utils/request.ts        axios 封装
    └── views
        ├── Login.vue
        └── Home.vue
```

## 与后端的约定

后端约定 HTTP 状态码恒为 200，成功与否看响应体的 `code`；`message` 是提示文案的来源。`src/utils/request.ts` 的响应拦截器按这个约定统一处理，业务码为 `40100` 时清掉本地 token 并回登录页。

三个常量前后端各有一份定义，改动时要一起改：

| 含义 | 前端 | 后端 | 取值 |
| --- | --- | --- | --- |
| 重发间隔 | `SEND_INTERVAL_SECONDS` | `mqxu.sms.send-interval-seconds` | 60 秒 |
| 验证码有效期 | `CODE_TTL_MINUTES` | `mqxu.sms.code-expire-seconds` | 5 分钟 |
| 登录态失效码 | `CODE_TOKEN_INVALID` | `ErrorCode.TOKEN_INVALID` | 40100 |

## 主要文件

| 文件 | 说明 |
| --- | --- |
| `src/views/Login.vue` | 手机号校验、发送验证码、60 秒倒计时、验证码登录 |
| `src/views/Home.vue` | 挂载时调用 `/user/me` 校验 token，展示手机号与登录时间 |
| `src/utils/request.ts` | 请求带 token，响应按业务码分流，区分业务失败与网络失败 |
| `src/router/index.ts` | `/home` 需要登录态，本地无 token 时回 `/login` |
