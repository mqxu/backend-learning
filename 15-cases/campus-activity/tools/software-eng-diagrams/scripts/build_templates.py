#!/usr/bin/env python3
"""生成软件工程精绘图模板（SVG）。

Mermaid 画系统架构图、UML 用例图、部署图、功能模块图、概念 E-R 图时
符号不规范、排版也偏松。这五类图由本脚本生成矢量模板，
坐标全部按网格算好，改文案不必重排。

用法：
    python3 build_templates.py            # 输出到 ../assets/templates/
    python3 build_templates.py --out DIR

改图时优先只改 CONTENT 区的文字；必须增删节点时，
按脚本里的间距常量重算 x / y，不要凭感觉挪坐标。
"""

from __future__ import annotations

import argparse
from html import escape
from pathlib import Path

FONT = "'PingFang SC','Hiragino Sans GB','Microsoft YaHei','Noto Sans CJK SC',sans-serif"

# ── 视觉规范 ───────────────────────────────────────────────
INK = "#0F172A"      # 主文字
INK2 = "#475569"     # 次要文字
LINE = "#8FA0B5"     # 连线
CARD = "#D7373F"     # 基数标注

LAYERS = {
    "blue": ("#2563EB", "#EFF5FE", "#DBE7FB"),
    "amber": ("#D97706", "#FEF6E7", "#FBE8C8"),
    "teal": ("#0D9488", "#E9F7F5", "#CFEDE9"),
    "violet": ("#7C3AED", "#F3EEFD", "#E4D8FB"),
    "slate": ("#64748B", "#F4F6F9", "#E3E8EF"),
}


def t(x, y, s, size=15, fill=INK, anchor="middle", weight="400", extra=""):
    return (
        f'<text x="{round(x,1)}" y="{round(y,1)}" font-size="{size}" fill="{fill}" '
        f'text-anchor="{anchor}" font-weight="{weight}"{extra}>{escape(s)}</text>'
    )


def mid(y, size):
    """把 y 当作视觉中心，换算成 SVG 基线。"""
    return round(y + size * 0.36, 1)


def head(extra_defs=""):
    return f"""<defs>
  <marker id="ar" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7.5"
          markerHeight="7.5" orient="auto-start-reverse">
    <path d="M0,0 L10,5 L0,10 Z" fill="{LINE}"/>
  </marker>
  <marker id="ar-open" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="9"
          markerHeight="9" orient="auto-start-reverse">
    <path d="M1,1 L11,6 L1,11" fill="none" stroke="{LINE}" stroke-width="1.6"/>
  </marker>
  <marker id="ar-ink" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7.5"
          markerHeight="7.5" orient="auto-start-reverse">
    <path d="M0,0 L10,5 L0,10 Z" fill="{INK2}"/>
  </marker>
{extra_defs}</defs>"""


def doc(w, h, body, defs=""):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
        f'viewBox="0 0 {w} {h}" font-family="{FONT}">\n'
        f"{head(defs)}\n{body}\n</svg>\n"
    )


def rrect(x, y, w, h, r=10, fill="#FFFFFF", stroke="none", sw=1.5, extra=""):
    s = f' stroke="{stroke}" stroke-width="{sw}"' if stroke != "none" else ""
    return (
        f'<rect x="{round(x,1)}" y="{round(y,1)}" width="{round(w,1)}" '
        f'height="{round(h,1)}" rx="{r}" ry="{r}" fill="{fill}"{s}{extra}/>'
    )


