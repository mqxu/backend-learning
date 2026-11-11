# 从零构建教程：65 个真实提交，代码一行一行长出来

> 这是对教学包的补充：**从创建三端空项目开始**，按「后端 / 小程序 / 管理后台」三轨交错提交，每一步一个真实 Git commit。
> 代码不是“从天上掉下来的”：每个文件都对应某一次提交，`git show <commit>` 能看到它是什么时候、和哪些文件一起被创建/修改的。
>
> 分支：`tutorial/from-zero`；标签：`tutorial-v1.0.0`；数量：**65 个提交**（B 后端 41 + A 小程序 11 + D 后台 9 + S 共享 4）。
> 完整机器可读清单：[commit-inventory.json](commit-inventory.json)；人读版：[05-提交总表与检查点验收](05-提交总表与检查点验收.md)。

---

## 1 这套教程解决什么问题

之前的教学包（00–08 号文档）是“项目已经存在”的前提下写的：讲架构、讲课堂、讲验收，但没有交代 **server/、app/、admin/ 里这 113 + 34 + 18 个文件是怎么从空目录一步步长出来的**。
本套教程把这件事补上：

- **起点是真的空**：只有一个 `.gitignore` 和占位 README；Spring Boot 工程、uni-app 工程、Vite 后台都由命令/配置从零创建。
- **每一步是真的提交**：65 个提交，每个提交有规范信息、文件清单、可查看的 diff；代码来源 = 提交历史。
- **前后端并行**：同一时间线上 `B*`（后端）、`A*`（小程序）、`D*`（后台）交错推进；组内按角色分工，检查点汇合。
- **检查点可验证**：C1–C6 都有明确命令与预期结果，且已在 2026-11-11 实测通过（见第 5 节）。

## 2 交付物

| 文件 | 内容 |
| --- | --- |
| `campus-activity-tutorial.bundle` | **完整 Git 仓库包**（65 个提交 + tag），克隆后即可 `git log/show` 回放 |
| `commit-inventory.json` | 机器可读清单：每一步 SHA、提交信息、文件、stat |
| [05-提交总表与检查点验收](05-提交总表与检查点验收.md) | 65 个提交总表、并行阶段、检查点命令与结果 |
| [01-后端从零构建](01-后端从零构建.md) | B01–B41：Maven 工程 → 骨架 → 领域模型 → 安全/业务/Redis/短信/文件 |
| [02-小程序从零构建](02-小程序从零构建.md) | A01–A11：uni-app 工程 → 请求层 → 14 个页面 |
| [03-管理后台从零构建](03-管理后台从零构建.md) | D01–D09：Vite 工程 → 请求层 → 登录/布局/7 个视图 |
| [04-三端联调、测试与上线](04-三端联调、测试与上线.md) | C1–C6 验收、三端联调、测试、云效流水线与 ECS 上线 |
| [06-独立验证记录](06-独立验证记录.md) | 独立复跑 65 提交与 C1–C6 的验证矩阵、不一致项与修复记录 |

## 3 前置条件

与教学包 [07-附录-可复现事实清单与验收表](../07-附录-可复现事实清单与验收表.md) 完全一致：

| 组件 | 要求 |
| --- | --- |
| JDK | 21（实测 21.0.12.1） |
| Maven | 3.9（实测 3.9.11） |
| Node / pnpm | Node 18+（实测 v24.20.0，建议 20/22 LTS）/ pnpm 8+（实测 12.14.0） |
| MySQL | 8.0+（实测 8.4.6），库名 `campus_activity` |
| Redis | 6.2+（实测 8.2.2），密码 `abc123`，database 11 |
| 端口 | 后端 8016、H5 5174、后台 5173 |
| 账号 | 后台 `admin / Admin@123`；组织者 `org2001 / Org@123`；手机号 `13800000003/4/5` |

## 4 三种使用方式

### 方式 A：克隆 bundle，从第 1 个提交开始回放（推荐自学 / 备课）

```bash
# 在任意空目录执行；bundle 路径按你的实际位置调整
git clone -b tutorial/from-zero \
  /path/to/campus-activity/docs/教学包/从零构建/campus-activity-tutorial.bundle \
  campus-activity-from-zero

cd campus-activity-from-zero
git log --oneline | tail -5      # 看最早几个提交
git log --oneline | head -5      # 看最后几个提交
git show <commit>                # 查看任意一步的完整 diff
```

学习节奏建议：`git checkout <检查点>` 先看到“能跑的版本”，再 `git checkout <下一步>` 对照学习；或**跟着教程文档自己敲**，每完成一步就 `git add -A && git commit`，用 `git diff <教程commit> -- .` 对比自己与参考实现的差异。

### 方式 B：接入现有仓库，成为 `tutorial/from-zero` 分支（教师使用）

```bash
git fetch /path/to/campus-activity-tutorial.bundle \
  'refs/heads/tutorial/from-zero:refs/heads/tutorial/from-zero' \
  'refs/tags/tutorial-v1.0.0:refs/tags/tutorial-v1.0.0'
git branch -a | grep tutorial
git checkout tutorial/from-zero
```

> 为什么不是直接在当前仓库里建分支？因为本教学仓库的工作区在 `15-cases/campus-activity/`，而 `.git` 在仓库根目录；bundle 方式可以完整携带 65 个提交与 tag，且不干扰现有分支与工作区。

### 方式 C：推到云效 Codeup，供全班使用

```bash
git remote add yunxiao https://codeup.aliyun.com/<你的组织>/<仓库>.git
git push yunxiao tutorial/from-zero --tags
```

