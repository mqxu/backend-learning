# 自绘 SVG 的图元与坐标规范

Mermaid 画不了 UML 用例图的火柴人、部署图的 3D 节点、概念 E-R 图的 Chen 记号。
这五类图用 SVG 直接画，观感和符号都能到位。代价是坐标要自己算，
所以下面这套网格和尺寸常量要照抄，不要每次凭感觉定。

## 一、画布与网格

| 图的类型 | 画布尺寸 | 说明 |
| --- | --- | --- |
| 系统架构图 | 1240 × 880 | 五层带，层高 96～210 |
| 用例图 | 1200 × 900 | 系统边界 600 × 756 |
| 功能模块图 | 1240 × 500 | 三级树 |
| 部署图 | 1240 × 710 | 节点横向三列 |
| E-R 图（概念） | 1240 × 680 | 主轴 + 上下属性 |

统一约定：

- 页边距 36px，内容区从 36 开始
- 主色文字 `#0F172A`，次要文字 `#475569`，连线 `#8FA0B5`
- 圆角：大容器 14，功能框 8，小标签 6
- 描边：外框 1.4，模块框 1.6，连线 1.4～1.6
- 字号阶梯：标题 17，模块名 15.5，正文 14，注释 12～12.5

字号不要出现 13、14.5、15.2 这种混排。同一层级的字号必须完全一致。

## 二、文字垂直居中

SVG 的 `<text y>` 是基线位置，不是中心。要把文字摆在 `cy` 位置，
`y` 应该写成：

```python
def mid(y, size):
    """把 y 当作视觉中心，换算成 SVG 基线。"""
    return round(y + size * 0.36, 1)
```

这个 0.36 是按中文字体的字面高度估的。用 `dominant-baseline="central"`
不靠谱——Word、PPT、部分 PDF 导出器不认这个属性，导出来文字会偏上。

水平居中用 `text-anchor="middle"`，这个属性兼容性没问题。

## 三、常用图元

### 火柴人（UML 参与者）

约定：`cy` 是头顶圆心，全身到 `cy+96`，名字在 `cy+128`。

```python
def actor(cx, cy, label, color="#2563EB"):
    return "\n".join([
        f'<circle cx="{cx}" cy="{cy}" r="15" fill="#FFFFFF" stroke="{color}" stroke-width="1.9"/>',
        f'<path d="M{cx},{cy+15} L{cx},{cy+56}" stroke="{color}" stroke-width="1.9"/>',
        f'<path d="M{cx-25},{cy+28} L{cx+25},{cy+28}" stroke="{color}" stroke-width="1.9"/>',
        f'<path d="M{cx},{cy+56} L{cx-21},{cy+96}" stroke="{color}" stroke-width="1.9"/>',
        f'<path d="M{cx},{cy+56} L{cx+21},{cy+96}" stroke="{color}" stroke-width="1.9"/>',
    ])
```

手臂画在 `cy+28` 而不是 `cy+40`，是为了让手臂和身体的交点看起来在胸腔位置。
画在正中会像个十字架。

### 3D 节点（UML 部署节点）

`x, y` 是前面的左上角，`d` 是进深（默认 18）。顶面朝右上，右侧面朝右。

```python
def node3d(x, y, w, h, label, color, fill, d=18):
    return "\n".join([
        # 顶面
        f'<path d="M{x},{y} L{x+d},{y-d} L{x+w+d},{y-d} L{x+w},{y} Z" fill="{fill}" stroke="{color}" stroke-width="1.4"/>',
        # 右侧面
        f'<path d="M{x+w},{y} L{x+w+d},{y-d} L{x+w+d},{y+h-d} L{x+w},{y+h} Z" fill="{fill}" stroke="{color}" stroke-width="1.4"/>',
        # 前面
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#FFFFFF" stroke="{color}" stroke-width="1.7"/>',
    ])
```

节点的实际占位是 `w+d` 宽、`h+d` 高，算布局时要按这个算，否则右侧面会压到相邻节点。

### 菱形（联系 / 判断）

```python
pts = f"{cx},{cy-h/2} {cx+w/2},{cy} {cx},{cy+h/2} {cx-w/2},{cy}"
f'<polygon points="{pts}" fill="#FEF6E7" stroke="#D97706" stroke-width="1.6"/>'
```

菱形的高宽比取 0.6～0.65 最像教材里的样子，不要画成正方形。

### 分层色带（架构图）

左侧 96px 深色条写层名，右侧浅色底放组件。深色条要用 `clipPath` 裁到
圆角容器内，否则直角会戳出容器的圆角：

```python
f'<defs><clipPath id="c-{key}"><rect x="{LX}" y="{y}" width="{LW}" height="{h}" rx="14"/></clipPath></defs>'
f'<g clip-path="url(#c-{key})"><rect x="{LX}" y="{y}" width="96" height="{h}" fill="{color}"/></g>'
```