# ══════════════════════════════════════════════════════════
# 1. 分层系统架构图
# ══════════════════════════════════════════════════════════
def architecture() -> str:
    W, H = 1240, 880
    LX, LW = 36, 1168
    TAGW = 96
    CX0, CX1 = LX + TAGW + 20, LX + LW - 20
    CW = CX1 - CX0
    AXIS = LX + LW / 2

    bands = [
        ("user", "用户层", 36, 96, "slate",
         [("学生", "选课 · 学习 · 提交作业"),
          ("教师", "建课 · 批改 · 成绩录入"),
          ("管理员", "用户 · 权限 · 统计")]),
        ("pres", "表现层", 168, 124, "blue",
         [("Web 管理端", "Vue 3 + Element Plus"),
          ("移动端 App", "uni-app"),
          ("微信小程序", "uni-app 编译")]),
        ("gate", "接入层", 328, 100, "amber",
         [("Nginx 反向代理", "静态资源 · 负载均衡 · HTTPS 终结"),
          ("API 网关", "鉴权 · 限流 · 路由转发")]),
        ("svc", "业务层", 464, 210, "teal",
         [("认证授权服务", "登录 · JWT 签发 · 权限校验"),
          ("课程服务", "课程维护 · 章节目录"),
          ("选课服务", "名额控制 · 冲突校验"),
          ("作业服务", "发布 · 提交 · 批改"),
          ("成绩服务", "统计 · 导出"),
          ("通知服务", "站内信 · 短信推送")]),
        ("data", "数据层", 710, 132, "violet",
         [("MySQL", "业务主库 · 主从复制"),
          ("Redis", "会话 · 热点缓存"),
          ("MinIO", "课件 · 附件对象存储"),
          ("Elasticsearch", "课程与题库检索")]),
    ]

    body = []
    for b in bands:
        y, h = b[2], b[3]
        color, bg, edge = LAYERS[b[4]]
        body.append(rrect(LX, y, LW, h, 14, bg, edge, 1.4))
        body.append(f'<defs><clipPath id="c-{b[0]}">'
                    f'<rect x="{LX}" y="{y}" width="{LW}" height="{h}" rx="14" ry="14"/>'
                    f'</clipPath></defs>')
        body.append(f'<g clip-path="url(#c-{b[0]})">{rrect(LX, y, TAGW, h, 0, color)}</g>')
        body.append(t(LX + TAGW / 2, mid(y + h / 2, 17), b[1], 17, "#FFFFFF", weight="600"))

        items = b[5]
        rows = [items[:3], items[3:]] if b[0] == "svc" else [items]
        ys = [y + 28, y + 112] if b[0] == "svc" else [y + (h - 76) / 2]

        for ri, row in enumerate(rows):
            cnt = len(row)
            if b[0] == "user":
                bw, gap = 300, (CW - 3 * 300) / 2
            elif cnt == 2:
                bw, gap = 498, CW - 2 * 498
            elif cnt == 3:
                bw, gap = (CW - 2 * 36) / 3, 36
            elif cnt == 4:
                bw, gap = (CW - 3 * 20) / 4, 20
            else:
                bw, gap = (CW - (cnt - 1) * 36) / cnt, 36
            bh = 64 if b[0] == "svc" else 76
            total = cnt * bw + (cnt - 1) * gap
            x0 = CX0 + (CW - total) / 2
            for i, (title, sub) in enumerate(row):
                x = x0 + i * (bw + gap)
                yy = ys[ri] if b[0] == "svc" else ys[0]
                radius = 38 if b[0] == "user" else 8
                body.append(rrect(x, yy, bw, bh, radius, "#FFFFFF", edge, 1.6))
                body.append(t(x + bw / 2, mid(yy + bh / 2 - 10, 15.5), title, 15.5, INK, weight="600"))
                body.append(t(x + bw / 2, mid(yy + bh / 2 + 13, 12), sub, 12, INK2))

    for i, b in enumerate(bands[:-1]):
        y1, y2 = b[2] + b[3], bands[i + 1][2]
        body.append(
            f'<path d="M{AXIS},{y1 + 5} L{AXIS},{y2 - 6}" stroke="{LINE}" '
            f'stroke-width="1.7" fill="none" marker-end="url(#ar)"/>'
        )

    return doc(W, H, "\n".join(body))


