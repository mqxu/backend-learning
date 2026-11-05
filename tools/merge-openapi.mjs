#!/usr/bin/env node
/**
 * 把各模块的 OpenAPI 描述合成一份，并生成导入说明。
 *
 *   node tools/merge-openapi.mjs
 *
 * 各模块是独立的服务、各占一个端口，路径难免重复（比如 04 与 05 都有 /users）。
 * 合并时给冲突路径加上模块前缀，原始路径写进 operation 的 description 里，
 * 这样在一份文档里也能看懂"它原本是哪个服务的哪个路径"。
 */
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const API_DIR = path.join(ROOT, 'docs/apifox');

const files = readdirSync(API_DIR)
  .filter((f) => f.endsWith('.openapi.json'))
  .sort();

const merged = {
  openapi: '3.0.3',
  info: {
    title: 'backend-learning 全部模块接口',
    version: '1.0.0',
    description: [
      '由 tools/merge-openapi.mjs 从各模块的实际运行结果生成。',
      '每个模块是独立服务，各占一个端口，servers 里按端口区分。',
      '路径重复的接口已加模块前缀，原始路径写在接口描述里。',
    ].join('\n'),
  },
  servers: [],
  tags: [],
  paths: {},
};

const summary = [];

for (const file of files) {
  const spec = JSON.parse(readFileSync(path.join(API_DIR, file), 'utf8'));
  const module = file.replace('.openapi.json', '');
  const port = spec.servers?.[0]?.url?.split(':').pop() ?? '?';
  const ops = Object.values(spec.paths ?? {}).reduce((n, item) => n + Object.keys(item).length, 0);

  summary.push({ module, port, ops, file });

  merged.servers.push({ url: spec.servers[0].url, description: `${module}（端口 ${port}）` });

  for (const [p, item] of Object.entries(spec.paths ?? {})) {
    for (const [method, op] of Object.entries(item)) {
      const tag = `${module} ${op.tags?.[0] ?? ''}`.trim();
      if (!merged.tags.some((t) => t.name === tag)) {
        merged.tags.push({ name: tag, description: `${module} 模块，端口 ${port}` });
      }
      // 路径冲突时加模块前缀，避免互相覆盖
      const target = merged.paths[p]?.[method] ? `/${module}${p}` : p;
      merged.paths[target] = merged.paths[target] ?? {};
      merged.paths[target][method] = {
        ...op,
        tags: [tag],
        description: `${op.description ?? op.summary ?? ''}\n\n原始地址：${spec.servers[0].url}${p}（${method.toUpperCase()}）`.trim(),
      };
    }
  }
}

writeFileSync(path.join(API_DIR, 'all.openapi.json'), JSON.stringify(merged, null, 2));

const rows = summary
  .map((s) => `| \`${s.module}\` | ${s.port} | ${s.ops} | [${s.file}](${s.file}) |`)
  .join('\n');

const md = `# 接口清单（可导入 Apifox）

这里的 JSON 不是手写的，是 \`tools/run-cases.mjs\` 启动每个模块、发真实请求之后生成的 OpenAPI 描述。
接口变了重新跑一遍脚本即可，不用手工维护。

## 导入 Apifox

两种方式，按需要选：

**按模块导入（推荐）**

每个模块是独立服务、各占一个端口，分开导入到不同目录，调试时不会串环境。

1. Apifox 里新建项目，进入「接口管理」
2. 右上角「导入数据」→ 选「OpenAPI / Swagger」
3. 选「URL 导入」填运行中服务的地址，或直接选「文件导入」上传下面的 JSON
4. 导入模式选「覆盖」，重复导入不会产生重复接口

**一次性导入全部**

用 \`all.openapi.json\`，所有模块合成一份文档，按「模块 + 标签」分组。
路径重复的接口已加模块前缀（如 \`/05-mybatis/users\`），原始地址写在接口描述里。

## 模块清单

| 模块 | 端口 | 接口数 | 文件 |
| --- | --- | --- | --- |
${rows}

合计 ${summary.reduce((n, s) => n + s.ops, 0)} 个接口。

## 重新生成

\`\`\`bash
# 单个模块：启动服务 → 发真实请求 → 出截图与 openapi
node tools/run-cases.mjs 05-mybatis

# 合并成一份
node tools/merge-openapi.mjs
\`\`\`

## 说明

- 请求示例、响应结构都来自实际调用，鉴权相关的接口需要先登录拿 token（脚本里已按顺序处理）
- \`resetRedis\` 类的模块每次运行前会清空对应的 Redis db，避免上一次的数据干扰
- 截图在 \`docs/public/shots/\`，每张图对应一个功能分组
`;

writeFileSync(path.join(API_DIR, 'README.md'), md);
console.log(`合并完成：${files.length} 个模块，${summary.reduce((n, s) => n + s.ops, 0)} 个接口`);
console.log(`输出：docs/apifox/all.openapi.json、docs/apifox/README.md`);
