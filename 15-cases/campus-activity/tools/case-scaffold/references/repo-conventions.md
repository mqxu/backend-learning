# 仓库口径速查

> 本文件是 `case-scaffold` 的事实来源。脚本与模板都按这里生成；手工补登记时同样以这里为准。
> 与仓库现状不一致的地方，先改脚本/模板，再改本文件，不要两边各说一套。

## 1. 仓库结构

```
backend-learning/
├── pom.xml                 # 父 POM，聚合 01–15-cases
├── README.md               # 模块导航表 + 端口表
├── 01-quickstart/          # 教学模块
│   ├── pom.xml
│   ├── README.md
│   └── src/main/java/top/mqxu/quickstart/
├── ...
├── 15-cases/
│   ├── pom.xml             # 只聚合 zhihu、task-commit
│   ├── zhihu/
│   ├── task-commit/
│   └── campus-activity/    # 前后端分离，后端在 server/，独立父 POM
├── docs/
│   ├── index.md            # VitePress 首页与导航
│   ├── 01-quickstart.md    # 每个模块一篇教程
│   ├── 15-zhihu.md
│   └── public/
│       ├── shots/          # run-cases 生成的实测截图（webp）
│       └── diagrams/       # 手绘 SVG
└── tools/
    ├── run-cases.mjs       # 用例 → 真实请求 → HTML/截图/OpenAPI
    ├── cases/<module>.json # 接口用例清单
    └── case-scaffold/      # 本 skill
```

## 2. 编号与命名

| 类型 | 目录 | 教程 | 根 README 行 | docs/index.md |
| --- | --- | --- | --- | --- |
| 教学模块 | `16-comments/` | `docs/16-comments.md` | `16` | `/16-comments` |
| 综合案例 | `15-cases/notes/` | `docs/15-notes.md` | `15-4` | `/15-notes` |

约定：

- 目录名 `编号-slug`，slug 小写、连字符分隔。
- Maven `artifactId` 与目录同名（案例例外：目录名即 artifactId，如 `task-commit`）。
- Java 包名默认 `top.mqxu.<slug>`，多段 slug 去掉连字符（`16-file-upload` → `top.mqxu.fileupload`）；
  与教学主题强相关的模块可以按语义命名，但要全模块统一。
- 启动类 `<PascalSlug>Application`，放在包根。
- 教程文件名与模块目录名完全一致，位置在 `docs/`。

## 3. 端口

| 模块 | 端口 | 模块 | 端口 |
| --- | --- | --- | --- |
| 01-quickstart | 8001 | 09-file | 8009 |
| 02-config | 8002 | 10-doc | 8010 |
| 03-logging | 8003 | 11-test | 8011 |
| 04-web | 8004 | 12-actuator | 8012 |
| 05-mybatis | 8005 | 13-deploy | 8013 |
| 06-redis | 8006 | 14-toolkit | 8014 |
| 07-schedule | 8007 | 15-cases/zhihu | 8080 |
| 08-security | 8008 | 15-cases/campus-activity | 8016 |
| | | 15-cases/task-commit | 8017 |

新模块取未占用的 8000–8099 段；不要复用、不要改老模块的端口。
`server.port` 只写在 `src/main/resources/application*.yml` 顶层 `server:` 下；
`app.port` 之类的业务配置不算模块端口。

## 4. 模块五件套

| 文件 | 作用 | 缺失后果 |
| --- | --- | --- |
| `pom.xml` | 继承根父 POM（案例继承 `15-cases`） | 聚合构建失败 |
| `src/main/resources/application.yml` | 端口 + 应用名 | 端口回落到 8080，与用例不符 |
| `src/main/java/top/mqxu/<pkg>/<Xxx>Application.java` | 启动类 | 无法 `java -jar` |
| `README.md` | 接口表、运行命令、目录结构 | 根导航点进来是空目录 |
| `docs/<编号>-<slug>.md` | 教程 | README 的「详细教程」404 |

可选但推荐：

- `tools/cases/<module>.json`：接口实测用例；没有它就只能手测，教程没有实测截图。
- `src/test/java/...`：自动化测试；没有测试的模块在 CI 里只能编译。

目录模板（最小可运行）：

```
16-comments/
├── pom.xml
├── README.md
├── src/main/java/top/mqxu/comments/CommentsApplication.java
├── src/main/java/top/mqxu/comments/controller/ModuleInfoController.java
├── src/main/resources/application.yml
└── src/test/java/top/mqxu/comments/CommentsApplicationTests.java
```

## 5. README 模板口径

```markdown
# 16 · 评论

> 📖 详细教程：[docs/16-comments.md](../docs/16-comments.md)

一句话定位这个模块解决什么问题。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/hello` | 模块信息，验证骨架能启动 |

## 运行

```bash
mvn -pl 16-comments spring-boot:run
curl http://127.0.0.1:8018/hello
```

## 目录结构

（代码结构说明，随实现补）
```

要求：

- 第一行 `# 编号 · 中文标题`，与根导航、教程标题一致。
- 第二行必须是教程链接，路径为 `../docs/<编号>-<slug>.md`（案例是 `../../docs/...`）。
- 接口表只写真实存在的接口，方法用大写。
- 运行命令用 `mvn -pl <module> spring-boot:run`，不要写绝对路径。