# ══════════════════════════════════════════════════════════
# 2. UML 用例图
# ══════════════════════════════════════════════════════════
def actor(cx, cy, label, color="#2563EB"):
    """火柴人：cy 为头顶圆心，全身到 cy+96，名字在 cy+128。"""
    s = [
        f'<circle cx="{cx}" cy="{cy}" r="15" fill="#FFFFFF" stroke="{color}" stroke-width="1.9"/>',
        f'<path d="M{cx},{cy + 15} L{cx},{cy + 56}" stroke="{color}" stroke-width="1.9"/>',
        f'<path d="M{cx - 25},{cy + 28} L{cx + 25},{cy + 28}" stroke="{color}" stroke-width="1.9"/>',
        f'<path d="M{cx},{cy + 56} L{cx - 21},{cy + 96}" stroke="{color}" stroke-width="1.9"/>',
        f'<path d="M{cx},{cy + 56} L{cx + 21},{cy + 96}" stroke="{color}" stroke-width="1.9"/>',
        t(cx, cy + 128, label, 16, INK, weight="600"),
    ]
    return "\n".join(s)


def usecase(cx, cy, label, rx=82, ry=30, color="#2563EB", fill="#EFF5FE"):
    return (
        f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{fill}" '
        f'stroke="{color}" stroke-width="1.6"/>\n' + t(cx, mid(cy, 14.5), label, 14.5, INK)
    )


def usecase_diagram() -> str:
    W, H = 1200, 900
    BX, BY, BW, BH = 300, 64, 600, 756
    L, R = 440, 760
    RX, RY = 82, 30

    stu = [200, 292, 384, 476]
    tea = [580, 668, 756]
    adm = [292, 384, 476]

    body = [rrect(BX, BY, BW, BH, 6, "#FCFDFF", "#94A3B8", 1.6),
            t(BX + BW / 2, BY + 40, "在线教学平台", 17, INK2, weight="600")]

    body.append(actor(115, 330, "学生"))
    body.append(actor(115, 670, "教师"))
    body.append(actor(1085, 380, "管理员"))

    sx, sy = 141, 358
    tx, ty = 141, 698
    ax, ay = 1059, 408

    for y, n in zip(stu, ["浏览课程", "选课报名", "提交作业", "查看成绩"]):
        body.append(usecase(L, y, n))
        body.append(f'<path d="M{sx},{sy} L{L - RX},{y}" stroke="{LINE}" stroke-width="1.4" fill="none"/>')
    for y, n in zip(tea, ["发布课程", "批改作业", "录入成绩"]):
        body.append(usecase(L, y, n))
        body.append(f'<path d="M{tx},{ty} L{L - RX},{y}" stroke="{LINE}" stroke-width="1.4" fill="none"/>')
    for y, n in zip(adm, ["用户管理", "角色授权", "数据统计"]):
        body.append(usecase(R, y, n))
        body.append(f'<path d="M{ax},{ay} L{R + RX},{y}" stroke="{LINE}" stroke-width="1.4" fill="none"/>')

    body.append(usecase(R, 200, "校验选课冲突", RX, RY, "#D97706", "#FEF6E7"))
    body.append(
        f'<path d="M{L + 52},{292 - RY + 8} L{R - RX - 8},{200 + RY - 4}" '
        f'stroke="{LINE}" stroke-width="1.4" fill="none" stroke-dasharray="7 5" '
        f'marker-end="url(#ar-open)"/>'
    )
    body.append(t(608, 238, "«include»", 12.5, INK2))

    body.append(t(BX, BY + BH + 40,
                  "图例：实线＝参与者与用例的关联；虚线箭头＝「包含」关系（被包含的用例必然执行）",
                  12.5, INK2, anchor="start"))
    return doc(W, H, "\n".join(body))


