#!/usr/bin/env node
/**
 * new-case.mjs —— 为 backend-learning 仓库生成教学模块或综合案例骨架。
 *
 * 用法：
 *   node tools/case-scaffold/scripts/new-case.mjs 16-comments --port 8018 --title "评论"
 *   node tools/case-scaffold/scripts/new-case.mjs 15-cases/notes --port 8019 --title "随手笔记"
 *
 * 设计原则：只生成骨架 + 登记导航，不写业务实现；所有事实性内容留给作者补。
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  appendTableRow,
  die,
  exists,
  findRepoRoot,
  info,
  insertPomModule,
  listModules,
  modulePorts,
  ok,
  readJson,
  readReadmePorts,
  readText,
  renderTemplate,
  sanitizeCell,
  walkFiles,
  warn,
} from './lib.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const TEMPLATES = path.join(__dirname, '..', 'templates');

function parseArgs(argv) {
  const opts = { _: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (!arg.startsWith('--')) {
      opts._.push(arg);
      continue;
    }
    const key = arg.slice(2);
    const next = argv[i + 1];
    if (next !== undefined && !next.startsWith('--')) {
      opts[key] = next;
      i += 1;
    } else {
      opts[key] = true;
    }
  }
  return opts;
}

function pascalCase(slug) {
  return slug
    .split('-')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('');
}

function nextModuleNumber(root) {
  let max = 0;
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    if (!entry.isDirectory()) {
      continue;
    }
    const match = entry.name.match(/^(\d{2})-/);
    if (match) {
      max = Math.max(max, Number(match[1]));
    }
  }
  return max + 1;
}

function nextCaseIndex(root) {
  let max = 0;
  for (const file of [path.join(root, 'README.md'), path.join(root, 'docs/index.md')]) {
    if (!exists(file)) {
      continue;
    }
    for (const line of readText(file).split(/\r?\n/)) {
      const match = line.match(/^\|\s*15-(\d+)\s*\|/);
      if (match) {
        max = Math.max(max, Number(match[1]));
      }
    }
  }
  return max + 1;
}

function collectUsedPorts(root) {
  const used = new Map(); // port -> owner
  for (const module of listModules(root)) {
    for (const port of modulePorts(module.dir)) {
      if (!used.has(port)) {
        used.set(port, module.rel);
      }
    }
  }
  for (const row of readReadmePorts(root)) {
    if (!used.has(row.port)) {
      used.set(row.port, `README:${row.module}`);
    }
  }
  for (const file of walkFiles(path.join(root, 'tools/cases'), { maxDepth: 4 })) {
    if (!file.endsWith('.json')) {
      continue;
    }
    try {
      const data = readJson(file);
      if (typeof data.port === 'number' && !used.has(data.port)) {
        used.set(data.port, `tools/cases:${path.basename(file)}`);
      }
    } catch {
      // 用例文件坏了不阻塞脚手架，check-module 会单独报
    }
  }
  return used;
}

function freePort(used) {
  for (let port = 8015; port <= 8099; port += 1) {
    if (!used.has(port) && port !== 8080) {
      return port;
    }
  }
  return 8099;
}

function readTemplate(relative) {
  return readText(path.join(TEMPLATES, relative));
}

function resolveTarget(root, rawName, opts) {
  if (rawName.startsWith('15-cases/')) {
    const slug = rawName.slice('15-cases/'.length);
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug)) {
      die(`案例名不合法：${slug}。只允许小写字母、数字与连字符。`);
    }
    return { kind: 'case', modulePath: `15-cases/${slug}`, slug };
  }

  if (opts.kind === 'case') {
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(rawName)) {
      die(`案例名不合法：${rawName}。只允许小写字母、数字与连字符。`);
    }
    return { kind: 'case', modulePath: `15-cases/${rawName}`, slug: rawName };
  }

  const match = rawName.match(/^(\d{2})-([a-z0-9]+(?:-[a-z0-9]+)*)$/);
  if (!match) {
    die(`模块名不合法：${rawName}。格式应为 16-comments 或 15-cases/notes。`);
  }
  const number = Number(match[1]);
  if (number === 15) {
    die('15 是案例段。新增案例请用 15-cases/<name>，教学模块从 16 开始。');
  }
  return { kind: 'module', modulePath: rawName, slug: match[2], number };
}

function buildVars(root, target, opts) {
  const { kind, modulePath, slug } = target;
  const caseName = modulePath.split('/').pop();
  const artifact = kind === 'case' ? caseName : modulePath;

  let navIndex;
  if (kind === 'case') {
    navIndex = `15-${nextCaseIndex(root)}`;
  } else {
    navIndex = target.number !== undefined
      ? String(target.number).padStart(2, '0')
      : String(nextModuleNumber(root)).padStart(2, '0');
  }

  const tutorialPath = kind === 'case'
    ? `docs/15-${caseName}.md`
    : `docs/${navIndex}-${slug}.md`;
  const tutorialLink = kind === 'case'
    ? `../../${tutorialPath}`
    : `../${tutorialPath}`;
  const caseJsonPath = kind === 'case'
    ? `tools/cases/15-cases/${caseName}.json`
    : `tools/cases/${modulePath}.json`;

  const title = opts.title || slug;
  const desc = opts.desc || `${title}模块的骨架，已生成目录、接口用例与教程占位。`;
  const packageName = opts.package || `top.mqxu.${slug.replace(/-/g, '')}`;
  const appClass = `${pascalCase(slug)}Application`;

  const vars = {
    MODULE: modulePath,
    ARTIFACT: artifact,
    PARENT_ARTIFACT: kind === 'case' ? '15-cases' : 'backend-learning',
    PACKAGE: packageName,
    PACKAGE_PATH: packageName.replace(/\./g, '/'),
    APP_CLASS: appClass,
    PORT: String(opts.port),
    TITLE: title,
    SUBTITLE: opts.subtitle || '从零到可验证',
    DESC: desc,
    TAG: opts.tag || title,
    THEME: opts.theme || title,
    APP_NAME: `${navIndex}-${title}`,
    NAV_INDEX: navIndex,
    TUTORIAL: tutorialPath,
    TUTORIAL_LINK: tutorialLink,
    TUTORIAL_BASENAME: path.basename(tutorialPath),
    CASE_JSON_PATH: caseJsonPath,
    KIND: kind === 'case' ? '案例' : '模块',
    AUTHOR: opts.author || 'mqxu',
    DATE: new Date().toISOString().slice(0, 10),
  };
  return { vars, navIndex, tutorialPath, caseJsonPath, artifact, kind, modulePath, slug };
}

function plannedFiles(root, target, meta) {
  const { vars } = meta;
  const base = path.join(root, target.modulePath);
  const files = [
    {
      destination: path.join(base, 'pom.xml'),
      template: 'module/pom.xml.tmpl',
    },
    {
      destination: path.join(base, 'src/main/resources/application.yml'),
      template: 'module/application.yml.tmpl',
    },
    {
      destination: path.join(base, `src/main/java/${vars.PACKAGE_PATH}/${vars.APP_CLASS}.java`),
      template: 'module/Application.java.tmpl',
    },
    {
      destination: path.join(base, `src/main/java/${vars.PACKAGE_PATH}/controller/ModuleInfoController.java`),
      template: 'module/ModuleInfoController.java.tmpl',
    },
    {
      destination: path.join(base, `src/test/java/${vars.PACKAGE_PATH}/${vars.APP_CLASS}Tests.java`),
      template: 'module/ApplicationTests.java.tmpl',
    },
    {
      destination: path.join(base, 'README.md'),
      template: 'module/README.md.tmpl',
    },
    {
      destination: path.join(root, meta.tutorialPath),
      template: 'docs/tutorial.md.tmpl',
    },
    {
      destination: path.join(root, meta.caseJsonPath),
      template: 'cases/module.json.tmpl',
    },
  ];
  return files;
}

function planPatches(root, target, meta, opts) {
  const { kind, modulePath, slug } = target;
  const { vars, navIndex, tutorialPath } = meta;
  const patches = [];

  const pomFile = kind === 'case' ? path.join(root, '15-cases/pom.xml') : path.join(root, 'pom.xml');
  const pomEntry = kind === 'case' ? slug : modulePath;
  patches.push({
    file: pomFile,
    label: `POM <module>${pomEntry}</module>`,
    apply: (text) => insertPomModule(text, pomEntry),
  });

  const readmeFile = path.join(root, 'README.md');
  const navRow = kind === 'case'
    ? `| ${navIndex} | [${slug}](${modulePath}) | 综合案例：${vars.TITLE} | ✅ 完成 | [教程](${tutorialPath}) |`
    : `| ${navIndex} | [${modulePath}](${modulePath}/README.md) | ${vars.TITLE} | ✅ 完成 | [教程](${tutorialPath}) |`;
  patches.push({
    file: readmeFile,
    label: `README 模块导航追加 ${navIndex}`,
    apply: (text) => appendTableRow(
      text,
      /^\|\s*序号\s*\|\s*模块\s*\|\s*主题\s*\|/,
      navRow,
      kind === 'module' ? { beforeRowPattern: /^\|\s*15-\d+\s*\|/ } : {},
    ),
  });

  const portRow = `| ${modulePath} | ${vars.PORT} | ${sanitizeCell(opts.deps || '无')} |`;
  patches.push({
    file: readmeFile,
    label: `README 端口表追加 ${vars.PORT}`,
    apply: (text) => appendTableRow(
      text,
      /^\|\s*模块\s*\|\s*端口\s*\|\s*外部依赖\s*\|/,
      portRow,
      kind === 'module' ? { beforeRowPattern: /^\|\s*15-cases\// } : {},
    ),
  });

  const indexFile = path.join(root, 'docs/index.md');
  const indexRow = kind === 'case'
    ? `| ${navIndex} | [${vars.TITLE}](/${path.basename(tutorialPath, '.md')}) | ${sanitizeCell(vars.THEME)} |`
    : `| ${navIndex} | [${vars.TITLE}](/${modulePath}) | ${sanitizeCell(vars.THEME)} |`;
  patches.push({
    file: indexFile,
    label: `docs/index.md 导航追加 ${navIndex}`,
    apply: (text) => appendTableRow(
      text,
      kind === 'case'
        ? /^\|\s*序号\s*\|\s*案例\s*\|\s*主题\s*\|/
        : /^\|\s*序号\s*\|\s*模块\s*\|\s*主题\s*\|/,
      indexRow,
    ),
  });

  patches.push({
    file: indexFile,
    label: 'docs/index.md hero 模块数量',
    apply: (text) => {
      const match = text.match(/(\d+)\s*个技术模块加\s*(\d+)\s*个综合案例/);
      if (!match) {
        return { content: text, inserted: false };
      }
      const modules = Number(match[1]) + (kind === 'module' ? 1 : 0);
      const cases = Number(match[2]) + (kind === 'case' ? 1 : 0);
      const next = text.replace(match[0], `${modules} 个技术模块加 ${cases} 个综合案例`);
      return { content: next, inserted: next !== text };
    },
  });

  return patches;
}

function applyPatches(patches, dryRun) {
  let changed = 0;
  for (const patch of patches) {
    if (!exists(patch.file)) {
      warn(`跳过登记：文件不存在 ${path.relative(process.cwd(), patch.file)}`);
      continue;
    }
    const before = readText(patch.file);
    const result = patch.apply(before);
    if (!result.inserted || result.content === before) {
      warn(`跳过登记：${patch.label}（已存在或表头未找到）`);
      continue;
    }
    if (!dryRun) {
      fs.writeFileSync(patch.file, result.content, 'utf8');
    }
    ok(`${dryRun ? '[dry-run] ' : ''}${patch.label}`);
    changed += 1;
  }
  return changed;
}

function main() {
  const opts = parseArgs(process.argv.slice(2));
  const rawName = opts._[0];

  if (!rawName || opts.help || opts.h) {
    console.log('用法：node new-case.mjs <16-comments|15-cases/notes> --port 8018 [--title 评论] [--desc 一句话]');
    console.log('参数：--root --package --tag --subtitle --theme --deps --dry-run --no-register --force');
    process.exit(0);
  }

  const root = opts.root ? path.resolve(opts.root) : findRepoRoot(process.cwd());
  if (!root) {
    die('找不到 backend-learning 仓库根。请在仓库内执行，或用 --root 指定。');
  }
  if (!exists(path.join(root, 'pom.xml')) || !exists(path.join(root, 'README.md'))) {
    die(`${root} 不像仓库根：缺 pom.xml 或 README.md。`);
  }

  const target = resolveTarget(root, rawName, opts);
  if (target.kind === 'module') {
    const suggested = nextModuleNumber(root);
    if (target.number !== undefined && target.number > suggested) {
      warn(`编号 ${target.number} 跳过了 ${String(suggested).padStart(2, '0')}，确认是有意留空。`);
    }
  }
  const port = Number(opts.port);
  if (!Number.isInteger(port) || port < 1024 || port > 65535) {
    const used = collectUsedPorts(root);
    die(`--port 必填，且为 1024–65535 的整数。当前仓库空闲端口建议：${freePort(used)}`);
  }

  const usedPorts = collectUsedPorts(root);
  if (usedPorts.has(port)) {
    const owner = usedPorts.get(port);
    if (!opts.force) {
      die(`端口 ${port} 已被占用（${owner}）。换一个，或用 --force 强行继续。`);
    }
    warn(`端口 ${port} 已被 ${owner} 占用，--force 继续。`);
  }

  const moduleDir = path.join(root, target.modulePath);
  if (exists(moduleDir) && !opts.force) {
    die(`目录已存在：${target.modulePath}。换名字，或用 --force 覆盖（只覆盖模板生成的文件）。`);
  }

  const meta = buildVars(root, target, { ...opts, port });
  const files = plannedFiles(root, target, meta);

  const existing = files.filter((file) => exists(file.destination));
  if (existing.length > 0 && !opts.force) {
    die(`以下文件已存在，换名字或加 --force：\n${existing.map((file) => `  - ${path.relative(root, file.destination)}`).join('\n')}`);
  }

  info(`仓库根：${root}`);
  info(`类型：${target.kind === 'case' ? '综合案例' : '教学模块'}，路径：${target.modulePath}，端口：${port}`);
  info(`导航编号：${meta.navIndex}，教程：${meta.tutorialPath}`);
  console.log('');

  for (const file of files) {
    const content = renderTemplate(readTemplate(file.template), meta.vars);
    if (opts['dry-run']) {
      ok(`[dry-run] 生成 ${path.relative(root, file.destination)}`);
      continue;
    }
    fs.mkdirSync(path.dirname(file.destination), { recursive: true });
    fs.writeFileSync(file.destination, content, 'utf8');
    ok(`生成 ${path.relative(root, file.destination)}`);
  }

  if (!opts['no-register']) {
    const patches = planPatches(root, target, meta, { ...opts, port });
    applyPatches(patches, opts['dry-run']);
  } else {
    warn('--no-register：跳过 POM 与导航登记，记得手工补。');
  }

  console.log('');
  info('下一步：');
  info(`  1. 补 ${meta.tutorialPath} 与 ${target.modulePath}/README.md 的内容`);
  info(`  2. mvn -pl ${target.modulePath} -am -DskipTests compile`);
  info(`  3. node tools/run-cases.mjs ${target.modulePath}`);
  info(`  4. node tools/case-scaffold/scripts/check-module.mjs --module ${target.modulePath}`);
}

main();
