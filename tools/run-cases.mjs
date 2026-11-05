#!/usr/bin/env node
/**
 * 模块接口实跑工具。
 *
 *   node tools/run-cases.mjs 01-quickstart            # 构建 + 启动 + 跑用例 + 出图 + 出 openapi
 *   node tools/run-cases.mjs 01-quickstart --no-build # 跳过 mvn package
 *   node tools/run-cases.mjs 01-quickstart --keep     # 跑完不杀进程
 *
 * 用例定义在 tools/cases/<module>.json，一份定义同时产出三样东西：
 *   docs/public/shots/<module>-<group>.webp  接口运行截图（无损 WebP）
 *   docs/apifox/<module>.openapi.json        OpenAPI 3.0 描述
 *   tools/out/<module>.report.json           原始请求响应记录
 */
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as sleep } from 'node:timers/promises';
import { screenshot } from './shot.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ARGS = process.argv.slice(2);
const MODULE = ARGS.find((a) => !a.startsWith('--'));
const NO_BUILD = ARGS.includes('--no-build');
const KEEP = ARGS.includes('--keep');

if (!MODULE) {
  console.error('用法: node tools/run-cases.mjs <模块目录名> [--no-build] [--keep]');
  process.exit(1);
}

const spec = JSON.parse(readFileSync(path.join(ROOT, 'tools/cases', `${MODULE}.json`), 'utf8'));
const PORT = spec.port ?? Number(String(MODULE).slice(0, 2)) + 8000;
const BASE = `http://127.0.0.1:${PORT}`;
const OUT_SHOTS = path.join(ROOT, 'docs/public/shots');
const OUT_API = path.join(ROOT, 'docs/apifox');
const OUT_RAW = path.join(ROOT, 'tools/out');
for (const d of [OUT_SHOTS, OUT_API, OUT_RAW]) mkdirSync(d, { recursive: true });

// 模块名可能带目录层级（15-cases/zhihu），落盘时换成短横线，避免多建一层目录
const SLUG = MODULE.replace(/\//g, '-');
const log = (...a) => console.log(`[${MODULE}]`, ...a);

/* ---------------- 构建与启动 ---------------- */

function sh(cmd, args, opts = {}) {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { cwd: ROOT, stdio: ['ignore', 'pipe', 'pipe'], ...opts });
    let out = '';
    p.stdout.on('data', (d) => (out += d));
    p.stderr.on('data', (d) => (out += d));
    p.on('close', (code) => (code === 0 ? resolve(out) : reject(new Error(`${cmd} 退出码 ${code}\n${out.slice(-3000)}`))));
  });
}

function findJar() {
  // spec.jarDir 用于不在父工程 reactor 里的独立项目（如 campus-activity/server）
  const targetDir = path.join(ROOT, spec.jarDir ?? path.join(MODULE, 'target'));
  if (!existsSync(targetDir)) return null;
  const jar = readdirSync(targetDir).find((f) => f.endsWith('.jar') && !f.endsWith('.original'));
  return jar ? path.join(targetDir, jar) : null;
}

async function build() {
  if (NO_BUILD) {
    const jar = findJar();
    if (!jar) throw new Error('没找到 jar，先去掉 --no-build 构建一次');
    return jar;
  }
  log('mvn package ...');
  if (spec.mavenDir) {
    // 独立工程：在它自己的目录里构建，不走父工程的 -pl
    await sh('mvn', ['-o', '-q', 'package', '-DskipTests'], { cwd: path.join(ROOT, spec.mavenDir) });
  } else {
    await sh('mvn', ['-o', '-q', '-pl', MODULE, 'package', '-DskipTests']);
  }
  const jar = findJar();
  if (!jar) throw new Error('构建后仍没找到 jar');
  return jar;
}

async function waitPort(proc, timeoutMs = 90000) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    if (proc.exitCode !== null) throw new Error(`进程提前退出（${proc.exitCode}）\n${tailLog()}`);
    try {
      await fetch(BASE + '/', { signal: AbortSignal.timeout(800) });
      return;
    } catch (e) {
      if (e.name === 'TimeoutError' || e.cause) {
        try {
          const s = await import('node:net').then(({ connect }) => connect);
          await new Promise((res, rej) => {
            const c = s({ host: '127.0.0.1', port: PORT });
            c.on('connect', () => (c.destroy(), res()));
            c.on('error', rej);
            c.setTimeout(500, () => (c.destroy(), rej(new Error('timeout'))));
          });
          return;
        } catch { /* 还没起来 */ }
      }
    }
    await sleep(400);
  }
  throw new Error(`端口 ${PORT} 在 ${timeoutMs}ms 内未就绪\n${tailLog()}`);
}