# ══════════════════════════════════════════════════════════
# 3. 功能模块图
# ══════════════════════════════════════════════════════════
def module_diagram() -> str:
    W, H = 1240, 500
    root_cx = 620
    root_y, rw, rh = 44, 244, 56

    cols = [
        ("课程管理", ["课程维护", "章节目录", "课件上传"]),
        ("选课管理", ["开课计划", "选课报名", "名额控制"]),
        ("作业管理", ["作业发布", "在线提交", "批改评分"]),
        ("成绩管理", ["成绩录入", "成绩统计", "成绩导出"]),
        ("系统管理", ["用户管理", "角色授权", "操作日志"]),
    ]
    lvl1_y, lw, lh = 170, 186, 54
    X0, TW = 60, 1120
    gap = (TW - 5 * lw) / 4
    centers = [X0 + i * (lw + gap) + lw / 2 for i in range(5)]

    sub_w, sub_h, sub_gap = 148, 44, 18
    sub_top = 280
    trunk_dx = 108

    body = [rrect(root_cx - rw / 2, root_y, rw, rh, 10, "#1E3A5F", "none"),
            t(root_cx, mid(root_y + rh / 2, 17), "在线教学平台", 17, "#FFFFFF", weight="600")]

    bus_y = root_y + rh + 32
    body.append(f'<path d="M{root_cx},{root_y + rh} L{root_cx},{bus_y}" stroke="{LINE}" stroke-width="1.6"/>')
    body.append(f'<path d="M{centers[0]},{bus_y} L{centers[-1]},{bus_y}" stroke="{LINE}" stroke-width="1.6"/>')

    for cx, (name, subs) in zip(centers, cols):
        body.append(f'<path d="M{cx},{bus_y} L{cx},{lvl1_y}" stroke="{LINE}" stroke-width="1.6"/>')
        body.append(rrect(cx - lw / 2, lvl1_y, lw, lh, 8, "#EFF5FE", "#2563EB", 1.6))
        body.append(t(cx, mid(lvl1_y + lh / 2, 15.5), name, 15.5, INK, weight="600"))

        tk = cx - trunk_dx
        last_cy = sub_top + (len(subs) - 1) * (sub_h + sub_gap) + sub_h / 2
        body.append(
            f'<path d="M{cx},{lvl1_y + lh} L{cx},{lvl1_y + lh + 16} '
            f'L{tk},{lvl1_y + lh + 16} L{tk},{last_cy}" '
            f'stroke="{LINE}" stroke-width="1.4" fill="none"/>'
        )
        for i, s in enumerate(subs):
            cy = sub_top + i * (sub_h + sub_gap) + sub_h / 2
            body.append(f'<path d="M{tk},{cy} L{cx - sub_w / 2},{cy}" '
                        f'stroke="{LINE}" stroke-width="1.4"/>')
            body.append(rrect(cx - sub_w / 2, cy - sub_h / 2, sub_w, sub_h, 6,
                              "#FFFFFF", "#B6C2D2", 1.3))
            body.append(t(cx, mid(cy, 13.5), s, 13.5, INK2))

    return doc(W, H, "\n".join(body))


# ══════════════════════════════════════════════════════════
# 4. UML 部署图
# ══════════════════════════════════════════════════════════
def node3d(x, y, w, h, label, sub="", color="#2563EB", fill="#EFF5FE", d=18):
    """UML 节点：3D 立方体。x,y 为前面左上角，d 为进深。"""
    s = [
        f'<path d="M{x},{y} L{x + d},{y - d} L{x + w + d},{y - d} L{x + w},{y} Z" '
        f'fill="{fill}" stroke="{color}" stroke-width="1.4"/>',
        f'<path d="M{x + w},{y} L{x + w + d},{y - d} L{x + w + d},{y + h - d} L{x + w},{y + h} Z" '
        f'fill="{fill}" stroke="{color}" stroke-width="1.4"/>',
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#FFFFFF" '
        f'stroke="{color}" stroke-width="1.7"/>',
        t(x + w / 2, y + 30, label, 15, INK, weight="600"),
    ]
    for i, ln in enumerate(sub.split("\n")):
        if ln:
            s.append(t(x + w / 2, y + 53 + i * 19, ln, 12.3, INK2))
    return "\n".join(s)


