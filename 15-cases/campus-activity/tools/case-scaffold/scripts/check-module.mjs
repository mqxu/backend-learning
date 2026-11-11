#!/usr/bin/env node
/**
 * check-module.mjs —— 仓库结构与口径自检。
 *
 * 检查：端口冲突、聚合 POM、根导航链接、模块五件套、教程图片/章节、case JSON。
 * 只做静态检查；编译与实跑仍需 mvn / run-cases。
 *
 * 用法：
 *   node tools/case-scaffold/scripts/check-module.mjs
 *   node tools/case-scaffold/scripts/check-module.mjs --module 16-comments
 *   node tools/case-scaffold/scripts/check-module.mjs --strict --json
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  die,
  findApplicationYamls,
  findRepoRoot,
  imageRefs,
  listModules,
  markdownLinks,
  modulePorts,
  readJson,
  readPomModules,
  readReadmePorts,
  readText,
  walkFiles,
} from './lib.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const results = [];
function add(level, area, message) {
  results.push({ level, area, message });
}

function parseArgs(argv) {
  const opts = { _: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg.startsWith('--')) {
      const key = arg.slice(2);
      const next = argv[i + 1];
      if (next !== undefined && !next.startsWith('--') && key !== 'strict' && key !== 'json') {
        opts[key] = next;
        i += 1;
      } else {
        opts[key] = true;
      }
    } else {
      opts._.push(arg);
    }
  }
  return opts;
}

function rel(root, file) {
  return path.relative(root, file) || '.';
}

function existsFile(file) {
  return fs.existsSync(file) && fs.statSync(file).isFile();
}

function isDir(file) {
  try {
    return fs.statSync(file).isDirectory();
  } catch {
    return false;
  }
}

/** 找 docs 下所有顶层教程（排除 apifox 子目录与 public）。 */
function topLevelDocs(root) {
  const docsDir = path.join(root, 'docs');
  if (!isDir(docsDir)) {
    return [];
  }
  return fs.readdirSync(docsDir, { withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.endsWith('.md'))
    .map((entry) => path.join(docsDir, entry.name));
}

function checkAggregators(root) {
  const rootPom = path.join(root, 'pom.xml');
  const rootModules = readPomModules(rootPom);
  if (rootModules.length === 0) {
    add('FAIL', 'POM', '根 pom.xml 没有 <modules>');
  }
  for (const name of rootModules) {
    if (!isDir(path.join(root, name))) {
      add('FAIL', 'POM', `根 pom.xml 指向不存在的模块：${name}`);
    }
  }

  const registered = new Set(rootModules);
  for (const module of listModules(root)) {
    if (module.kind !== 'module' || module.standalone) {
      continue;
    }
    if (!registered.has(module.rel) && module.rel !== '15-cases') {
      add('WARN', 'POM', `${module.rel} 未登记到根 pom.xml 的 <modules>`);
    }
  }

  const casesPom = path.join(root, '15-cases/pom.xml');
  if (existsFile(casesPom)) {
    const casesModules = readPomModules(casesPom);
    for (const name of casesModules) {
      if (!isDir(path.join(root, '15-cases', name))) {
        add('FAIL', 'POM', `15-cases/pom.xml 指向不存在的模块：${name}`);
      }
    }
    const registeredCases = new Set(casesModules);
    for (const module of listModules(root)) {
      if (module.kind !== 'case') {
        continue;
      }
      if (module.standalone) {
        add('PASS', 'POM', `${module.rel} 是独立工程，未加入聚合（预期）`);
      } else if (!registeredCases.has(module.name)) {
        add('WARN', 'POM', `${module.rel} 未登记到 15-cases/pom.xml 的 <modules>`);
      }
    }
  }
}

function checkPorts(root, modules) {
  const portMap = new Map();
  for (const module of modules) {
    const ports = modulePorts(module.dir);
    if (ports.length === 0) {
      add('WARN', '端口', `${module.rel} 找不到 server.port`);
      continue;
    }
    for (const port of ports) {
      const owners = portMap.get(port) || [];
      if (!owners.includes(module.rel)) {
        owners.push(module.rel);
      }
      portMap.set(port, owners);
    }
  }
  for (const [port, owners] of portMap) {
    if (owners.length > 1) {
      add('FAIL', '端口', `端口 ${port} 被多个模块使用：${owners.join('、')}`);
    }
  }

  const readmePorts = readReadmePorts(root);
  for (const row of readmePorts) {
    const module = modules.find((item) => item.rel === row.module);
    if (!module) {
      continue;
    }
    const ports = modulePorts(module.dir);
    if (ports.length > 0 && !ports.includes(row.port)) {
      add('WARN', '端口', `README 端口表写 ${row.module} = ${row.port}，实际 application.yml 是 ${ports.join('、')}`);
    }
  }
}

function checkCaseJson(root, modules) {
  const casesDir = path.join(root, 'tools/cases');
  if (!isDir(casesDir)) {
    add('FAIL', '用例', '缺少 tools/cases 目录');
    return;
  }
  const files = walkFiles(casesDir, { maxDepth: 4 }).filter((file) => file.endsWith('.json'));
  if (files.length === 0) {
    add('WARN', '用例', 'tools/cases 下没有用例文件');
  }
  const byModule = new Map();
  for (const file of files) {
    let data;
    try {
      data = readJson(file);
    } catch (error) {
      add('FAIL', '用例', `JSON 解析失败：${rel(root, file)}（${error.message}）`);
      continue;
    }
    const name = rel(root, file);
    if (!data.module || !data.title || typeof data.port !== 'number' || !Array.isArray(data.cases)) {
      add('WARN', '用例', `${name} 缺少 module/title/port/cases 字段`);
      continue;
    }
    byModule.set(data.module, data);

    const module = modules.find((item) => item.rel === data.module);
    if (!module) {
      add('WARN', '用例', `${name} 的 module=${data.module} 找不到对应目录`);
    } else {
      const ports = modulePorts(module.dir);
      if (ports.length > 0 && !ports.includes(data.port)) {
        add('WARN', '用例', `${name} 的 port=${data.port} 与 application.yml（${ports.join('、')}）不一致`);
      }
    }

    const ids = new Set();
    for (const testCase of data.cases) {
      if (!testCase.id) {
        add('WARN', '用例', `${name} 有 case 缺 id`);
        continue;
      }
      if (ids.has(testCase.id)) {
        add('WARN', '用例', `${name} 的 case id 重复：${testCase.id}`);
      }
      ids.add(testCase.id);
      if (!testCase.req || !testCase.req.method || !testCase.req.path) {
        add('WARN', '用例', `${name} 的 ${testCase.id} 缺 req.method/req.path`);
      }
      if (testCase.group && data.groupSlug && !Object.hasOwn(data.groupSlug, testCase.group)) {
        add('WARN', '用例', `${name} 的分组「${testCase.group}」不在 groupSlug 里`);
      }
    }
  }

  for (const module of modules) {
    if (!byModule.has(module.rel)) {
      add('WARN', '用例', `${module.rel} 没有 tools/cases 用例`);
    }
  }
}

function findApplicationClass(moduleDir) {
  const javaFiles = walkFiles(moduleDir, { maxDepth: 12 })
    .filter((file) => file.endsWith('Application.java'))
    .filter((file) => !file.includes(`${path.sep}src${path.sep}test${path.sep}`));
  return javaFiles[0] || null;
}

function tutorialFor(module) {
  if (module.kind === 'case') {
    return `docs/15-${module.name}.md`;
  }
  return `docs/${module.name}.md`;
}

function checkModules(root, modules, filter) {
  for (const module of modules) {
    if (filter && module.rel !== filter) {
      continue;
    }
    const required = [
      ['README.md', path.join(module.dir, 'README.md')],
      ['pom.xml', path.join(module.dir, module.standalone ? 'server/pom.xml' : 'pom.xml')],
    ];
    for (const [label, file] of required) {
      if (!existsFile(file)) {
        add('FAIL', '结构', `${module.rel} 缺 ${label}`);
      }
    }

    const appClass = findApplicationClass(module.dir);
    if (!appClass) {
      add('FAIL', '结构', `${module.rel} 找不到 *Application.java`);
    }

    const yamls = findApplicationYamls(module.dir);
    if (yamls.length === 0) {
      add('FAIL', '结构', `${module.rel} 找不到 application*.yml`);
    }

    const tutorial = path.join(root, tutorialFor(module));
    if (!existsFile(tutorial)) {
      add('FAIL', '结构', `${module.rel} 缺教程 ${tutorialFor(module)}`);
    }

    const readme = path.join(module.dir, 'README.md');
    if (existsFile(readme)) {
      const broken = [];
      for (const link of markdownLinks(readText(readme))) {
        const href = link.href.split('#')[0];
        if (!href || /^[a-z]+:/i.test(href)) {
          continue;
        }
        if (!fs.existsSync(path.resolve(module.dir, href))) {
          broken.push(link.href);
        }
      }
      if (broken.length > 0) {
        add('WARN', '链接', `${module.rel}/README.md 有 ${broken.length} 个本地链接失效：${broken.slice(0, 3).join('、')}`);
      }
    }

    const testDirs = [path.join(module.dir, 'src/test'), path.join(module.dir, 'server/src/test')];
    if (!testDirs.some((dir) => isDir(dir))) {
      add('WARN', '结构', `${module.rel} 没有 src/test（CI 里只能编译不能验证）`);
    }
  }
}

function checkRootReadme(root) {
  const readme = path.join(root, 'README.md');
  if (!existsFile(readme)) {
    add('FAIL', '导航', '缺根 README.md');
    return;
  }
  const broken = [];
  for (const link of markdownLinks(readText(readme))) {
    const href = link.href.split('#')[0];
    if (!href || /^[a-z]+:/i.test(href)) {
      continue;
    }
    if (!fs.existsSync(path.resolve(root, href))) {
      broken.push(link.href);
    }
  }
  if (broken.length > 0) {
    add('FAIL', '导航', `根 README 有 ${broken.length} 个本地链接失效：${broken.slice(0, 5).join('、')}`);
  }
}

function checkDocsIndex(root) {
  const index = path.join(root, 'docs/index.md');
  if (!existsFile(index)) {
    add('FAIL', '导航', '缺 docs/index.md');
    return;
  }
  const broken = [];
  for (const link of markdownLinks(readText(index))) {
    const href = link.href.split('#')[0];
    if (!href || /^[a-z]+:/i.test(href)) {
      continue;
    }
    const candidates = [];
    if (href.startsWith('/')) {
      const relPath = href.slice(1);
      candidates.push(
        path.join(root, 'docs', `${relPath}.md`),
        path.join(root, 'docs', relPath),
        path.join(root, 'docs', relPath, 'README.md'),
        path.join(root, 'docs', relPath, 'index.md'),
      );
    } else {
      candidates.push(path.resolve(path.dirname(index), href));
    }
    if (!candidates.some((candidate) => fs.existsSync(candidate))) {
      broken.push(link.href);
    }
  }
  if (broken.length > 0) {
    add('WARN', '导航', `docs/index.md 有 ${broken.length} 个路由找不到对应文件：${broken.slice(0, 5).join('、')}`);
  }
}

function checkTutorials(root, modules, filter) {
  const docs = topLevelDocs(root);
  const tutorials = new Set(
    modules.map((module) => path.join(root, tutorialFor(module))),
  );

  for (const doc of docs) {
    const name = rel(root, doc);
    const text = readText(doc);

    // 所有 docs 都查图片引用，防止教程与总览引用失联
    const broken = [];
    for (const ref of imageRefs(text)) {
      if (!fs.existsSync(path.resolve(path.dirname(doc), ref))) {
        broken.push(ref);
      }
    }
    if (broken.length > 0) {
      add('FAIL', '图片', `${name} 引用不存在的图片：${broken.slice(0, 5).join('、')}`);
    }

    // 章节结构只对模块教程要求；index.md、15-cases.md 等总览页不套模板
    if (!tutorials.has(doc)) {
      continue;
    }
    if (filter) {
      const module = modules.find((item) => item.rel === filter);
      if (module && doc !== path.join(root, tutorialFor(module))) {
        continue;
      }
    }
    if (!/^##\s+0\.\s*先看结果/m.test(text)) {
      add('WARN', '教程', `${name} 没有「## 0. 先看结果」`);
    }
    if (!/^##\s+\d+\.\s*小结/m.test(text) && !/^##\s+小结/m.test(text)) {
      add('WARN', '教程', `${name} 没有「小结」`);
    }
  }
}

function printHuman(filter) {
  const fails = results.filter((item) => item.level === 'FAIL');
  const warns = results.filter((item) => item.level === 'WARN');
  const passes = results.filter((item) => item.level === 'PASS');

  console.log('');
  console.log(`自检完成${filter ? `（模块：${filter}）` : ''}`);
  console.log(`  FAIL ${fails.length}   WARN ${warns.length}   PASS ${passes.length}`);

  for (const item of fails) {
    console.log(`  \x1b[31m✗ ${item.message}\x1b[0m`);
  }
  for (const item of warns) {
    console.log(`  \x1b[33m! ${item.message}\x1b[0m`);
  }
  for (const item of passes) {
    console.log(`  \x1b[32m✓ ${item.message}\x1b[0m`);
  }
  console.log('');
  if (fails.length > 0) {
    console.log('\x1b[31m有 FAIL，先修完再交付。\x1b[0m');
  } else if (warns.length > 0) {
    console.log('\x1b[33m没有 FAIL；WARN 逐条确认是「待补」还是「真问题」。\x1b[0m');
  } else {
    console.log('\x1b[32m全部通过。\x1b[0m');
  }
}

function main() {
  const opts = parseArgs(process.argv.slice(2));
  const root = opts.root ? path.resolve(opts.root) : findRepoRoot(process.cwd());
  if (!root) {
    die('找不到 backend-learning 仓库根。请在仓库内执行，或用 --root 指定。');
  }

  const allModules = listModules(root);
  if (allModules.length === 0) {
    die(`${root} 下没有识别到模块。`);
  }

  let modules = allModules;
  if (opts.module) {
    const found = allModules.find((item) => item.rel === opts.module || item.name === opts.module);
    if (!found) {
      die(`找不到模块：${opts.module}。可选：${allModules.map((item) => item.rel).join('、')}`);
    }
    modules = [found];
  }

  checkAggregators(root);
  checkPorts(root, allModules);
  checkCaseJson(root, allModules);
  checkModules(root, modules, opts.module ? opts.module : null);
  checkRootReadme(root);
  checkDocsIndex(root);
  checkTutorials(root, allModules, opts.module ? opts.module : null);

  const fails = results.filter((item) => item.level === 'FAIL');
  const warns = results.filter((item) => item.level === 'WARN');

  if (opts.json) {
    console.log(JSON.stringify({
      root,
      module: opts.module || null,
      summary: { fail: fails.length, warn: warns.length },
      results,
    }, null, 2));
  } else {
    printHuman(opts.module || null);
  }

  if (fails.length > 0 || (opts.strict && warns.length > 0)) {
    process.exit(1);
  }
}

main();
