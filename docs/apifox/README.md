# 接口清单（可导入 Apifox）

这里的 JSON 不是手写的，是 `tools/run-cases.mjs` 启动每个模块、发真实请求之后生成的 OpenAPI 描述。
接口变了重新跑一遍脚本即可，不用手工维护。

## 导入 Apifox

两种方式，按需要选：

**按模块导入（推荐）**

每个模块是独立服务、各占一个端口，分开导入到不同目录，调试时不会串环境。

1. Apifox 里新建项目，进入「接口管理」
2. 右上角「导入数据」→ 选「OpenAPI / Swagger」
3. 选「URL 导入」填运行中服务的地址，或直接选「文件导入」上传下面的 JSON
4. 导入模式选「覆盖」，重复导入不会产生重复接口

**一次性导入全部**

用 `all.openapi.json`，所有模块合成一份文档，按「模块 + 标签」分组。
路径重复的接口已加模块前缀（如 `/05-mybatis/users`），原始地址写在接口描述里。

## 模块清单

| 模块 | 端口 | 接口数 | 文件 |
| --- | --- | --- | --- |
| `01-quickstart` | 8001 | 6 | [01-quickstart.openapi.json](01-quickstart.openapi.json) |
| `02-config` | 8002 | 7 | [02-config.openapi.json](02-config.openapi.json) |
| `03-logging` | 8003 | 5 | [03-logging.openapi.json](03-logging.openapi.json) |
| `04-web` | 8004 | 14 | [04-web.openapi.json](04-web.openapi.json) |
| `05-mybatis` | 8005 | 19 | [05-mybatis.openapi.json](05-mybatis.openapi.json) |
| `06-redis` | 8006 | 23 | [06-redis.openapi.json](06-redis.openapi.json) |
| `07-schedule` | 8007 | 8 | [07-schedule.openapi.json](07-schedule.openapi.json) |
| `08-security` | 8008 | 13 | [08-security.openapi.json](08-security.openapi.json) |
| `09-file` | 8009 | 8 | [09-file.openapi.json](09-file.openapi.json) |
| `10-doc` | 8010 | 10 | [10-doc.openapi.json](10-doc.openapi.json) |
| `11-test` | 8011 | 3 | [11-test.openapi.json](11-test.openapi.json) |
| `12-actuator` | 8012 | 11 | [12-actuator.openapi.json](12-actuator.openapi.json) |
| `13-deploy` | 8013 | 6 | [13-deploy.openapi.json](13-deploy.openapi.json) |
| `14-toolkit` | 8014 | 18 | [14-toolkit.openapi.json](14-toolkit.openapi.json) |
| `15-cases-campus-activity` | 8016 | 16 | [15-cases-campus-activity.openapi.json](15-cases-campus-activity.openapi.json) |
| `15-cases-task-commit` | 8017 | 9 | [15-cases-task-commit.openapi.json](15-cases-task-commit.openapi.json) |
| `15-cases-zhihu` | 8080 | 14 | [15-cases-zhihu.openapi.json](15-cases-zhihu.openapi.json) |
| `all` | 8001 | 301 | [all.openapi.json](all.openapi.json) |

合计 491 个接口。

## 重新生成

```bash
# 单个模块：启动服务 → 发真实请求 → 出截图与 openapi
node tools/run-cases.mjs 05-mybatis

# 合并成一份
node tools/merge-openapi.mjs
```

## 说明

- 请求示例、响应结构都来自实际调用，鉴权相关的接口需要先登录拿 token（脚本里已按顺序处理）
- `resetRedis` 类的模块每次运行前会清空对应的 Redis db，避免上一次的数据干扰
- 截图在 `docs/public/shots/`，每张图对应一个功能分组