云效代码库页面切到 `tutorial/from-zero` 分支即可浏览提交历史；小组做项目时从该分支创建自己的 `feat/*` 分支，而不是直接在教程分支上开发（避免把回放历史改乱）。

## 5 检查点验收（每个都能复跑）

| 检查点 | 落点提交 | 验证命令 | 预期 | 实测 |
| --- | --- | --- | --- | --- |
| C1 | `ba086b2` | `cd server && mvn -o -DskipTests compile` | BUILD SUCCESS；应用能启动并监听 8016（此时点击 `/api/ping` 返回 **401 属预期**，见下方说明） | ✅ 2026-11-11 |
| C2 | `ee173f6` | `cd server && mvn -o -DskipTests compile` | BUILD SUCCESS | ✅ 2026-11-11 |
| C3 | `5a5c97b` | `cd server && mvn -o test` | 20/20 | ✅ 2026-11-11 |
| C4 | `067c5b3` | `cd app && pnpm type-check && pnpm build:h5` | 构建成功 | ✅ 2026-11-11 |
| C5 | `5f4da02` | `cd admin && pnpm type-check && pnpm build` | 构建成功 | ✅ 2026-11-11 |
| C6 | `4e7d08a6` | 启动后端后 `./scripts/smoke.sh` | 全部通过：33 项 | ✅ 2026-11-11 |

> 非检查点的中间提交**不承诺单独可编译/可运行**（按依赖顺序拆分文件时会出现“类还没建齐”的瞬时状态），这是正常的；跟到最近检查点即可。教程中每一步都会标注“本步验证方式”。
>
> **C1 的 401 是刻意设计的教学现象**：B01 的 `pom.xml` 已经引入 `spring-boot-starter-security`，但 `SecurityConfig` 要到 B15 才创建。Spring Boot 检测到 security starter 后会用默认安全链拦截所有请求，所以 C1 时 `/api/ping` 返回 401。B13–B15 会解释并修复它；如果想提前看到 `{"code":0,...}`，直接 `git checkout ee173f6`（C2）即可。

## 6 并行学习法（4–5 人小组）

| 角色 | 主要轨道 | 课堂动作 | 课后任务 |
| --- | --- | --- | --- |
| 后端 | B* | 跟 01 号文档，从 Maven 工程到文件上传 | 每个检查点前跑 `mvn -o test` |
| 小程序前端 | A* | 跟 02 号文档，从 uni-app 工程到 14 页 | 每个页面提交一次，截图对照 |
| 管理后台前端 | D* | 跟 03 号文档，从 Vite 工程到 7 个视图 | 每个视图提交一次，对照页面 |
| 测试 | 全链路 | 跟 04 号文档，维护检查点清单 | 每个 C 点记录截图与问题单 |
| DevOps | S* + 04 | 跟 04 号文档，负责流水线/部署 | 第 4 次课前把流水线跑绿 |

同步节奏：**同一条时间线上交错提交**，不是三条独立分支。课堂按“检查点”对齐（每 5–8 个提交一个），组内三轨同时推进；某个角色落后时，下个检查点前补齐即可，不会互相阻塞。

## 7 与 4 次课的对应

| 课次 | 覆盖提交 | 检查点 | 课堂产出 |
| --- | --- | --- | --- |
| 第 1 次课 | S00、B01–B12、A01–A02、D01 | C1 | 三端工程能创建、后端能启动并监听 8016（默认安全下 ping 401，C2 后 200）、云效项目与 24 条任务 |
| 第 2 次课 | B13–B39、A03–A09、D02–D07 | C2、C3 | 登录/活动/报名/签到链路 + Redis/短信/文件上传 + 20 用例 |
| 第 3 次课 | A10–A11、D08–D09、S01 | C4、C5 | 两端页面补齐、封面/素材上传、冒烟脚本 |
| 第 4 次课 | S02–S03 + 04 号文档 | C6 | 云效流水线、ECS 上线、`v1.0.0` 发布与答辩 |

## 8 每一步怎么读（文档统一结构）

每个提交在对应轨道文档里的写法：

1. **提交信息**：`<commit短SHA> <type(scope): subject>`
2. **这一步做什么**：一句话目标 + 为什么现在做
3. **涉及文件**：表格（路径 / 作用 / 大致行数），全部可在 `git show <commit>` 看到
4. **关键代码**：小文件全文、大文件核心片段；完整内容用 `git show <commit>:<path>` 查看
5. **命令与预期**：创建/生成命令、启动或验证命令、预期输出
6. **检查点**：属于哪个 C 点、怎么验收
7. **常见错误**：该步最容易踩的 1–3 个坑

## 9 数据初始化（唯一需要手工的前置）

代码从零，但数据库不是“凭代码自动长出来”的：`B04`/`B05` 两个提交给出了建表与演示数据脚本，**第一次启动前**需要执行：

```bash
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS campus_activity DEFAULT CHARSET utf8mb4"
cd server/src/main/resources/db
mysql -h127.0.0.1 -P3306 -uroot -p123456 campus_activity < schema.sql
mysql -h127.0.0.1 -P3306 -uroot -p123456 campus_activity < data.sql
```

Redis 只需启动并设置密码（见 07 事实清单），应用会自动使用 database 11。

## 10 维护约定

- 教程对应版本：`tutorial-v1.0.0`（65 提交）。若主项目代码继续演进，重新生成分支后需同步更新 `commit-inventory.json` 与 05 号总表。
- 文档与提交不一致时，以 `git show` 的实际提交内容为准，并在教学包维护记录中登记。
- 云效界面步骤以云效当前版本为准；Git 操作本身与平台无关。

---

最后核对日期：2026-11-11；核对人：教学包编写组。