let procLog = [];
const tailLog = (n = 40) => procLog.slice(-n).join('');

async function start(jar) {
  const args = ['-jar', jar, `--server.port=${PORT}`, '--spring.main.banner-mode=off'];
  if (spec.startupArgs) args.push(...spec.startupArgs);
  const p = spawn('java', args, {
    cwd: path.join(ROOT, MODULE),
    env: { ...process.env, SERVER__PORT: String(PORT), SERVER__HOST: '127.0.0.1' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  const push = (d) => {
    procLog.push(...d.toString().split('\n').filter(Boolean).map((l) => l + '\n'));
    if (procLog.length > 4000) procLog = procLog.slice(-2000);
  };
  p.stdout.on('data', push);
  p.stderr.on('data', push);
  await waitPort(p);
  log(`已启动，端口 ${PORT}`);
  return p;
}

/* ---------------- 发请求 ---------------- */

const vars = {};

function resolve(value) {
  if (typeof value === 'string') {
    return value.replace(/\$\{([^}]+)\}/g, (_, expr) => {
      const got = expr.split('.').reduce((o, k) => (o == null ? o : o[k]), vars);
      if (got === undefined) throw new Error(`变量未定义: \${${expr}}`);
      return String(got);
    });
  }
  if (Array.isArray(value)) return value.map(resolve);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, resolve(v)]));
  }
  return value;
}

function pick(obj, expr) {
  return expr.split('.').reduce((o, k) => (o == null ? o : o[k]), obj);
}

/** 宽松解析：未定义的变量留空，用于 defaultHeaders 这类可能先于变量产生的场景 */
function resolveLoose(value) {
  if (typeof value === 'string') {
    return value.replace(/\$\{([^}]+)\}/g, (_, expr) => {
      const v = expr.split('.').reduce((o, k) => (o == null ? o : o[k]), vars);
      return v === undefined ? '' : String(v);
    });
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, resolveLoose(v)]));
  }
  return value;
}

function mergedHeaders(req) {
  const base = req.noDefaultHeaders ? {} : resolveLoose(spec.defaultHeaders ?? {});
  const all = { ...base, ...resolveLoose(req.headers ?? {}) };
  // 变量还没产生时（比如第一条登录请求）会解析成空串，这类头直接不发
  return Object.fromEntries(Object.entries(all).filter(([, v]) => v !== ''));
}

function maskHeader(name, value) {
  // JWT 有 200 多个字符，原样打出来会把命令挤成好几行；展示时折成变量，实际请求仍用完整值
  const v = String(value);
  return /^authorization$/i.test(name) && v.length > 40
    ? v.replace(/^(Bearer\s+).*$/i, '$1$TOKEN')
    : v;
}

function curlPreview(req, url) {
  const parts = [`curl -i -X ${req.method}`];
  for (const [k, v] of Object.entries(req.headers ?? {})) {
    parts.push(`-H '${k}: ${maskHeader(k, v)}'`);
  }
  if (req.json) parts.push(`-H 'Content-Type: application/json'`);
  if (req.form) {
    // 表单字段与文件都按真实参数写出来，文件只显示仓库内路径，不暴露本机绝对路径
    for (const [k, v] of Object.entries(resolve(req.form.fields ?? {}))) {
      parts.push(`-F '${k}=${v}'`);
    }
    for (const f of req.form.files ?? []) {
      const raw = String(f.path);
      const shown = raw.includes('/Users/') ? raw.split('/').pop() : raw;
      parts.push(`-F '${f.name}=@${shown}'`);
    }
  }
  if (req.json) parts.push(`-d '${JSON.stringify(req.json)}'`);
  parts.push(`'${url}'`);
  return parts.join(' \\\n  ');
}