def deployment_diagram() -> str:
    W, H = 1240, 710
    body = [rrect(20, 20, 1200, 624, 4, "none", "#CBD5E1", 1.4),
            t(44, 52, "«deployment» 在线教学平台部署视图", 15, INK2, anchor="start", weight="600")]

    body.append(node3d(88, 110, 230, 92, "«device» 客户端", "浏览器 / 手机 / 小程序", "#64748B", "#F4F6F9"))
    body.append(node3d(420, 110, 230, 92, "«node» Nginx", "静态资源 · HTTPS 终结", "#D97706", "#FEF6E7"))

    body.append(rrect(390, 258, 480, 244, 10, "#F8FAFC", "#CBD5E1", 1.4))
    body.append(t(410, 286, "«execution environment» Tomcat 集群", 12.5, INK2, anchor="start"))
    body.append(node3d(408, 306, 404, 66, "app-01", "Spring Boot 3.3 · JDK 21", "#0D9488", "#E9F7F5"))
    body.append(node3d(408, 402, 404, 66, "app-02", "Spring Boot 3.3 · JDK 21", "#0D9488", "#E9F7F5"))

    body.append(node3d(940, 280, 222, 88, "«node» MySQL 8", "主库 + 只读从库", "#7C3AED", "#F3EEFD"))
    body.append(node3d(940, 410, 222, 80, "«node» Redis 7", "会话 / 热点缓存", "#7C3AED", "#F3EEFD"))
    body.append(node3d(940, 530, 222, 80, "«node» MinIO", "课件与附件对象存储", "#7C3AED", "#F3EEFD"))

    def link(points, label="", lp=None):
        d = "M" + " L".join(f"{a},{b}" for a, b in points)
        out = [f'<path d="{d}" stroke="{LINE}" stroke-width="1.5" fill="none"/>']
        if label:
            out.append(t(lp[0], lp[1], label, 12, INK2))
        return "\n".join(out)

    body.append(link([(318, 156), (420, 156)], "HTTPS 443", (369, 148)))
    body.append(link([(535, 202), (535, 258)], "反向代理", (556, 234)))
    body.append(link([(812, 340), (940, 326)], "JDBC 3306", (876, 320)))
    body.append(link([(812, 436), (940, 450)], "RESP 6379", (876, 432)))
    body.append(link([(812, 452), (880, 570), (940, 570)], "S3 API", (900, 596)))

    body.append(t(44, 672, "说明：app-01 / app-02 前置负载均衡，会话托管 Redis，可水平扩容。",
                  12.5, INK2, anchor="start"))
    return doc(W, H, "\n".join(body))


