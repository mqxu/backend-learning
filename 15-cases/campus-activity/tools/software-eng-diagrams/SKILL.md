---
name: software-eng-diagrams
description: 绘制软件工程专业图，覆盖系统架构图、UML 用例图、功能模块图、时序图、状态图、活动图、E-R 图、部署图、流程图、甘特图、类图。当需要为课程设计报告、需求规格说明书、毕业设计论文、架构文档、课件生成规范插图，或要把文字描述转成图表、检查已画的图是否合规范时使用。Also use when asked to draw, create, or fix UML diagrams, architecture diagrams, ER diagrams, sequence diagrams, flowcharts, or Gantt charts for software engineering documents.
agent_created: true
---

# 软件工程绘图

把需求或代码结构转成能直接插进文档的矢量图，符号符合 UML 2.5 与国内软件工程教材口径，
配色统一，交付 Mermaid 源码 + 渲染好的 SVG / PNG。

## 一、先定图型

不要拿到需求就开画。先确认要表达什么，再选图形。选错了，画得再精细也是废图。

最快的判断：

- 讲**系统由什么组成** → 系统架构图
- 讲**谁能做什么** → 用例图
- 讲**系统内部拆成什么** → 功能模块图
- 讲**一次交互的消息顺序** → 时序图
- 讲**一个东西的状态变化** → 状态图
- 讲**业务流程、谁负责哪步** → 活动图
- 讲**数据结构与联系** → E-R 图
- 讲**软件装在哪台机器** → 部署图
- 讲**程序里怎么判断怎么循环** → 流程图
- 讲**项目排期** → 甘特图
- 讲**类的属性方法和继承** → 类图

每种图的必备要素、常见错法、容易混淆的边界，见 `references/diagram-selection.md`。
动手前读对应章节，能挡掉大部分返工。

## 二、再选手法

| 图的类型 | 用什么画 | 原因 |
| --- | --- | --- |
| 时序图、状态图、活动图、流程图、E-R 物理模型、甘特图、类图 | Mermaid | 语法成熟，源码可编辑，能贴进 Typora / 语雀 / 飞书 |
| 系统架构图、用例图、功能模块图、部署图、E-R 概念模型 | 手绘 SVG | Mermaid 画不出火柴人、3D 节点、Chen 记号，观感也偏松 |

判断依据：**Mermaid 是否能画出该图的规范符号**。画不出的走手绘，不要用近似图形凑。

手绘不要从空白画布开始。`assets/templates/` 下有五个改好坐标的模板，
改文案即可，增删节点时按 `references/svg-cookbook.md` 的间距常量重算。

## 三、出图

### Mermaid

```bash
# 单个文件
python3 scripts/render_mermaid.py 图.mmd --preset paper

# 批量：渲染目录下所有 .mmd，同时生成 index.html 总览页
python3 scripts/render_mermaid.py diagrams/ --preset paper --gallery
```

产出：`diagrams/out/` 下的 `*.svg`、`*.png`（2 倍图），加 `--gallery` 再出一张
带源码折叠的 HTML 总览页，讲课时可以直接翻。

参数：

| 参数 | 取值 | 说明 |
| --- | --- | --- |
| `--preset` | `paper` / `slide` / `tech` | 报告论文 / 课件演示 / 技术文档 |
| `--format` | `svg` / `png` / `both` | 默认 both |
| `--scale` | 数字，默认 2 | PNG 缩放倍数，投屏用 3 |
| `--transparent` | 开关 | 插深色 PPT 时用 |
| `--outdir` | 路径 | 默认源文件同级的 `out/` |
| `--gallery` | 开关 | 生成 `index.html` 总览页，源码可折叠 |
| `--include-svg` | 开关 | 总览页一并收录同目录的手绘 `.svg`，一套图放一个页面 |

渲染器用本机 Chrome 无头模式，`mermaid.min.js` 随 skill 打包，断网可用。
渲染失败会把 Mermaid 的原始报错带出来，照着 `references/mermaid-rules.md`
的排查表处理。

产出的 SVG 强制关掉了 `foreignObject`，是严格合法的 XML，可以直接插进
Word 2016+、PowerPoint 2019+、LaTeX，缩放不失真。

写 Mermaid 源码时照抄 `assets/templates/mmd-*.mmd` 的结构，
里面已经处理好节点形状、分支标签、注释、配色类。

### 手绘 SVG

```bash
# 重新生成五个模板（改完 build_templates.py 里的文案后执行）
python3 scripts/build_templates.py
```

模板文件：`svg-architecture.svg`、`svg-usecase.svg`、`svg-module.svg`、
`svg-deployment.svg`、`svg-er.svg`。改的时候只动文字和坐标常量，
颜色、线宽、字号从 `references/visual-spec.md` 取，不要现编。

画完必须转成 PNG 看一眼。手算的坐标一定有偏差，读代码看不出来。
重点看：文字有没有溢出方框、线有没有穿过文字、同类元素尺寸是否一致。

## 四、交付前自检

逐条对一遍，每条都能在图上指出来才算过：

1. 图型和所在章节对得上（「系统设计」章节里不该出现用例图）
2. 图上每个名词，正文里都有解释
3. 没有指向不明的箭头、没有悬空节点
4. 判断框的每条出边都写了条件
5. 参与者、初始状态、终态、开始结束节点这类「边界元素」都在
6. 文字没被线穿、没被框裁
7. 转灰度后层次仍能分辨
8. 缩到页宽后最小的字还看得清

七、八两条最容易漏。报告和论文经常黑白打印，靠颜色区分层次的图会糊成一片。

## 五、参考文件

| 文件 | 什么时候读 |
| --- | --- |
| `references/diagram-selection.md` | 动手前。选图型、查该图的必备要素和常见错法 |
| `references/mermaid-rules.md` | 写 Mermaid 时。语法要点、基数符号、报错排查 |
| `references/svg-cookbook.md` | 手绘 SVG 时。图元代码、坐标算法、缺陷修正 |
| `references/visual-spec.md` | 定配色字号时。三套预设的色板与使用场景 |

## 六、几条不能破的规矩

**不要用功能模块图代替用例图。** 这是学生作业里最高频的错误。用例图讲「谁能做什么」，
画在系统边界外；功能模块图讲「内部怎么拆」，画的是父子结构。两回事。

**概念 E-R 图不能出现字段类型。** 出现 `varchar(50)` 就说明画成了物理模型。
概念模型只有矩形、椭圆、菱形和基数；表结构属于物理模型，用 Mermaid 的 `erDiagram`。

**架构图不写 IP 和端口。** 那是部署图的内容。架构图讲逻辑分层，
出现 `192.168.1.10:8080` 就串了。

**一张图只讲一件事。** 用例超过 12 个、流程超过 15 个节点、时序参与者超过 7 个，
都该拆图。塞在一张图里，读者只能放大到 200% 一格格找。

**同一份文档的图风格统一。** 预设只用一个，色带顺序不变，字号阶梯不变。
混用不同预设的图，一眼就能看出是东拼西凑的。

**不编造技术细节。** 用户没说清楚中间件版本、端口号、表字段时，
用通用表述（「消息队列」「缓存」）或留占位符，不要臆造 `Kafka 3.6.1` 这种具体版本。
问一句比编一个更省事。
