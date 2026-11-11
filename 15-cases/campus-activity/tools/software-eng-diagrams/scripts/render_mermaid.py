#!/usr/bin/env python3
"""把 Mermaid 源码渲染成 SVG / PNG，不依赖网络。

用法：
    python3 render_mermaid.py 单图.mmd                      # 输出 SVG + PNG
    python3 render_mermaid.py 图目录/ -o out --preset paper  # 批量渲染
    python3 render_mermaid.py a.mmd b.mmd --format svg       # 只出矢量图
    python3 render_mermaid.py 图目录/ --gallery              # 附带 HTML 总览页

设计要点：
  * 用本机 Chrome 无头模式渲染，mermaid.min.js 随 skill 打包，断网可用。
  * 输出的 SVG 使用固定宽高 + 显式 font-family，可直接插入 Word / PPT / 论文。
  * 关闭 htmlLabels，避免生成 foreignObject（Word、PPT、LaTeX 都不认）。
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SKILL_ROOT = HERE.parent
MERMAID_JS = SKILL_ROOT / "assets" / "vendor" / "mermaid.min.js"

FONT_STACK = "'PingFang SC','Hiragino Sans GB','Microsoft YaHei','Source Han Sans SC','Noto Sans CJK SC',sans-serif"

CHROME_CANDIDATES = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
    str(Path.home() / ".agent-browser/browsers/chrome-153.0.8010.52/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"),
]

# 视觉预设。三套场景共用同一套语义色，只调字号与饱和度，
# 保证同一份文档里不同图之间观感统一。
PRESETS: dict[str, dict] = {
    # 每套配色给 8 组 (主色, 浅填充)。浅填充必须是不透明色，
    # 用带 alpha 的 8 位 hex 会让 Mermaid 把它当成文字色，出现「透明字」。
    "paper": {
        "fontSize": 14,
        "colors": [
            ("#1F3A5F", "#E9F0F8"), ("#2F6B4F", "#E8F2EC"),
            ("#8A5A1E", "#FAF0E1"), ("#6B3A5F", "#F4EAF1"),
            ("#3A5F7D", "#EAF1F6"), ("#5F7D3A", "#EFF4E6"),
            ("#7D3A3A", "#F7E9E9"), ("#4A4A6B", "#EDEDF4"),
        ],
        "line": "#33415C", "text": "#16202E", "muted": "#5A6779", "bg": "#FFFFFF",
    },
    "slide": {
        "fontSize": 20,
        "colors": [
            ("#12508F", "#E3EEF9"), ("#0F7B5F", "#E2F3EE"),
            ("#B45309", "#FCEEDD"), ("#7C2D5F", "#F6E7EF"),
            ("#2B6CA3", "#E6F0F8"), ("#5B8C2A", "#EEF5E3"),
            ("#A83232", "#F8E7E7"), ("#4C4C8A", "#ECECF7"),
        ],
        "line": "#1B2A3D", "text": "#0B1523", "muted": "#4A5666", "bg": "#FFFFFF",
    },
    "tech": {
        "fontSize": 15,
        "colors": [
            ("#2563EB", "#EAF1FE"), ("#0D9488", "#E4F5F3"),
            ("#D97706", "#FDF1DF"), ("#7C3AED", "#F1EAFE"),
            ("#0EA5E9", "#E4F4FD"), ("#65A30D", "#EFF6DF"),
            ("#DC2626", "#FCE9E9"), ("#475569", "#EEF1F5"),
        ],
        "line": "#475569", "text": "#0F172A", "muted": "#64748B", "bg": "#FFFFFF",
    },
}

DIAGRAM_LABEL = {
    "svg": "手绘矢量图",
    "flowchart": "流程图 / 功能结构",
    "graph": "流程图 / 功能结构",
    "sequencediagram": "时序图",
    "classdiagram": "类图",
    "statediagram": "状态图",
    "statediagram-v2": "状态图",
    "erdiagram": "E-R 图",
    "journey": "用户旅程图",
    "gantt": "甘特图",
    "pie": "饼图",
    "mindmap": "思维导图",
    "timeline": "时间线",
    "gitgraph": "版本分支图",
    "quadrantchart": "四象限图",
    "c4context": "C4 系统上下文",
    "block-beta": "块图",
    "architecture": "架构图",
    "packet": "报文图",
}


def build_config(preset: str, transparent: bool) -> dict:
    p = PRESETS[preset]
    c = p["colors"]
    main = lambda i: c[i % len(c)][0]      # noqa: E731
    light = lambda i: c[i % len(c)][1]     # noqa: E731
    ink = p["text"]

    cfg = {
        "startOnLoad": False,
        # 关掉 HTML 标签：foreignObject 在 Word / PPT / LaTeX 里都渲染不出来，
        # 顶层和 flowchart 各写一次，只写一处对部分图型不生效。
        "htmlLabels": False,
        "securityLevel": "loose",
        "theme": "base",
        "look": "classic",
        "fontFamily": FONT_STACK,
        "themeVariables": {
            "fontFamily": FONT_STACK,
            "fontSize": f"{p['fontSize']}px",
            "background": "transparent" if transparent else p["bg"],
            "primaryColor": light(0),
            "primaryTextColor": ink,
            "primaryBorderColor": main(0),
            "secondaryColor": light(1),
            "secondaryTextColor": ink,
            "secondaryBorderColor": main(1),
            "tertiaryColor": light(2),
            "tertiaryTextColor": ink,
            "tertiaryBorderColor": main(2),
            "lineColor": p["line"],
            "textColor": ink,
            "mainBkg": light(0),
            "nodeBorder": main(0),
            "nodeTextColor": ink,
            "clusterBkg": "#F7F9FC",
            "clusterBorder": p["muted"],
            "titleColor": ink,
            "edgeLabelBackground": p["bg"],
            # 时序图
            "actorBkg": light(0),
            "actorBorder": main(0),
            "actorTextColor": ink,
            "actorLineColor": p["muted"],
            "signalColor": p["line"],
            "signalTextColor": ink,
            "labelBoxBkgColor": light(0),
            "labelBoxBorderColor": main(0),
            "labelTextColor": ink,
            "loopTextColor": ink,
            "noteBkgColor": "#FFF8E1",
            "noteBorderColor": "#D6A419",
            "noteTextColor": "#4A3B00",
            "activationBkgColor": light(1),
            "activationBorderColor": main(1),
            "sequenceNumberColor": "#FFFFFF",
            # 状态图
            "labelColor": ink,
            "altBackground": "#F7F9FC",
            "compositeBackground": "#F7F9FC",
            "compositeTitleBackground": light(0),
            "compositeBorder": main(0),
            "stateBkg": light(0),
            "stateBorder": main(0),
            "transitionColor": p["line"],
            "transitionLabelColor": ink,
            "specialStateColor": p["line"],
            # 类图 / ER
            "classText": ink,
            "attributeBackgroundColorOdd": "#FFFFFF",
            "attributeBackgroundColorEven": "#F7F9FC",
            # 甘特图
            "sectionBkgColor": light(0),
            "sectionBkgColor2": light(7),
            "taskBkgColor": main(0),
            "taskBorderColor": main(0),
            "taskTextColor": "#FFFFFF",
            "taskTextOutsideColor": ink,
            "taskTextLightColor": "#FFFFFF",
            "taskTextDarkColor": ink,
            "activeTaskBkgColor": main(1),
            "activeTaskBorderColor": main(1),
            "doneTaskBkgColor": "#D7DEE8",
            "doneTaskBorderColor": "#A9B6C6",
            "critBkgColor": main(6),
            "critBorderColor": main(6),
            "gridColor": "#E2E8F0",
            "todayLineColor": main(6),
            # 其他
            "pieTitleTextColor": ink,
            "pieSectionTextColor": "#FFFFFF",
            "quadrant1Fill": light(0),
            "quadrant2Fill": light(1),
            "quadrant3Fill": light(2),
            "quadrant4Fill": light(3),
            "cScale0": main(0), "cScale1": main(1), "cScale2": main(2), "cScale3": main(3),
            "cScale4": main(4), "cScale5": main(5), "cScale6": main(6), "cScale7": main(7),
            "cScaleInv0": light(0), "cScaleInv1": light(1), "cScaleInv2": light(2),
            "cScaleInv3": light(3), "cScaleInv4": light(4), "cScaleInv5": light(5),
            "cScaleInv6": light(6), "cScaleInv7": light(7),
        },
        # Mermaid 的状态图有几处文字颜色没跟着 themeVariables 走，补一小段 CSS 兜底
        "themeCSS": (
            f".statediagram .edgeLabel,.statediagram .edgeLabel p,.statediagram .edgeLabel span"
            f"{{color:{ink} !important;}}"
            f".statediagram-state .nodeLabel,.statediagram-state .nodeLabel p,"
            f".statediagram-cluster .cluster-label,.statediagram-cluster .cluster-label span"
            f"{{color:{ink} !important;fill:{ink} !important;}}"
            f".statediagram-state rect.basic{{fill:{light(0)} !important;stroke:{main(0)} !important;}}"
            f".statediagram-state .divider{{stroke:{main(0)} !important;}}"
        ),
        "flowchart": {
            "htmlLabels": False,
            "useMaxWidth": False,
            "curve": "linear",
            "nodeSpacing": 44,
            "rankSpacing": 50,
            "padding": 12,
            "diagramPadding": 16,
        },
        "sequence": {
            "useMaxWidth": False,
            "diagramMarginX": 16,
            "diagramMarginY": 12,
            "actorMargin": 52,
            "boxMargin": 12,
            "messageMargin": 36,
            "noteMargin": 12,
            "mirrorActors": False,
            "wrap": True,
        },
        "state": {"useMaxWidth": False, "nodeSpacing": 46, "rankSpacing": 48, "htmlLabels": False},
        "class": {"useMaxWidth": False, "htmlLabels": False},
        "er": {"useMaxWidth": False, "entityPadding": 14, "minEntityWidth": 110},
        "gantt": {
            "useMaxWidth": False,
            "barHeight": 24,
            "barGap": 7,
            "topPadding": 46,
            "leftPadding": 178,
            "gridLineStartPadding": 42,
            "fontSize": p["fontSize"],
            "sectionFontSize": p["fontSize"],
        },
        "c4": {"useMaxWidth": False},
        "mindmap": {"useMaxWidth": False, "padding": 12},
        "journey": {"useMaxWidth": False},
    }
    return cfg


def find_chrome() -> str:
    env = os.environ.get("CHROME_PATH")
    if env and Path(env).exists():
        return env
    for c in CHROME_CANDIDATES:
        if Path(c).exists():
            return c
    found = shutil.which("google-chrome") or shutil.which("chromium")
    if found:
        return found
    sys.exit(
        "找不到 Chrome / Chromium。安装 Chrome 或设置环境变量 CHROME_PATH 指向可执行文件。"
    )


CHROME = None


def _chrome() -> str:
    global CHROME
    if CHROME is None:
        CHROME = find_chrome()
    return CHROME


HTML_SHELL = """<!DOCTYPE html><html><head><meta charset="utf-8">
<style>
html,body{{margin:0;padding:0;background:{bg};}}
#box{{display:inline-block;}}
</style></head><body>
<div id="box"></div>
<script src="file://{js}"></script>
<script type="text/plain" id="src">{code}</script>
<script>
const cfg = {cfg};
mermaid.initialize(cfg);
const code = document.getElementById('src').textContent;
mermaid.render('mmd', code).then(r => {{
  document.getElementById('box').innerHTML = r.svg;
  document.title = '__RENDER_OK__';
}}).catch(e => {{
  document.getElementById('box').textContent = '__RENDER_ERR__' + (e && e.message ? e.message : String(e));
  document.title = '__RENDER_ERR__';
}});
</script></body></html>"""


def run_chrome(args: list[str], timeout: int = 90) -> subprocess.CompletedProcess:
    return subprocess.run(
        [
            _chrome(),
            "--headless=new",
            "--disable-gpu",
            "--no-sandbox",
            "--hide-scrollbars",
            "--disable-extensions",
            "--disable-background-networking",
            "--run-all-compositor-stages-before-draw",
            "--no-first-run",
            "--no-default-browser-check",
            "--user-data-dir=" + tempfile.mkdtemp(prefix="mmd-profile-"),
            *args,
        ],
        capture_output=True,
        text=True,
        timeout=timeout,
    )


def dump_dom(page: Path, timeout: int = 90) -> str:
    r = run_chrome(
        ["--virtual-time-budget=20000", "--dump-dom", page.as_uri()], timeout=timeout
    )
    return r.stdout


def _num(v: float) -> str:
    return str(int(round(v))) if abs(v - round(v)) < 0.01 else f"{v:.2f}"


def parse_viewbox(svg: str) -> tuple[float, float, float, float]:
    m = re.search(r'viewBox="([-\d.\s,eE]+)"', svg)
    if not m:
        raise ValueError("SVG 缺少 viewBox")
    parts = re.split(r"[,\s]+", m.group(1).strip())
    return tuple(float(x) for x in parts[:4])  # type: ignore[return-value]


def normalize_svg(svg: str, scale_pad: float = 0.0) -> tuple[str, int, int]:
    """写死宽高、补上字体声明，让 SVG 脱离容器也能正常显示。"""
    x, y, w, h = parse_viewbox(svg)
    w += scale_pad * 2
    h += scale_pad * 2
    x -= scale_pad
    y -= scale_pad
    wi, hi = max(1, int(round(w))), max(1, int(round(h)))

    svg = re.sub(r'\swidth="[^"]*"', "", svg, count=1)
    svg = re.sub(r'\sheight="[^"]*"', "", svg, count=1)
    svg = re.sub(r'\sstyle="[^"]*max-width[^"]*"', "", svg)
    svg = re.sub(
        r'viewBox="[^"]*"',
        f'viewBox="{_num(x)} {_num(y)} {_num(w)} {_num(h)}"',
        svg,
        count=1,
    )
    svg = svg.replace(
        "<svg",
        f'<svg width="{wi}" height="{hi}" preserveAspectRatio="xMidYMid meet"',
        1,
    )
    # 统一字体，避免 Word / PPT 里字体回退成宋体或 Times
    if "font-family" not in svg.split(">", 1)[0]:
        svg = svg.replace("<svg", f'<svg font-family="{FONT_STACK}"', 1)
    # Mermaid 偶尔产出 HTML 风格的 <br>，SVG 作为图片加载时要求严格 XML
    svg = re.sub(r"<br\s*(?<!/)>", "<br/>", svg)
    svg = re.sub(r"<hr\s*(?<!/)>", "<hr/>", svg)
    return svg, wi, hi


def render_svg_source(source: str, preset: str, transparent: bool) -> tuple[str, int, int]:
    if not MERMAID_JS.exists():
        sys.exit(f"缺少 mermaid.min.js：{MERMAID_JS}")
    cfg = build_config(preset, transparent)
    cfg["themeVariables"]["background"] = "transparent"
    page = HTML_SHELL.format(
        bg="transparent" if transparent else PRESETS[preset]["bg"],
        js=str(MERMAID_JS),
        code=source,
        cfg=json.dumps(cfg, ensure_ascii=False),
    )
    tmp = Path(tempfile.mkdtemp(prefix="mmd-run-"))
    try:
        p = tmp / "draw.html"
        p.write_text(page, encoding="utf-8")
        dom = dump_dom(p)
        m = re.search(r"<svg\b.*?</svg>", dom, re.S)
        if m:
            return normalize_svg(m.group(0))
        err = re.search(r'<div id="box"[^>]*>(.*?)</div>', dom, re.S)
        if err and err.group(1).strip():
            msg = html.unescape(re.sub(r"<[^>]+>", "", err.group(1))).strip()
            raise RuntimeError(f"Mermaid 渲染失败：{msg[:600]}")
        raise RuntimeError("未拿到 SVG：语法错误或渲染超时")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def svg_to_png(svg: str, w: int, h: int, out: Path, scale: float) -> None:
    tmp = Path(tempfile.mkdtemp(prefix="mmd-png-"))
    try:
        p = tmp / "one.html"
        p.write_text(
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
            "<style>html,body{margin:0;padding:0;background:transparent}"
            "svg{display:block}</style></head><body>"
            + svg
            + "</body></html>",
            encoding="utf-8",
        )
        r = run_chrome(
            [
                f"--force-device-scale-factor={scale}",
                f"--window-size={w},{h}",
                f"--screenshot={out}",
                p.as_uri(),
            ]
        )
        if not out.exists() or out.stat().st_size == 0:
            raise RuntimeError("PNG 截图失败：" + (r.stderr or "")[:300])
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def svg_size(svg: str) -> tuple[int, int]:
    """读 SVG 的显示尺寸，缺 width/height 时退回 viewBox。"""
    head = svg[:600]
    mw = re.search(r'\bwidth="([\d.]+)(?:px)?"', head)
    mh = re.search(r'\bheight="([\d.]+)(?:px)?"', head)
    if mw and mh:
        return max(1, round(float(mw.group(1)))), max(1, round(float(mh.group(1))))
    try:
        _, _, w, h = parse_viewbox(svg)
        return max(1, round(w)), max(1, round(h))
    except ValueError:
        return 1200, 800


def detect_type(source: str) -> str:
    for line in source.splitlines():
        s = line.strip()
        if not s or s.startswith("%%"):
            continue
        key = s.split()[0].lower().rstrip(":")
        if key == "graph":
            return "flowchart"
        return key
    return "unknown"


def strip_frontmatter(source: str) -> str:
    """Mermaid 允许 --- 包裹的配置块，渲染时原样保留即可，这里只做规整。"""
    return source.strip("\n")


def process_file(
    src: Path,
    outdir: Path,
    preset: str,
    fmt: str,
    scale: float,
    transparent: bool,
) -> dict:
    source = strip_frontmatter(src.read_text(encoding="utf-8"))
    stem = src.stem
    rec: dict = {"name": stem, "source": src, "type": detect_type(source), "ok": False}
    try:
        svg, w, h = render_svg_source(source, preset, transparent)
        outdir.mkdir(parents=True, exist_ok=True)
        if fmt in ("svg", "both"):
            (outdir / f"{stem}.svg").write_text(svg, encoding="utf-8")
            rec["svg"] = outdir / f"{stem}.svg"
        if fmt in ("png", "both"):
            svg_to_png(svg, w, h, outdir / f"{stem}.png", scale)
            rec["png"] = outdir / f"{stem}.png"
        rec.update(ok=True, w=w, h=h)
    except Exception as e:  # noqa: BLE001
        rec["error"] = str(e)
    return rec


def write_gallery(records: list[dict], outdir: Path, preset: str) -> Path:
    cards = []
    for r in records:
        label = DIAGRAM_LABEL.get(r["type"], r["type"])
        if r.get("ok"):
            body = (
                f'<div class="canvas"><img src="{r["name"]}.svg" alt="{r["name"]}" '
                f'style="width:{min(r["w"], 980)}px"></div>'
            )
            meta = f'{r["w"]} × {r["h"]} px · {label}'
        else:
            body = f'<div class="canvas err">渲染失败<br><code>{html.escape(r.get("error",""))}</code></div>'
            meta = label
        src_txt = html.escape(r["source"].read_text(encoding="utf-8"))
        cards.append(
            f"""<section class="card">
  <header><h2>{html.escape(r["name"])}</h2><span class="tag">{meta}</span></header>
  {body}
  <details><summary>查看 Mermaid 源码</summary><pre><code>{src_txt}</code></pre></details>