async function runCase(c) {
  const req = c.req ?? { method: 'GET', path: '/' };
  // 定时任务类的用例需要先让任务跑几轮
  if (c.wait) await sleep(c.wait);
  if (c.parallel) return runParallel(c, req);

  const method = (req.method ?? 'GET').toUpperCase();
  const qs = req.query
    ? '?' +
      new URLSearchParams(Object.entries(resolve(req.query)).map(([k, v]) => [k, String(v)])).toString()
    : '';
  const url = BASE + resolve(req.path) + qs;

  const headers = mergedHeaders(req);
  let body;
  if (req.json !== undefined) {
    headers['Content-Type'] = headers['Content-Type'] ?? 'application/json';
    body = JSON.stringify(resolve(req.json));
  } else if (req.form) {
    const fd = new FormData();
    for (const [k, v] of Object.entries(resolve(req.form.fields ?? {}))) fd.append(k, String(v));
    for (const f of req.form.files ?? []) {
      const abs = path.resolve(ROOT, f.path);
      if (!existsSync(abs)) throw new Error(`上传文件不存在: ${f.path}`);
      fd.append(f.name, new Blob([readFileSync(abs)]), f.filename ?? path.basename(abs));
    }
    body = fd;
  } else if (req.raw !== undefined) {
    body = req.raw;
  }

  const started = Date.now();
  const res = await fetch(url, {
    method,
    headers,
    body,
    // manual：短链跳转这类用例要看到原始的 302 与 Location，不能被自动跟随
    redirect: req.redirect ?? 'follow',
    signal: AbortSignal.timeout(30000),
  });
  const ms = Date.now() - started;
  const resHeaders = Object.fromEntries(res.headers.entries());
  const ct = resHeaders['content-type'] ?? '';
  let text;
  let note;
  if (/image|octet-stream|pdf|excel|zip/.test(ct)) {
    const buf = Buffer.from(await res.arrayBuffer());
    note = `二进制响应，${buf.length} 字节（${ct.split(';')[0]}）`;
    text = note;
  } else {
    const raw = await res.text();
    try {
      text = JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      text = raw;
    }
  }

  if (req.save) {
    let parsed = null;
    try {
      parsed = JSON.parse(text);
    } catch { /* 纯文本响应不能抽变量 */ }
    for (const [name, expr] of Object.entries(req.save)) {
      let v;
      if (expr && typeof expr === 'object' && expr.regex) {
        // 值藏在文本里时用正则抓，比如"验证码已发送（演示模式，验证码：123456）"
        const src = pick(parsed, expr.from);
        const matched = new RegExp(expr.regex).exec(String(src ?? ''));
        v = matched ? matched[1] : undefined;
      } else {
        v = pick(parsed, expr);
      }
      if (v === undefined) throw new Error(`用例 ${c.id} 抽取变量失败: ${JSON.stringify(expr)}，响应体：${text.slice(0, 400)}`);
      vars[name] = v;
    }
  }

  return {
    id: c.id,
    name: c.name,
    desc: c.desc,
    group: c.group ?? '接口验证',
    curl: curlPreview({ ...req, headers }, url),
    request: { method, url, headers, body: typeof body === 'string' ? body : req.form ? '(multipart)' : undefined },
    response: { status: res.status, headers: resHeaders, body: text },
    maxBody: c.maxBody,
    ms,
  };
}

/* ---------------- 并发用例 ---------------- */

function buildUrl(req) {
  const qs = req.query
    ? '?' + new URLSearchParams(Object.entries(resolve(req.query)).map(([k, v]) => [k, String(v)])).toString()
    : '';
  return BASE + resolve(req.path) + qs;
}