### 箭头

定义在 `<defs>` 里，用 `marker-end` 引用：

```xml
<marker id="ar" viewBox="0 0 10 10" refX="9" refY="5"
        markerWidth="7.5" markerHeight="7.5" orient="auto-start-reverse">
  <path d="M0,0 L10,5 L0,10 Z" fill="#8FA0B5"/>
</marker>
```

`refX` 落在箭尖处（9），线才会精确停在箭头根部。
`refX` 设成 10 时，箭头会缩进线头，看起来像缺了一截。
`orient="auto-start-reverse"` 让同一个 marker 能用于 `marker-start`。

空心三角（继承）用 `fill="#FFFFFF"`，描边同色即可。

## 四、连线规则

### 优先正交，不要斜线穿场

树形结构、分层结构用 `M` + `L` 画正交折线：

```python
d = f"M{x1},{y1} L{x1},{y2} L{x2},{y2}"
```

只有「实体到属性椭圆」「参与者到用例」这类放射状连接才用直线斜连。
一张图里混用两种连线风格不是问题，但同一层级内要统一。

### 连接点选在图形边缘，不是中心

连线端点要算到图形边界，不要连到中心点然后被图形盖住：

- 矩形：连到边的中点或角
- 椭圆：连到左右顶点 `(cx±rx, cy)`，斜连时按角度算 `cx + rx*cosθ`
- 菱形：连到四个顶点

### 标签避开线

连线标签的位置手工指定，不要让标签压在线或节点上。
经验值：水平线上方 8～10px，竖直线的右侧 14～18px。
斜线标签放在线的上方，且与线保持至少 12px 垂直距离。

判断方法：把渲染结果截成 PNG 看一眼，标签压线一眼就能看出来。

## 五、分层的坐标算法

架构图最容易写乱。按下面的顺序算，不要跳步：

```python
LX, LW = 36, 1168          # 层外框左边界与宽度
TAGW = 96                  # 左侧层名条宽度
CX0 = LX + TAGW + 20       # 组件区左边界 152
CX1 = LX + LW - 20         # 组件区右边界 1184
CW = CX1 - CX0             # 组件区可用宽度 1032

# 层位置：每层高度 + 固定 36px 间隙
bands = [("用户层", 36, 96), ("表现层", 168, 124), ("接入层", 328, 100),
         ("业务层", 464, 210), ("数据层", 710, 132)]

# 层内组件：给定数量，算等分宽度
def layout(n, gap, row_w=CW):
    bw = (row_w - gap * (n - 1)) / n
    total = n * bw + (n - 1) * gap
    x0 = CX0 + (row_w - total) / 2
    return bw, [x0 + i * (bw + gap) for i in range(n)]
```

`gap` 按数量取：2 个组件用 36，3 个用 36，4 个用 20，5 个以上用 36。
这样每行都刚好铺满 1032px，五层的左右边界对齐。

## 六、画完必须验证

手写的 SVG 一定会有坐标错误，靠肉眼读代码看不出来。流程：

```bash
# 1. 生成
python3 build_templates.py

# 2. 转 PNG 看一眼（本 skill 的渲染器可以直接转）
python3 render_mermaid.py --help   # SVG 转 PNG 用 assets 里的同款 Chrome 调用

# 3. 检查这几点
#    - 文字有没有溢出方框
#    - 线条有没有穿过文字或图形
#    - 同类元素尺寸是否一致
#    - 上下左右留白是否均衡
```

常见缺陷和改法：

| 现象 | 原因 | 改法 |
| --- | --- | --- |
| 文字压线 | 标签 y 值没留够间距 | 上移 10～14px，或改到线的另一侧 |
| 椭圆和矩形重叠 | 中心距小于两个半宽之和 | 拉开到 `rx1 + w2/2 + 16` 以上 |
| 3D 节点右侧面戳出容器 | 布局只算了 `w`，没算 `w+d` | 容器宽度按 `w+d+留白` 算 |
| 深层元素被圆角裁掉 | 深色条直角戳出容器 | 套 `clipPath` |
| 文字颜色发灰 | 用了 8 位 hex 当填充 | 换成不透明浅色 |
| 中文字体回退成宋体 | 没在 `<svg>` 上声明 `font-family` | 加 `font-family="'PingFang SC','Microsoft YaHei',sans-serif"` |

## 七、导出

SVG 直接插进 Word 2016 及以上、PowerPoint 2019 及以上都支持，且缩放不失真。
需要 PNG 时按 2 倍或 3 倍导出，不要导出 1 倍再放大。

透明背景用 `assets/templates` 里的图直接改 `background`；
交付给印刷或投稿时建议保留白底，避免深色主题下反色。