# ══════════════════════════════════════════════════════════
# 5. E-R 图（Chen 记号，概念模型）
# ══════════════════════════════════════════════════════════
def er_diagram() -> str:
    W, H = 1240, 680
    AXIS = 400
    EW, EH = 140, 62
    RW, RH = 150, 96

    TX, JX, KX, XX, SX = 160, 380, 620, 860, 1090

    body = []

    def entity(cx, cy, label, w=EW, h=EH):
        return (rrect(cx - w / 2, cy - h / 2, w, h, 4, "#EFF5FE", "#2563EB", 1.7)
                + "\n" + t(cx, mid(cy, 16), label, 16, INK, weight="600"))

    def rel(cx, cy, label, w=RW, h=RH):
        pts = f"{cx},{cy - h / 2} {cx + w / 2},{cy} {cx},{cy + h / 2} {cx - w / 2},{cy}"
        return (f'<polygon points="{pts}" fill="#FEF6E7" stroke="#D97706" stroke-width="1.6"/>\n'
                + t(cx, mid(cy, 15), label, 15, INK, weight="600"))

    def attr(cx, cy, label, pk=False, rx=58, ry=26):
        out = [f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="#FFFFFF" '
               f'stroke="#64748B" stroke-width="1.4"/>', t(cx, mid(cy, 13.5), label, 13.5, INK)]
        if pk:
            half = len(label) * 13.5 / 2 + 2
            base = mid(cy, 13.5) + 7
            out.append(f'<path d="M{cx - half},{base} L{cx + half},{base}" '
                       f'stroke="{INK}" stroke-width="1.1"/>')
        return "\n".join(out)

    def el(x1, y1, x2, y2):
        return f'<path d="M{x1},{y1} L{x2},{y2}" stroke="{LINE}" stroke-width="1.4" fill="none"/>'

    def card(x, y, s):
        return t(x, y, s, 14, CARD, weight="700")

    body.append(attr(TX, 250, "工号", pk=True))
    body.append(attr(TX, 572, "姓名"))
    body.append(el(TX, 276, TX, AXIS - EH / 2))

    body.append(attr(556, 256, "课程号", pk=True))
    body.append(attr(690, 256, "课程名"))
    body.append(attr(KX, 566, "学分"))
    body.append(el(566, 281, 584, AXIS - EH / 2))
    body.append(el(680, 281, 656, AXIS - EH / 2))
    body.append(el(KX, 540, KX, AXIS + EH / 2))

    body.append(attr(XX, 566, "成绩"))
    body.append(el(XX, 540, XX, AXIS + RH / 2))

    body.append(attr(1022, 256, "学号", pk=True))
    body.append(attr(1160, 256, "专业"))
    body.append(attr(SX, 572, "姓名"))
    body.append(el(1032, 281, 1056, AXIS - EH / 2))
    body.append(el(1150, 281, 1124, AXIS - EH / 2))
    body.append(el(SX, 546, SX, AXIS + EH / 2))

    body.append(el(TX + EW / 2, AXIS, JX - RW / 2, AXIS))
    body.append(el(JX + RW / 2, AXIS, KX - EW / 2, AXIS))
    body.append(el(KX + EW / 2, AXIS, XX - RW / 2, AXIS))
    body.append(el(XX + RW / 2, AXIS, SX - EW / 2, AXIS))

    body.append(entity(TX, AXIS, "教师"))
    body.append(rel(JX, AXIS, "讲授"))
    body.append(entity(KX, AXIS, "课程"))
    body.append(rel(XX, AXIS, "选课"))
    body.append(entity(SX, AXIS, "学生"))

    body.append(card(TX + 106, AXIS - 12, "1"))
    body.append(card(KX - 106, AXIS - 12, "n"))
    body.append(card(KX + 106, AXIS - 12, "n"))
    body.append(card(SX - 106, AXIS - 12, "m"))

    body.append(t(36, H - 30,
                  "Chen 记号：矩形＝实体，椭圆＝属性（下划线为主键），菱形＝联系；边上字母为基数，"
                  "如「教师 1 — 讲授 — n 课程」表示一位教师可讲授多门课程",
                  12.5, INK2, anchor="start"))
    return doc(W, H, "\n".join(body))


# ══════════════════════════════════════════════════════════
def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(Path(__file__).resolve().parent.parent / "assets" / "templates"))
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    items = {
        "svg-architecture.svg": architecture(),
        "svg-usecase.svg": usecase_diagram(),
        "svg-module.svg": module_diagram(),
        "svg-deployment.svg": deployment_diagram(),
        "svg-er.svg": er_diagram(),
    }
    for name, content in items.items():
        (out / name).write_text(content, encoding="utf-8")
        print(f"✓ {name}  {len(content)} bytes")


if __name__ == "__main__":
    main()
