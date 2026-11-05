#!/usr/bin/env node
/**
 * 把单元测试执行结果与 JaCoCo 覆盖率汇总渲染成一张图。
 *
 *   node tools/shot-test.mjs 11-test
 *
 * 数据来源：target/surefire-reports/*.txt（用例数）与 target/site/jacoco/jacoco.csv（覆盖率）。
 * 前提是先跑过 mvn -pl <module> test。
 */
import { execSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { screenshot } from './shot.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const MODULE = process.argv[2] ?? '11-test';
const moduleDir = path.join(ROOT, MODULE);

const surefireDir = path.join(moduleDir, 'target/surefire-reports');
const jacocoCsv = path.join(moduleDir, 'target/site/jacoco/jacoco.csv');

if (!existsSync(surefireDir) || !existsSync(jacocoCsv)) {
  console.error(`找不到测试报告，先执行：mvn -pl ${MODULE} test`);
  process.exit(1);
}

/* ---------------- 用例统计 ---------------- */

const suites = readdirSync(surefireDir)
  .filter((f) => f.startsWith('TEST-') && f.endsWith('.xml'))
  .map((f) => {
    // 顶层统计在 <testsuite> 的属性里；@Nested 的用例已经合并进所属类的计数
    const head = readFileSync(path.join(surefireDir, f), 'utf8').slice(0, 4000);
    const tag = /<testsuite\s([^>]*)>/.exec(head);
    if (!tag) return null;
    const attr = (name) => {
      const m = new RegExp(`${name}="([^"]*)"`).exec(tag[1]);
      return m ? m[1] : '';
    };
    const cls = attr('name').split('.').pop();
    return {
      name: cls,
      fullName: attr('name'),
      tests: Number(attr('tests') || 0),
      failures: Number(attr('failures') || 0),
      errors: Number(attr('errors') || 0),
      skipped: Number(attr('skipped') || 0),
      seconds: Number(attr('time') || 0),
    };
  })
  .filter(Boolean)
  .sort((a, b) => b.tests - a.tests);

const total = suites.reduce(
  (acc, s) => ({
    tests: acc.tests + s.tests,
    failures: acc.failures + s.failures,
    errors: acc.errors + s.errors,
    skipped: acc.skipped + s.skipped,
    seconds: acc.seconds + s.seconds,
  }),
  { tests: 0, failures: 0, errors: 0, skipped: 0, seconds: 0 },
);

/* ---------------- 覆盖率 ---------------- */

const rows = readFileSync(jacocoCsv, 'utf8')
  .trim()
  .split('\n')
  .slice(1)
  .map((line) => {
    const c = line.split(',');
    return {
      pkg: c[1],
      cls: c[2],
      lineCovered: Number(c[8]),
      lineMissed: Number(c[7]),
      // CSV 列序：0 GROUP,1 PACKAGE,2 CLASS,3 INSTR_MISSED,4 INSTR_COVERED,
      // 5 BRANCH_MISSED,6 BRANCH_COVERED,7 LINE_MISSED,8 LINE_COVERED
      branchCovered: Number(c[6]),
      branchMissed: Number(c[5]),
    };
  })
  .filter((r) => r.lineCovered + r.lineMissed > 0);

const sum = (key) => rows.reduce((a, r) => a + r[key], 0);
const lineTotal = sum('lineCovered') + sum('lineMissed');
const branchTotal = sum('branchCovered') + sum('branchMissed');
const pct = (a, b) => (b === 0 ? '—' : ((a / b) * 100).toFixed(1) + '%');

const primary = rows
  .filter((r) => !r.cls.endsWith('Application') && !r.pkg.endsWith('.dto'))
  .sort((a, b) => (b.lineCovered + b.lineMissed) - (a.lineCovered + a.lineMissed));

/* ---------------- 渲染 ---------------- */

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const suiteRows = suites
  .map(
    (s) => `<tr>
      <td class="name">${esc(s.name)}</td>
      <td class="num">${s.tests}</td>
      <td class="ok">${s.tests - s.failures - s.errors - s.skipped}</td>
      <td class="${s.failures + s.errors > 0 ? 'err' : 'muted'}">${s.failures + s.errors}</td>
      <td class="muted">${s.skipped}</td>
      <td class="muted">${s.seconds.toFixed(3)} s</td>
    </tr>`,
  )
  .join('');

const coverRows = primary
  .map((r) => {
    const lt = r.lineCovered + r.lineMissed;
    const bt = r.branchCovered + r.branchMissed;
    const lineRate = (r.lineCovered / lt) * 100;
    const cls = lineRate >= 80 ? 'ok' : lineRate >= 50 ? 'warn' : 'err';
    return `<tr>
      <td class="name"><code>${esc(r.cls)}</code><span class="pkg">${esc(r.pkg)}</span></td>
      <td class="num ${cls}">${pct(r.lineCovered, lt)}</td>
      <td class="muted">${r.lineCovered}/${lt}</td>
      <td class="num ${cls}">${bt === 0 ? '—' : pct(r.branchCovered, bt)}</td>
      <td><div class="bar"><i style="width:${lineRate.toFixed(1)}%" class="${cls}"></i></div></td>
    </tr>`;
  })
  .join('');

const allGreen = total.failures + total.errors === 0;

const html = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  body{width:1180px;background:#f5f6f8;font-family:"PingFang SC","Helvetica Neue",Arial,sans-serif;color:#1f2329;padding:34px 40px 40px}
  h1{font-size:22px;font-weight:600}
  .meta{color:#6b7280;font-size:12.5px;margin-top:8px;line-height:1.7}
  .meta code{background:#e8eaee;padding:1px 6px;border-radius:4px;font-size:12px;font-family:ui-monospace,Menlo,monospace}
  .cards{display:flex;gap:14px;margin:22px 0 26px}
  .card{flex:1;background:#fff;border-radius:10px;padding:16px 18px;box-shadow:0 1px 2px rgba(16,24,40,.06),0 0 0 1px rgba(16,24,40,.06)}
  .card .k{font-size:11.5px;color:#8a93a0;letter-spacing:.6px}
  .card .v{font-size:26px;font-weight:600;margin-top:6px;font-family:ui-monospace,Menlo,monospace}
  .card .v.ok{color:#12805c}.card .v.err{color:#c62828}
  .card .s{font-size:11.5px;color:#9aa3af;margin-top:4px}
  table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 2px rgba(16,24,40,.06),0 0 0 1px rgba(16,24,40,.06);font-size:13px;margin-bottom:24px}
  th,td{padding:9px 14px;text-align:left;border-bottom:1px solid #eef0f3}
  th{background:#fafbfc;font-weight:600;color:#4b5563;font-size:12px;letter-spacing:.4px}
  tr:last-child td{border-bottom:none}
  td.num{font-family:ui-monospace,Menlo,monospace;font-weight:600;width:86px}
  td.muted{color:#9aa3af}
  td.ok,.num.ok{color:#12805c}.num.warn{color:#a26a00}.num.err,.err{color:#c62828}
  td.name{font-weight:500}
  .pkg{display:block;font-size:11px;color:#9aa3af;font-family:ui-monospace,Menlo,monospace;margin-top:2px}
  .bar{width:150px;height:6px;background:#eef0f3;border-radius:3px;overflow:hidden}
  .bar i{display:block;height:100%;border-radius:3px}
  .bar i.ok{background:#2da44e}.bar i.warn{background:#d4a72c}.bar i.err{background:#cf222e}
  h2{font-size:14px;font-weight:600;margin:26px 0 10px;color:#374151}
  .footer{margin-top:22px;font-size:11.5px;color:#9aa3af;text-align:right}
</style></head><body>
<h1>${esc(MODULE)} · 测试执行结果</h1>
<div class="meta">
  命令 <code>mvn -pl ${MODULE} test</code> · 报告 <code>target/surefire-reports</code> 与 <code>target/site/jacoco/index.html</code><br>
  下列数字来自 surefire 与 JaCoCo 的实际输出。
</div>

<div class="cards">
  <div class="card"><div class="k">用例总数</div><div class="v">${total.tests}</div><div class="s">${suites.length} 个测试类</div></div>
  <div class="card"><div class="k">通过</div><div class="v ok">${total.tests - total.failures - total.errors - total.skipped}</div><div class="s">${allGreen ? '全部通过' : '存在失败'}</div></div>
  <div class="card"><div class="k">失败 / 错误</div><div class="v ${allGreen ? 'ok' : 'err'}">${total.failures + total.errors}</div><div class="s">跳过 ${total.skipped}</div></div>
  <div class="card"><div class="k">行覆盖率</div><div class="v">${pct(sum('lineCovered'), lineTotal)}</div><div class="s">${sum('lineCovered')}/${lineTotal} 行</div></div>
  <div class="card"><div class="k">分支覆盖率</div><div class="v">${pct(sum('branchCovered'), branchTotal)}</div><div class="s">${sum('branchCovered')}/${branchTotal} 个分支</div></div>
</div>

<h2>各测试类执行情况</h2>
<table>
  <thead><tr><th>测试类</th><th>用例</th><th>通过</th><th>失败</th><th>跳过</th><th>耗时</th></tr></thead>
  <tbody>${suiteRows}</tbody>
</table>

<h2>按类统计的覆盖率</h2>
<table>
  <thead><tr><th>类</th><th>行覆盖</th><th>行数</th><th>分支覆盖</th><th>比例</th></tr></thead>
  <tbody>${coverRows}</tbody>
</table>

<div class="footer">backend-learning · ${esc(MODULE)}</div>
</body></html>`;

const outHtml = path.join(ROOT, 'tools/out', `${MODULE}-test-report.html`);
writeFileSync(outHtml, html);
const png = path.join(ROOT, 'docs/public/shots', `${MODULE}-report.webp`);
const size = await screenshot(outHtml, png);
console.log(`图 docs/public/shots/${MODULE}-report.webp  ${size.width}×${size.height}`);
console.log(`用例 ${total.tests}，失败 ${total.failures + total.errors}，行覆盖 ${pct(sum('lineCovered'), lineTotal)}`);