</section>"""
        )
    doc = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>图集总览</title>
<style>
*{{box-sizing:border-box}}
body{{margin:0;padding:36px 28px 64px;background:#F6F8FB;color:#0F172A;
  font-family:{FONT_STACK};line-height:1.65}}
h1{{font-size:24px;margin:0 0 6px;letter-spacing:.01em}}
.sub{{color:#64748B;font-size:13px;margin-bottom:28px}}
.wrap{{max-width:1180px;margin:0 auto}}
.card{{background:#fff;border:1px solid #E2E8F0;border-radius:14px;
  padding:22px 24px 16px;margin-bottom:22px;box-shadow:0 1px 2px rgba(15,23,42,.04)}}
.card header{{display:flex;align-items:baseline;gap:12px;flex-wrap:wrap;
  margin-bottom:16px;padding-bottom:12px;border-bottom:1px solid #EEF2F7}}
h2{{font-size:16px;margin:0;font-weight:600}}
.tag{{font-size:12px;color:#64748B;background:#F1F5F9;border-radius:999px;padding:2px 10px}}
.canvas{{overflow-x:auto;text-align:center;padding:8px 0 12px}}
.canvas img{{max-width:100%;height:auto}}
.canvas.err{{color:#B42318;font-size:13px;text-align:left}}
details{{border-top:1px dashed #E2E8F0;padding-top:10px}}
summary{{cursor:pointer;font-size:13px;color:#2563EB;user-select:none}}
pre{{background:#0F172A;color:#E2E8F0;padding:14px 16px;border-radius:10px;
  overflow-x:auto;font-size:12.5px;line-height:1.6;margin:12px 0 4px}}
code{{font-family:'SF Mono',Menlo,Consolas,monospace}}
</style></head><body><div class="wrap">
<h1>图集总览</h1>
<div class="sub">共 {len(records)} 张图 · 预设 {preset} · 点击展开源码</div>
{''.join(cards)}
</div></body></html>"""
    out = outdir / "index.html"
    out.write_text(doc, encoding="utf-8")
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description="Mermaid 离线渲染器")
    ap.add_argument("inputs", nargs="+", help=".mmd 文件或包含 .mmd 的目录")
    ap.add_argument("-o", "--outdir", help="输出目录，默认与源文件同级 out/")
    ap.add_argument(
        "--preset", default="paper", choices=sorted(PRESETS), help="视觉预设"
    )
    ap.add_argument("--format", default="both", choices=["svg", "png", "both"])
    ap.add_argument("--scale", type=float, default=2.0, help="PNG 缩放倍数")
    ap.add_argument("--transparent", action="store_true", help="透明背景")
    ap.add_argument("--gallery", action="store_true", help="生成 index.html 总览页")
    ap.add_argument(
        "--include-svg",
        action="store_true",
        help="总览页一并收录同目录下的手绘 .svg",
    )
    args = ap.parse_args()

    files: list[Path] = []
    svg_files: list[Path] = []
    for raw in args.inputs:
        p = Path(raw).expanduser()
        if p.is_dir():
            files.extend(sorted(p.glob("*.mmd")))
            if args.include_svg:
                svg_files.extend(sorted(p.glob("*.svg")))
        elif p.is_file():
            if p.suffix.lower() == ".svg":
                svg_files.append(p)
            else:
                files.append(p)
        else:
            print(f"跳过不存在的路径：{raw}", file=sys.stderr)
    if not files and not svg_files:
        sys.exit("没有找到 .mmd 或 .svg 文件")

    base = (files or svg_files)[0].parent
    outdir = Path(args.outdir).expanduser() if args.outdir else base / "out"
    outdir.mkdir(parents=True, exist_ok=True)
    records = []
    for f in files:
        rec = process_file(f, outdir, args.preset, args.format, args.scale, args.transparent)
        records.append(rec)
        if rec["ok"]:
            print(f"✓ {f.name}  →  {rec['w']}×{rec['h']}  ({rec['type']})")
        else:
            print(f"✗ {f.name}  {rec.get('error')}", file=sys.stderr)

    for s in svg_files:
        text = s.read_text(encoding="utf-8")
        w, h = svg_size(text)
        shutil.copy2(s, outdir / s.name)
        records.append(
            {"name": s.stem, "source": s, "type": "svg", "ok": True, "w": w, "h": h}
        )
        print(f"✓ {s.name}  →  {w}×{h}  (手绘 SVG)")


    if args.gallery:
        g = write_gallery(records, outdir, args.preset)
        print(f"总览页：{g}")
    print(f"输出目录：{outdir}")

    failed = [r for r in records if not r["ok"]]
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
