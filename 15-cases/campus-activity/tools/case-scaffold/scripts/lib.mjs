#!/usr/bin/env node
/**
 * case-scaffold 共用工具：仓库定位、模块枚举、端口解析、Markdown 表格操作。
 *
 * 只做零依赖的静态处理，不启动 Maven、不连数据库。
 */

import fs from 'node:fs';
import path from 'node:path';

export const SKIP_DIRS = new Set([
  'node_modules', 'target', 'dist', 'build', '.git', '.idea', '.vscode',
  '.pnpm-store', 'out', 'uploads',
]);

export function die(message, code = 1) {
  console.error(`\x1b[31m✗ ${message}\x1b[0m`);
  process.exit(code);
}

export function info(message) {
  console.log(message);
}

export function ok(message) {
  console.log(`\x1b[32m✓\x1b[0m ${message}`);
}

export function warn(message) {
  console.log(`\x1b[33m!\x1b[0m ${message}`);
}

export function fail(message) {
  console.log(`\x1b[31m✗\x1b[0m ${message}`);
}

/** 从 start 向上找 backend-learning 仓库根：同时有父 POM 与 01-quickstart 目录。 */
export function findRepoRoot(start = process.cwd()) {
  let current = path.resolve(start);
  for (let i = 0; i < 8; i += 1) {
    const pom = path.join(current, 'pom.xml');
    const quickstart = path.join(current, '01-quickstart');
    if (fs.existsSync(pom) && fs.existsSync(quickstart)) {
      const pomText = fs.readFileSync(pom, 'utf8');
      if (pomText.includes('<artifactId>backend-learning</artifactId>')) {
        return current;
      }
    }
    const parent = path.dirname(current);
    if (parent === current) {
      break;
    }
    current = parent;
  }
  return null;
}

export function readText(file) {
  return fs.readFileSync(file, 'utf8');
}

export function readJson(file) {
  return JSON.parse(readText(file));
}

export function exists(file) {
  return fs.existsSync(file);
}

export function isDir(file) {
  try {
    return fs.statSync(file).isDirectory();
  } catch {
    return false;
  }
}

/** 递归列出文件，默认跳过依赖与构建目录。 */
export function walkFiles(dir, { maxDepth = 12 } = {}) {
  const out = [];
  const walk = (current, depth) => {
    if (depth > maxDepth) {
      return;
    }
    let entries;
    try {
      entries = fs.readdirSync(current, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      if (entry.name.startsWith('.') && entry.name !== '.github') {
        continue;
      }
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        if (SKIP_DIRS.has(entry.name)) {
          continue;
        }
        walk(full, depth + 1);
      } else {
        out.push(full);
      }
    }
  };
  walk(dir, 0);
  return out;
}

/** 解析 POM 里的 <module> 列表。 */
export function readPomModules(pomFile) {
  if (!exists(pomFile)) {
    return [];
  }
  const text = readText(pomFile);
  return [...text.matchAll(/<module>\s*([^<]+?)\s*<\/module>/g)].map((m) => m[1].trim());
}

/**
 * 取 application*.yml 里的顶层 server.port。
 * 只认 server: 下的第一个 port，忽略 app.port、datasource.port 等嵌套配置。
 */