## 6. 教程章节模板

仓库所有教程统一按这个顺序（标题里的编号从 0 开始）：

1. `# 编号 · 标题：副标题`
2. `## 0. 先看结果` —— 先给截图与三条命令，不铺垫
3. `## 1. 这个模块解决什么问题`
4. `## 2. 准备环境` ——  JDK、依赖、端口、必需中间件
5. `## 3. 创建模块` —— 目录、POM、application.yml
6. `## 4. 核心实现` —— 按主题拆 3–6 节，每节一个小目标
7. `## 5. 运行与实测` —— 真实命令 + 真实输出 + 截图
8. `## 6. 常见问题` —— 至少 3 条，写实测踩过的坑
9. `## 7. 练习`
10. `## 8. 小结`

写作口径：

- 先结论后原因，一段不超过 6 行；能用表格的不用长段落。
- 代码片段来自真实源码，单段不超过 30 行。
- 数字（用例数、覆盖率、耗时）必须来自实际运行，不确定就写「设计目标」。
- 截图放 `docs/public/shots/<module>-<场景>.webp`，在文中用 `![说明](./public/shots/xxx.webp)`。
- 图（非截图）放 `docs/public/diagrams/*.svg`，由 `software-eng-diagrams` skill 生成。

## 7. 接口用例 JSON schema

`tools/cases/<module>.json`：

```json
{
  "module": "16-comments",
  "port": 8018,
  "title": "16 · 评论",
  "tag": "评论",
  "groupSlug": { "公开接口": "public", "评论管理": "comments" },
  "cases": [
    {
      "id": "hello",
      "name": "16.1 模块信息",
      "desc": "一句话说明这条用例验证什么，写进截图。",
      "group": "公开接口",
      "req": { "method": "GET", "path": "/hello" },
      "openapi": { "summary": "模块信息" }
    }
  ]
}
```

字段规则：

- `module` 必须与目录名（案例是 `15-cases/<name>`）一致。
- `port` 必须与 `application.yml` 的 `server.port` 一致，`run-cases.mjs` 用它拼 URL。
- `groupSlug` 覆盖 `cases[].group` 的全部取值；缺了会退化成拼音/序号。
- `cases[].id` 全文件唯一，建议 `kebab-case`。
- `req` 支持 `method`、`path`、`query`、`headers`、`json`、`form`、`multipart`；
  需要先取 token 的用 `capture`/`var` 机制，写法参考 `tools/cases/15-cases/campus-activity.json`。

## 8. 五处登记点

| 登记点 | 位置 | 内容 |
| --- | --- | --- |
| 聚合 POM | 根 `pom.xml` `<modules>`；案例是 `15-cases/pom.xml` | `<module>16-comments</module>` |
| 模块导航 | 根 `README.md` 的「模块导航」表 | 序号、目录、主题、状态、教程链接 |
| 端口表 | 根 `README.md` 的「各模块端口」表 | 模块路径、端口、外部依赖 |
| 文档导航 | `docs/index.md` 的「模块导航」/「综合案例」表 | 序号、标题、路由 |
| 首页数量 | `docs/index.md` hero 的 tagline | 「14 个技术模块加 3 个综合案例」 |

另外：

- `docs/15-cases.md`（案例总览）里的案例表需要手工补一行，脚本不自动改。
- `tools/cases` 目录下的用例文件由脚本生成，之后按第 7 节补全。
- Apifox/OpenAPI 不需要手工登记：`run-cases.mjs` 会把用例导出成 OpenAPI。

## 9. 特例

**campus-activity（15-cases/campus-activity）**

- 前端 `app/`（uni-app）与 `admin/`（Vue3）独立，后端在 `server/`。
- 后端父 POM 是 `spring-boot-starter-parent`，不是 `backend-learning`，因此不在 `15-cases/pom.xml` 聚合。
- `server/src/main/resources/application.yml` 端口 8016；`scripts/smoke.sh` 用 20 项 curl 断言覆盖主链路。
- 演示数据有状态，跑报名相关用例前要重新导入 `schema.sql` + `data.sql`。
- 生成同类独立案例时，`new-case.mjs` 只能生成单模块骨架；前后端分离结构需手工补 `app/`、`admin/`。

**02-config 的 application.yml**

- 一个文件里有多个 `---` 文档段，`app.port: 8002` 是业务配置，不是模块端口。
- 端口检查只认 `server.port`，不要被 `app.port` 干扰。

**15-cases/pom.xml**

- 只聚合 `zhihu` 与 `task-commit`；`campus-activity` 是注释里写明的例外。
- 新案例若是独立工程，不要强行加入聚合，先在 `15-cases/pom.xml` 注释里说明。

## 10. 检查与验证

```bash
# 全仓结构与端口自检
node tools/case-scaffold/scripts/check-module.mjs

# 单模块
node tools/case-scaffold/scripts/check-module.mjs --module 16-comments

# 编译验证（新模块）
mvn -pl 16-comments -am -DskipTests compile

# 启动 + 用例 + 截图 + OpenAPI
node tools/run-cases.mjs 16-comments

# 测试
mvn -pl 16-comments test
```

`check-module.mjs` 只做静态检查，不替代编译与实跑；编译和 `run-cases` 才是最终验收。