async function runParallel(c, req) {
  const n = c.parallel;
  const url = buildUrl(req);
  const headers = mergedHeaders(req);
  const method = (req.method ?? 'GET').toUpperCase();
  let body;
  if (req.json !== undefined) {
    headers['Content-Type'] = headers['Content-Type'] ?? 'application/json';
    body = JSON.stringify(resolve(req.json));
  }

  const started = Date.now();
  const shots = await Promise.all(
    Array.from({ length: n }, async () => {
      const t0 = Date.now();
      try {
        const res = await fetch(url, {
    method,
    headers,
    body,
    // manual：短链跳转这类用例要看到原始的 302 与 Location，不能被自动跟随
    redirect: req.redirect ?? 'follow',
    signal: AbortSignal.timeout(30000),
  });
        const raw = await res.text();
        let text;
        try {
          text = JSON.stringify(JSON.parse(raw), null, 2);
        } catch {
          text = raw;
        }
        return { status: res.status, ms: Date.now() - t0, body: text };
      } catch (e) {
        return { status: 0, ms: Date.now() - t0, body: `请求失败: ${e.message}` };
      }
    }),
  );
  const totalMs = Date.now() - started;

  const okCount = shots.filter((s) => s.status >= 200 && s.status < 300).length;
  const summary = {
    total: n,
    ok: okCount,
    failed: n - okCount,
    totalMs,
    avgMs: Math.round(shots.reduce((a, s) => a + s.ms, 0) / n),
  };

  return {
    id: c.id,
    name: c.name,
    desc: c.desc,
    group: c.group ?? '接口验证',
    curl: curlPreview({ ...req, headers }, url) + `\n# 并发 ${n} 次`,
    request: { method, url, headers, body: typeof body === 'string' ? body : undefined },
    response: { status: okCount === n ? 200 : 429, headers: {}, body: '' },
    ms: totalMs,
    parallel: { summary, shots },
  };
}

/* ---------------- 出图 ---------------- */

const esc = (s) =>
  String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const BIN = /二进制|application\/(pdf|octet)|image\/|excel/;
const inlineable = (r) => !BIN.test(r.response.headers?.['content-type'] ?? '');

function statusClass(s) {
  if (s < 300) return 'ok';
  if (s < 400) return 'warn';
  return 'err';
}

function respBlock(r) {
  if (r.parallel) {
    const { summary, shots } = r.parallel;
    const rows = shots
      .map(
        (s, i) =>
          `<tr><td class="m">#${i + 1}</td><td class="${statusClass(s.status)}">${s.status || 'ERR'}</td><td class="ms">${s.ms} ms</td><td><code>${esc(
            (s.body || '').replace(/\s+/g, ' ').slice(0, 96),
          )}</code></td></tr>`,
      )
      .join('');
    return `<div class="term resp">
    <div class="term-bar"><i></i><i></i><i></i><span>并发 ${summary.total} 次的结果</span></div>
    <div class="sum">
      <span>成功 <b class="ok">${summary.ok}</b></span>
      <span>失败/拒绝 <b class="err">${summary.failed}</b></span>
      <span>总耗时 <b>${summary.totalMs} ms</b></span>
      <span>平均 <b>${summary.avgMs} ms</b></span>
    </div>
    <table class="mini"><thead><tr><th>#</th><th>状态</th><th>耗时</th><th>响应摘要</th></tr></thead><tbody>${rows}</tbody></table>
  </div>`;
  }
  const resHdr = Object.entries(r.response.headers)
    .filter(([k]) => /content-type|content-length|location|set-cookie|authorization|x-trace/i.test(k))
    .map(([k, v]) => `${k}: ${v}`)
    .join('\n');
  return `<div class="term resp">
    <div class="term-bar"><i></i><i></i><i></i><span>响应</span></div>
    <pre><code><span class="st ${statusClass(r.response.status)}">HTTP/1.1 ${r.response.status}</span>${resHdr ? '\n' + esc(resHdr) : ''}</code></pre>
    <pre class="body">${esc(r.response.body).slice(0, r.maxBody ?? 2600)}${r.response.body.length > (r.maxBody ?? 2600) ? '\n…（已截断，完整内容见响应）' : ''}</pre>
  </div>`;
}

function caseCard(r, idx) {
  const hdr = Object.entries(r.request.headers ?? {})
    .map(([k, v]) => `${k}: ${maskHeader(k, v)}`)
    .join('\n');

  const bodyBlock = r.request.body
    ? `<div class="seg"><span class="seg-t">请求体</span><pre>${esc(pretty(r.request.body))}</pre></div>`
    : '';

  return `
<section class="case">
  <div class="case-hd">
    <span class="num">${esc(r.name.split(' ')[0])}</span>
    <h3>${esc(r.name.replace(/^\S+\s*/, ''))}</h3>
    <span class="badge ${r.parallel ? 'ok' : statusClass(r.response.status)}">${r.parallel ? `并发 ${r.parallel.summary.total}` : r.response.status}</span>
    <span class="ms">${r.ms} ms</span>
  </div>
  <p class="desc">${esc(r.desc ?? '')}</p>
  <div class="term">
    <div class="term-bar"><i></i><i></i><i></i><span>请求</span></div>
    <pre><code><span class="pr">$</span> ${esc(r.curl)}</code></pre>
    ${hdr ? `<pre class="dim">${esc(hdr)}</pre>` : ''}
    ${bodyBlock}
  </div>
  ${respBlock(r)}
</section>`;
}