export function parseServerPort(text) {
  const lines = text.split(/\r?\n/);
  for (let i = 0; i < lines.length; i += 1) {
    if (!/^server:\s*(#.*)?$/.test(lines[i])) {
      continue;
    }
    for (let j = i + 1; j < lines.length && j < i + 10; j += 1) {
      if (/^\S/.test(lines[j])) {
        break;
      }
      const match = lines[j].match(/^\s+port:\s*(\d{2,5})\s*(#.*)?$/);
      if (match) {
        return Number(match[1]);
      }
    }
  }
  return null;
}

/** 枚举模块：根目录 NN-topic 教学模块 + 15-cases/* 综合案例。 */
export function listModules(root) {
  const modules = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    if (!entry.isDirectory() || !/^\d{2}-[a-z0-9]/.test(entry.name)) {
      continue;
    }
    const dir = path.join(root, entry.name);
    if (entry.name === '15-cases') {
      continue;
    }
    if (!exists(path.join(dir, 'pom.xml'))) {
      continue;
    }
    modules.push({
      name: entry.name,
      rel: entry.name,
      dir,
      kind: 'module',
      standalone: false,
    });
  }

  const casesRoot = path.join(root, '15-cases');
  if (isDir(casesRoot)) {
    for (const entry of fs.readdirSync(casesRoot, { withFileTypes: true })) {
      if (!entry.isDirectory() || entry.name.startsWith('.')) {
        continue;
      }
      const dir = path.join(casesRoot, entry.name);
      if (exists(path.join(dir, 'pom.xml'))) {
        modules.push({
          name: entry.name,
          rel: `15-cases/${entry.name}`,
          dir,
          kind: 'case',
          standalone: false,
        });
      } else if (exists(path.join(dir, 'server/pom.xml'))) {
        modules.push({
          name: entry.name,
          rel: `15-cases/${entry.name}`,
          dir,
          kind: 'case',
          standalone: true,
        });
      }
    }
  }

  modules.sort((a, b) => a.rel.localeCompare(b.rel, 'zh-CN', { numeric: true }));
  return modules;
}

/** 模块下的 application*.yml（跳过 target）。 */
export function findApplicationYamls(moduleDir) {
  return walkFiles(moduleDir, { maxDepth: 9 })
    .filter((file) => /application(-[a-z0-9-]+)?\.ya?ml$/i.test(path.basename(file)))
    .filter((file) => !file.includes(`${path.sep}target${path.sep}`));
}

/** 模块实际使用的端口集合（一个模块可能有多环境覆盖，取并集）。 */
export function modulePorts(moduleDir) {
  const ports = new Set();
  for (const file of findApplicationYamls(moduleDir)) {
    const port = parseServerPort(readText(file));
    if (port) {
      ports.add(port);
    }
  }
  return [...ports];
}

/** 解析根 README 的端口表：| 模块 | 端口 | 外部依赖 |。 */
export function readReadmePorts(root) {
  const readme = path.join(root, 'README.md');
  if (!exists(readme)) {
    return [];
  }
  const rows = [];
  for (const line of readText(readme).split(/\r?\n/)) {
    const match = line.match(/^\|\s*([A-Za-z0-9._/-]+)\s*\|\s*(\d{2,5})\s*\|/);
    if (match) {
      rows.push({ module: match[1].trim(), port: Number(match[2]) });
    }
  }
  return rows;
}

/** 解析一行里的本地 Markdown 链接。 */
export function markdownLinks(line) {
  return [...line.matchAll(/\[([^\]]+)\]\(([^)]+)\)/g)]
    .map((m) => ({ text: m[1], href: m[2].trim() }))
    .filter((link) => !/^(https?:|mailto:|#)/.test(link.href));
}

/** 解析 Markdown 里的本地图片引用（Markdown 语法 + <img src>）。 */
export function imageRefs(markdown) {
  const refs = [];
  for (const match of markdown.matchAll(/!\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g)) {
    refs.push(match[1]);
  }
  for (const match of markdown.matchAll(/<img[^>]+src=["']([^"']+)["']/g)) {
    refs.push(match[1]);
  }
  return refs.filter((ref) => !/^(https?:|data:)/.test(ref));
}

/** 把 {{VAR}} 替换成 vars[VAR]；未提供的保持原样，便于发现漏配。 */
export function renderTemplate(text, vars) {
  return text.replace(/\{\{([A-Z0-9_]+)\}\}/g, (raw, name) => {
    if (Object.hasOwn(vars, name)) {
      return String(vars[name]);
    }
    return raw;
  });
}

/**
 * 在 Markdown 表格末尾插入一行。
 * header 用正则匹配表头行；返回新内容与是否插入成功。
 */
export function appendTableRow(markdown, headerPattern, row, options = {}) {
  const lines = markdown.split(/\r?\n/);
  const headerIndex = lines.findIndex((line) => headerPattern.test(line));
  if (headerIndex === -1) {
    return { content: markdown, inserted: false };
  }
  let last = headerIndex;
  for (let i = headerIndex + 1; i < lines.length; i += 1) {
    if (!/^\s*\|/.test(lines[i])) {
      break;
    }
    last = i;
  }
  let insertAt = last + 1;
  if (options.beforeRowPattern) {
    for (let i = headerIndex + 1; i <= last; i += 1) {
      if (options.beforeRowPattern.test(lines[i])) {
        insertAt = i;
        break;
      }
    }
  }
  lines.splice(insertAt, 0, row);
  return { content: lines.join('\n'), inserted: true };
}

/** 生成 POM 的 module 条目，插到 </modules> 前。 */
export function insertPomModule(pomText, modulePath) {
  if (pomText.includes(`<module>${modulePath}</module>`)) {
    return { content: pomText, inserted: false };
  }
  if (!pomText.includes('</modules>')) {
    return { content: pomText, inserted: false };
  }
  const content = pomText.replace(
    /(\n[ \t]*)(<\/modules>)/,
    `$1    <module>${modulePath}</module>$1$2`,
  );
  return { content, inserted: content !== pomText };
}

const CJK = /[\u4e00-\u9fa5]/;

/** 生成用于导航的标题文本。 */
export function sanitizeCell(value) {
  return String(value).replace(/\|/g, '\\|').trim();
}

export function hasChinese(text) {
  return CJK.test(text);
}
