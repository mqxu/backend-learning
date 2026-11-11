---
name: case-scaffold
description: 为后端工程化多模块仓库新增教学模块或综合案例：生成模块骨架、POM、启动类、application.yml、README、教程骨架、tools/cases 接口用例，并同步根 README / docs 导航 / 聚合 POM；生成后用脚本检查模块结构与端口冲突。当需要新增案例、新增模块、补教程骨架、检查模块结构与端口冲突时使用。Also use when scaffolding a new Spring Boot teaching module or case in the backend-learning repo, or validating module structure, port conflicts, and documentation navigation.
agent_created: true
---

# 案例脚手架

新增一个模块或案例，从来不是 `mkdir` 一下：仓库里有五处登记点、三条命名约定、一张端口表，
漏一处就是链接 404、端口打架或教程找不到图。这个 skill 把「生成骨架」和「生成后自检」绑在一起。

## 一、先判断：模块、案例，还是给现有模块加功能

| 你要做的事 | 类型 | 落点 |
| --- | --- | --- |
| 讲一个技术点（缓存、事务、消息……） | 教学模块 | 根目录 `NN-topic/` |
| 做一套完整业务（登录、订单、社区……） | 综合案例 | `15-cases/<name>/` |
| 现有模块加一个接口/表 | 功能改动 | 不生成新模块，改现有代码 |

拿不准就看根 README 的模块导航：01–14 是教学模块，15-x 是综合案例。新案例通常延续 15 系列。

## 二、动手前确认三件事

1. **编号**：教学模块取下一个未占用的两位数；案例取 `15-<n>`。
2. **端口**：查根 README 的端口表，再全仓查 `server.port`，确认没被占用。
3. **包名**：默认 `top.mqxu.<slug>`（slug 去掉编号和连字符）。

```bash
# 看现有模块
ls -d [0-9][0-9]-*/ 15-cases/*/ 2>/dev/null
# 看现有端口
grep -rn "port:" --include="application*.yml" . | grep -v target
```

## 三、生成

推荐把 skill 放在仓库根 `tools/case-scaffold/`，在仓库根执行（放在别处就用实际路径）：

```bash
# 教学模块：16-comments，端口 8018
node tools/case-scaffold/scripts/new-case.mjs 16-comments \
  --port 8018 --title "评论" --desc "围绕评论的增删查与分页"

# 综合案例：15-cases/notes，端口 8019
node tools/case-scaffold/scripts/new-case.mjs 15-cases/notes \
  --port 8019 --title "随手笔记"
```

`15-cases/notes` 这种带斜杠的名字自动按案例处理；也可以显式加 `--kind case`。

| 参数 | 说明 |
| --- | --- |
| `--port` | 必填。会检查 README 端口表、application*.yml 与 tools/cases 是否已占用 |
| `--title` | 中文标题，默认用 slug |
| `--subtitle` | 教程副标题，默认「从零到可验证」 |
| `--tag` | 接口用例分组标签，默认同标题 |
| `--package` | Java 包名，默认 `top.mqxu.<slug>` |
| `--desc` | README 与教程里的一句话定位 |
| `--root` | 仓库根目录，默认从当前目录向上自动查找 |
| `--dry-run` | 只打印将要生成/修改的文件，不落盘 |
| `--no-register` | 只生成文件，不改根 README / docs 导航 / 聚合 POM |
| `--force` | 允许覆盖已存在文件 |

生成后会做五处登记（除非 `--no-register`）：

1. 根 `pom.xml` 的 `<modules>`（案例登记到 `15-cases/pom.xml`）
2. 根 `README.md` 的模块导航表
3. 根 `README.md` 的端口表
4. `docs/index.md` 的模块/案例导航表（并更新 hero 里的模块数量）
5. `docs/<编号>-<slug>.md`、`README.md`、`tools/cases/<module>.json`

生成的是**骨架**，不是成品：README 的接口表、教程每一节、接口用例都要按真实代码补。

## 四、生成后自检

```bash
# 只查新模块
node tools/case-scaffold/scripts/check-module.mjs --module 16-comments

# 全仓查：端口冲突、导航链接、教程图片、case JSON、聚合 POM
node tools/case-scaffold/scripts/check-module.mjs
```

| 检查 | 级别 | 说明 |
| --- | --- | --- |
| 端口重复 | FAIL | 两个模块用同一个 `server.port` |
| 聚合 POM 指向不存在的模块 | FAIL | `mvn` 会直接报错 |
| 导航链接目标不存在 | FAIL | README/docs 里的本地链接 |
| 教程图片引用不存在 | FAIL | `docs/*.md` 引用的截图/图 |
| README 端口表与实际 yml 不一致 | WARN | 以 yml 为准修表 |
| 缺 README/POM/启动类/yml/教程 | FAIL | 模块五件套 |
| 缺 tools/cases 用例 | WARN | 没有实测产物的模块 |
| 教程没有「0. 先看结果」或「小结」 | WARN | 与仓库教程口径不一致 |
| case JSON 字段缺失/端口不符 | WARN | 跑 `run-cases` 前修 |

`--strict` 把 WARN 也当失败；`--json` 输出机器可读结果。

## 五、模板与占位符

`templates/` 下是四类骨架：模块 POM、启动类与探活接口、application.yml、README、教程、case JSON。
占位符只有 `{{MODULE}}`、`{{ARTIFACT}}`、`{{PARENT_ARTIFACT}}`、`{{PACKAGE}}`、`{{APP_CLASS}}`、
`{{PORT}}`、`{{TITLE}}`、`{{NAV_INDEX}}`、`{{TUTORIAL}}`、`{{TUTORIAL_LINK}}` 这些；
新增占位符要同步改 `scripts/new-case.mjs` 和本表。

改模板时遵守 `references/repo-conventions.md` 的口径，不要按个人偏好改章节或命名。

## 六、几条不能破的规矩

**端口必须唯一。** 01–14 用 8001–8014，15 系列已占 8016、8017、8080。新模块先查表再定，
冲突就换新端口，不要改别人的端口。

**编号和目录名就是链接。** `docs/16-comments.md`、`16-comments/`、根 README 的教程列、docs/index 的路由
四者必须同名。改一个要四处一起改。

**campus-activity 是特例。** 它是前后端分离项目，后端有自己的父 POM（`spring-boot-starter-parent`），
不放进 `15-cases/pom.xml` 聚合。新案例若同样是独立工程，生成后手动确认聚合与构建方式，
不要直接塞进 `15-cases`。

**教程先写「0. 先看结果」。** 仓库里所有教程都是先给三条命令和截图，再讲原理。骨架已按这个顺序生成，
不要改成「第一章 概述」。

**不生成假数据。** 骨架里的接口用例只放一条 `/hello` 探活；截图、接口清单、测试数字都要跑出来再填，
不要先写「20 项断言全部通过」。

**演示数据有状态的模块要能重置。** 如果模块连 MySQL 且用例会写数据（报名、下单），
在 README 里写清楚「跑用例前先重新导入 schema.sql + data.sql」，并检查是否已有重置脚本。

## 七、参考文件

| 文件 | 什么时候读 |
| --- | --- |
| `references/repo-conventions.md` | 改模板或手工补登记前。端口、命名、教程章节、case JSON schema 的全量口径 |