function pretty(s) {
  try {
    return JSON.stringify(JSON.parse(s), null, 2);
  } catch {
    return s;
  }
}

function renderGroup(group, results, startedAt, consoleLines = []) {
  const rows = results
    .map(
      (r) =>
        `<tr><td class="m">${esc(r.request.method)}</td><td><code>${esc(
          r.request.url.replace(BASE, ''),
        )}</code></td><td>${esc(r.name.replace(/^\S+\s*/, ''))}</td><td class="${statusClass(
          r.response.status,
        )}">${r.response.status}</td></tr>`,
    )
    .join('');

  const consoleBlock = consoleLines.length
    ? `<div class="term wide">
    <div class="term-bar"><i></i><i></i><i></i><span>${esc(spec.console?.title ?? '应用控制台输出')}</span></div>
    <pre class="log">${esc(consoleLines.join('\n'))}</pre>
  </div>`
    : '';

  return `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  body{width:1180px;background:#f5f6f8;font-family:"PingFang SC","Helvetica Neue",Arial,sans-serif;color:#1f2329;padding:34px 40px 40px}
  h1{font-size:22px;font-weight:600;letter-spacing:.2px}
  .meta{color:#6b7280;font-size:12.5px;margin-top:8px;line-height:1.7}
  .meta code{background:#e8eaee;padding:1px 6px;border-radius:4px;font-size:12px}
  table{width:100%;border-collapse:collapse;margin:20px 0 26px;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 2px rgba(16,24,40,.06),0 0 0 1px rgba(16,24,40,.06);font-size:13px}
  th,td{padding:9px 14px;text-align:left;border-bottom:1px solid #eef0f3}
  th{background:#fafbfc;font-weight:600;color:#4b5563;font-size:12px;letter-spacing:.4px}
  tr:last-child td{border-bottom:none}
  td.m{font-family:ui-monospace,Menlo,monospace;font-weight:600;color:#0b63c5;width:70px}
  td.ok{color:#12805c;font-weight:600}td.warn{color:#a26a00;font-weight:600}td.err{color:#c62828;font-weight:600}
  code{font-family:ui-monospace,"SF Mono",Menlo,monospace}
  .case{background:#fff;border-radius:10px;padding:20px 22px 22px;margin-bottom:18px;box-shadow:0 1px 2px rgba(16,24,40,.06),0 0 0 1px rgba(16,24,40,.06)}
  .case-hd{display:flex;align-items:center;gap:10px}
  .num{font-family:ui-monospace,Menlo,monospace;font-size:12px;color:#0b63c5;background:#e8f1fb;padding:2px 7px;border-radius:4px}
  .case-hd h3{font-size:15px;font-weight:600;flex:1}
  .badge{font-family:ui-monospace,Menlo,monospace;font-size:12px;padding:2px 8px;border-radius:4px;font-weight:600}
  .badge.ok{background:#e7f6ef;color:#12805c}.badge.warn{background:#fdf3e0;color:#a26a00}.badge.err{background:#fdeaea;color:#c62828}
  .ms{font-size:11.5px;color:#9aa3af}
  .desc{font-size:12.5px;color:#5b6472;margin:8px 0 12px}
  .term{background:#1c2128;border-radius:8px;overflow:hidden;margin-bottom:8px}
  .term-bar{display:flex;align-items:center;gap:6px;padding:7px 12px;background:#22272f}
  .term-bar i{width:9px;height:9px;border-radius:50%;background:#3d434d}
  .term-bar i:nth-child(1){background:#ec6a5e}.term-bar i:nth-child(2){background:#f4bf4f}.term-bar i:nth-child(3){background:#61c554}
  .term-bar span{margin-left:6px;font-size:11px;color:#7d8590;font-family:ui-monospace,Menlo,monospace}
  .term pre{padding:12px 14px;font-family:ui-monospace,"SF Mono",Menlo,monospace;font-size:12px;line-height:1.68;color:#c9d1d9;white-space:pre-wrap;word-break:break-all}
  .term pre.dim{color:#8b949e;padding-top:0}
  .term pre.body{color:#a5d6ff;padding-top:0;max-height:300px;overflow:hidden}
  .pr{color:#61c554}.st.ok{color:#7ee787}.st.warn{color:#f4bf4f}.st.err{color:#ff7b72}
  .seg{padding:0 14px 12px}
  .term pre.log{color:#c9d1d9;font-size:11.5px;line-height:1.62}
  .term.wide{margin-bottom:24px}
  .sum{display:flex;gap:26px;padding:12px 14px 6px;font-size:12px;color:#8b949e;font-family:ui-monospace,Menlo,monospace}
  .sum b{font-weight:600;color:#c9d1d9}.sum b.ok{color:#7ee787}.sum b.err{color:#ff7b72}
  table.mini{width:calc(100% - 20px);margin:6px 10px 10px;border-collapse:collapse;background:transparent;box-shadow:none;border-radius:0;font-size:11.5px}
  table.mini th{background:transparent;color:#7d8590;border-bottom:1px solid #30363d;padding:5px 8px;font-weight:500}
  table.mini td{border-bottom:1px solid #262c34;padding:5px 8px;color:#c9d1d9}
  table.mini td.ok{color:#7ee787}table.mini td.err{color:#ff7b72}
  table.mini td code{color:#a5d6ff;font-size:11px}
  table.mini td.ms{color:#7d8590}
  .seg-t{display:inline-block;font-size:10.5px;color:#7d8590;letter-spacing:.6px;margin-bottom:4px;font-family:ui-monospace,Menlo,monospace}
  .footer{margin-top:24px;font-size:11.5px;color:#9aa3af;text-align:right}
</style></head><body>
<h1>${esc(spec.title)} · ${esc(group)}</h1>
<div class="meta">
  模块 <code>${esc(MODULE)}</code> · 端口 <code>${PORT}</code> · 启动时间 ${esc(startedAt)}<br>
  下列请求与响应均由脚本实际发起（<code>java -jar</code> 真实进程 + 真实 HTTP 调用），非示意数据。
</div>
<table>
  <thead><tr><th>方法</th><th>路径</th><th>用例</th><th>状态</th></tr></thead>
  <tbody>${rows}</tbody>
</table>
${consoleBlock}
${results.map(caseCard).join('')}
<div class="footer">backend-learning · ${esc(MODULE)}</div>
</body></html>`;
}

