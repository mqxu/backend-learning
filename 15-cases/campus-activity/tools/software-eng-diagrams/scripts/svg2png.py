#!/usr/bin/env python3
"""把 SVG 转成 PNG，用于插入 Word / PPT 或不支持矢量的场景。

用法：
    python3 svg2png.py 架构图.svg                      # 输出 2 倍图
    python3 svg2png.py diagrams/*.svg --scale 3        # 投屏用 3 倍图
    python3 svg2png.py diagrams/ -o png --gallery      # 批量 + HTML 总览页

宽高从 SVG 的 width/height 读；缺失时退回 viewBox。
背景默认白色，加 --transparent 输出透明底。
"""

from __future__ import annotations

import argparse
import html
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

FONT_STACK = "'PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif"

CHROME_CANDIDATES = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
]


def find_chrome() -> str:
    import os

    env = os.environ.get("CHROME_PATH")
    if env and Path(env).exists():
        return env
    for c in CHROME_CANDIDATES:
        if Path(c).exists():
            return c
    found = shutil.which("google-chrome") or shutil.which("chromium")
    if found:
        return found
    sys.exit("找不到 Chrome / Chromium。安装 Chrome 或设置环境变量 CHROME_PATH。")


def svg_size(svg: str) -> tuple[int, int]:
    head = svg[:600]
    mw = re.search(r'\bwidth="([\d.]+)(?:px)?"', head)
    mh = re.search(r'\bheight="([\d.]+)(?:px)?"', head)
    if mw and mh:
        return max(1, round(float(mw.group(1)))), max(1, round(float(mh.group(1))))
    mv = re.search(r'viewBox="([-\d.\s,eE]+)"', svg)
    if mv:
        parts = re.split(r"[,\s]+", mv.group(1).strip())
        return max(1, round(float(parts[2]))), max(1, round(float(parts[3])))
    return 1200, 800


def convert(src: Path, out: Path, scale: float, transparent: bool) -> tuple[int, int]:
    svg = src.read_text(encoding="utf-8")
    w, h = svg_size(svg)
    page = (
        '<!DOCTYPE html><html><head><meta charset="utf-8">'
        f'<style>html,body{{margin:0;padding:0;background:{"transparent" if transparent else "#FFFFFF"}}}'
        f'svg{{display:block}}</style></head><body>{svg}</body></html>'
    )
    tmp = Path(tempfile.mkdtemp(prefix="svg2png-"))
    try:
        p = tmp / "page.html"
        p.write_text(page, encoding="utf-8")
        args = [
            find_chrome(),
            "--headless=new",
            "--disable-gpu",
            "--no-sandbox",
            "--hide-scrollbars",
            "--disable-extensions",
            "--force-device-scale-factor=" + str(scale),
            f"--window-size={w},{h}",
            f"--screenshot={out}",
        ]
        if transparent:
            args.append("--default-background-color=00000000")
        args.append(p.as_uri())
        subprocess.run(args, capture_output=True, text=True, timeout=120)
        if not out.exists() or out.stat().st_size == 0:
            raise RuntimeError("截图失败，检查 SVG 是否有效")
        return w, h
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def write_gallery(items: list[tuple[Path, Path, int, int]], outdir: Path) -> Path:
    cards = []
    for svg_path, png_path, w, h in items:
        cards.append(
            f"""<section class="card">
  <header><h2>{html.escape(svg_path.stem)}</h2><span class="tag">{w} × {h}</span></header>
  <div class="canvas"><img src="{png_path.name}" alt="{html.escape(svg_path.stem)}"></div>
</section>"""
        )
    doc = f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>图集</title>
<style>
body{{margin:0;padding:36px 28px 64px;background:#F6F8FB;color:#0F172A;
  font-family:{FONT_STACK};line-height:1.65}}
.wrap{{max-width:1180px;margin:0 auto}}
h1{{font-size:24px;margin:0 0 6px}}
.sub{{color:#64748B;font-size:13px;margin-bottom:28px}}
.card{{background:#fff;border:1px solid #E2E8F0;border-radius:14px;padding:22px 24px 18px;
  margin-bottom:22px;box-shadow:0 1px 2px rgba(15,23,42,.04)}}
.card header{{display:flex;align-items:baseline;gap:12px;margin-bottom:14px;
  padding-bottom:12px;border-bottom:1px solid #EEF2F7}}
h2{{font-size:16px;margin:0;font-weight:600}}
.tag{{font-size:12px;color:#64748B;background:#F1F5F9;border-radius:999px;padding:2px 10px}}
.canvas{{overflow-x:auto;text-align:center}}
.canvas img{{max-width:100%;height:auto}}
</style></head><body><div class="wrap">
<h1>图集</h1><div class="sub">共 {len(items)} 张</div>
{''.join(cards)}
</div></body></html>"""
    out = outdir / "index.html"
    out.write_text(doc, encoding="utf-8")
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description="SVG 转 PNG")
    ap.add_argument("inputs", nargs="+", help=".svg 文件或目录")
    ap.add_argument("-o", "--outdir", help="输出目录，默认与源文件同级")
    ap.add_argument("--scale", type=float, default=2.0, help="缩放倍数，默认 2")
    ap.add_argument("--transparent", action="store_true", help="透明背景")
    ap.add_argument("--gallery", action="store_true", help="生成 index.html 总览页")
    args = ap.parse_args()

    files: list[Path] = []
    from_dir = False
    for raw in args.inputs:
        p = Path(raw).expanduser()
        if p.is_dir():
            files.extend(sorted(p.glob("*.svg")))
            from_dir = True
        elif p.is_file():
            files.append(p)
        else:
            print(f"跳过不存在的路径：{raw}", file=sys.stderr)
    if not files:
        sys.exit("没有找到 .svg 文件")

    # 目录输入时默认落到 out/ 子目录，与 render_mermaid.py 保持一致
    default_out = files[0].parent / "out" if from_dir else None

    done: list[tuple[Path, Path, int, int]] = []
    for f in files:
        outdir = Path(args.outdir).expanduser() if args.outdir else (default_out or f.parent)
        outdir.mkdir(parents=True, exist_ok=True)
        out = outdir / (f.stem + ".png")
        try:
            w, h = convert(f, out, args.scale, args.transparent)
            print(f"✓ {f.name}  →  {out.name}  {int(w * args.scale)}×{int(h * args.scale)}px")
            done.append((f, out, w, h))
        except Exception as e:  # noqa: BLE001
            print(f"✗ {f.name}  {e}", file=sys.stderr)

    if args.gallery and done:
        g = write_gallery(done, done[0][1].parent)
        print(f"总览页：{g}")
    sys.exit(0 if len(done) == len(files) else 1)


if __name__ == "__main__":
    main()