/* ---------------- 控制台日志片段 ---------------- */

const ANSI = /\u001b\[[0-9;]*m/g;

function pickConsoleLines() {
  const cfg = spec.console;
  if (!cfg?.include?.length) return [];
  const lines = procLog.map((l) => l.replace(ANSI, '').trimEnd()).filter(Boolean);
  const hit = lines.filter((l) => cfg.include.some((k) => l.includes(k)));
  return cfg.limit ? hit.slice(-cfg.limit) : hit;
}

/* ---------------- OpenAPI ---------------- */
function inferSchema(body) {
  try {
    const v = JSON.parse(body);
    return schemaOf(v);
  } catch {
    return { type: 'string' };
  }
}

function schemaOf(v) {
  if (v === null) return { nullable: true };
  if (Array.isArray(v)) return { type: 'array', items: v.length ? schemaOf(v[0]) : {} };
  if (typeof v === 'object') {
    return {
      type: 'object',
      properties: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, schemaOf(x)])),
    };
  }
  if (typeof v === 'number') return Number.isInteger(v) ? { type: 'integer' } : { type: 'number' };
  if (typeof v === 'boolean') return { type: 'boolean' };
  return { type: 'string' };
}

function buildOpenapi(results) {
  const paths = {};
  const tags = new Set();
  for (const c of spec.cases) {
    const r = results.find((x) => x.id === c.id);
    if (!r) continue;
    const def = c.openapi ?? {};
    const tag = def.tag ?? spec.tag ?? '默认';
    tags.add(tag);
    const p = r.request.url.replace(BASE, '').split('?')[0];
    const method = r.request.method.toLowerCase();
    paths[p] = paths[p] ?? {};
    const status = String(r.response.status);
    // 文档类端点（/v3/api-docs）返回的本身就是 OpenAPI 描述，再推断成 schema 只会产生一层套一层的噪音
    const isDocEndpoint = /^\/v3\//.test(p) || p.startsWith('/actuator/prometheus');
    const okBody = !isDocEndpoint && inlineable(r) && !r.response.body.startsWith('<') ? r.response.body : null;
    paths[p][method] = {
      tags: [tag],
      summary: def.summary ?? c.name,
      description: c.desc,
      operationId: c.id,
      parameters: def.parameters ?? [],
      ...(r.request.body && !r.request.body.startsWith('(')
        ? { requestBody: { required: true, content: { 'application/json': { schema: inferSchema(r.request.body) } } } }
        : {}),
      responses: {
        [status]: {
          description: def.responseDesc ?? '实际返回',
          ...(okBody ? { content: { 'application/json': { schema: inferSchema(okBody) } } } : {}),
        },
        ...(def.errors ?? {}),
      },
    };
  }
  return {
    openapi: '3.0.3',
    info: {
      title: spec.title,
      version: '1.0.0',
      description: `backend-learning · ${MODULE}。由 tools/run-cases.mjs 依据实际运行的接口生成。`,
    },
    servers: [{ url: BASE }],
    tags: [...tags].map((t) => ({ name: t })),
    paths,
  };
}

/* ---------------- main ---------------- */

if (spec.resetRedis) {
  const { host = '127.0.0.1', port = 6379, password, db = 0 } = spec.resetRedis;
  const cliArgs = ['-h', host, '-p', String(port), '-n', String(db)];
  if (password) cliArgs.push('-a', password, '--no-auth-warning');
  cliArgs.push('flushdb');
  try {
    await sh('redis-cli', cliArgs);
    log(`已清空 Redis db${db}`);
  } catch (e) {
    log(`Redis 清理失败：${e.message.slice(0, 120)}`);
  }
}

const jar = await build();
const startedAt = new Date().toLocaleString('zh-CN', { hour12: false });
const proc = await start(jar);

let results = [];
try {
  for (const c of spec.cases) {
    try {
      const r = await runCase(c);
      results.push(r);
      log(`  ✓ ${r.request.method} ${r.request.url.replace(BASE, '')} -> ${r.response.status}`);
    } catch (e) {
      log(`  ✗ ${c.id}: ${e.message}`);
      results.push({
        id: c.id,
        name: c.name,
        desc: c.desc,
        group: c.group ?? '接口验证',
        curl: '',
        request: { method: c.req?.method ?? 'GET', url: BASE + (c.req?.path ?? ''), headers: {} },
        response: { status: 0, headers: {}, body: `请求失败: ${e.message}` },
        ms: 0,
      });
    }
  }

  const groups = [...new Set(results.map((r) => r.group))];
  const consoleLines = pickConsoleLines();
  for (const [i, g] of groups.entries()) {
    const subset = results.filter((r) => r.group === g);
    const html = renderGroup(g, subset, startedAt, consoleLines);
    const slug = spec.groupSlug?.[g] ?? String(i + 1);
    const htmlPath = path.join(OUT_RAW, `${SLUG}-${slug}.html`);
    writeFileSync(htmlPath, html);
    const png = path.join(OUT_SHOTS, `${SLUG}-${slug}.webp`);
    const size = await screenshot(htmlPath, png);
    log(`  图 ${path.relative(ROOT, png)}  ${size.width}×${size.height}`);
  }

  writeFileSync(path.join(OUT_API, `${SLUG}.openapi.json`), JSON.stringify(buildOpenapi(results), null, 2));
  writeFileSync(path.join(OUT_RAW, `${SLUG}.report.json`), JSON.stringify({ module: MODULE, startedAt, results }, null, 2));

  const failed = results.filter((r) => {
    if (r.parallel) return false;
    const expect = spec.cases.find((c) => c.id === r.id)?.expect;
    if (r.response.status === 0) return true;
    if (expect === undefined) return r.response.status >= 400;
    return !(Array.isArray(expect) ? expect : [expect]).includes(r.response.status);
  });
  log(`完成：${results.length} 个用例，异常 ${failed.length} 个`);
  if (failed.length) process.exitCode = 2;
} finally {
  if (!KEEP) {
    proc.kill('SIGTERM');
    await sleep(1200);
    if (proc.exitCode === null) proc.kill('SIGKILL');
  } else {
    log(`进程保留，PID ${proc.pid}，端口 ${PORT}`);
  }
}
