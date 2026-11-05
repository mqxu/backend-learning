// 生成小程序 tabBar 图标：24 栅格线性图标，导出 81×81 PNG（正常态灰、选中态蓝）
// 用法：node scripts/gen-tabbar-icons.mjs
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const outDir = join(root, 'app/src/static/tabbar')
mkdirSync(outDir, { recursive: true })

const NORMAL = '#8a8f99'
const ACTIVE = '#1f6feb'
const SIZE = 81

// 线性轮廓：24×24 栅格，1.8 描边，圆头圆角
const outline = (color, body) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="${SIZE}" height="${SIZE}" fill="none" stroke="${color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`

// 实心：同一几何填充，细节用白色挖空
const solid = (color, body) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="${SIZE}" height="${SIZE}" fill="${color}" stroke="none">${body}</svg>`

const house = 'M3.4 10.2 12 3.4l8.6 6.8v8.9a1.4 1.4 0 0 1-1.4 1.4h-4.1v-5.6H8.9v5.6H4.8a1.4 1.4 0 0 1-1.4-1.4z'
const calendarBody = 'M3.2 5.2a2.6 2.6 0 0 1 2.6-2.6h12.4a2.6 2.6 0 0 1 2.6 2.6v13.2a2.6 2.6 0 0 1-2.6 2.6H5.8a2.6 2.6 0 0 1-2.6-2.6z'
const bubble = 'M4 7.4A3.4 3.4 0 0 1 7.4 4h9.2A3.4 3.4 0 0 1 20 7.4v6A3.4 3.4 0 0 1 16.6 16.8H10.6L6.4 20.6v-3.8H7.4A3.4 3.4 0 0 1 4 13.4z'
const personBody = 'M4.6 20.4c0-3.9 3.3-6.2 7.4-6.2s7.4 2.3 7.4 6.2'

const icons = {
  home: {
    normal: outline(NORMAL, `<path d="${house}"/>`),
    active: solid(ACTIVE, `<path d="${house}"/>`)
  },
  activity: {
    normal: outline(
      NORMAL,
      `<rect x="3.2" y="5.2" width="17.6" height="15.8" rx="2.6"/>
       <path d="M8 2.6v3.4M16 2.6v3.4M3.2 9.6h17.6"/>
       <circle cx="8.2" cy="13.8" r="1" fill="${NORMAL}" stroke="none"/>
       <circle cx="12" cy="13.8" r="1" fill="${NORMAL}" stroke="none"/>
       <circle cx="15.8" cy="13.8" r="1" fill="${NORMAL}" stroke="none"/>`
    ),
    active: solid(
      ACTIVE,
      `<path d="${calendarBody}"/>
       <path d="M8 2.2v3.4M16 2.2v3.4" stroke="#fff" stroke-width="1.8" stroke-linecap="round"/>
       <path d="M3.2 9.6h17.6" stroke="#fff" stroke-width="1.6"/>
       <circle cx="8.2" cy="13.8" r="1" fill="#fff"/>
       <circle cx="12" cy="13.8" r="1" fill="#fff"/>
       <circle cx="15.8" cy="13.8" r="1" fill="#fff"/>`
    )
  },
  message: {
    normal: outline(
      NORMAL,
      `<path d="${bubble}"/>
       <circle cx="9.2" cy="10.4" r="1" fill="${NORMAL}" stroke="none"/>
       <circle cx="14.6" cy="10.4" r="1" fill="${NORMAL}" stroke="none"/>`
    ),
    active: solid(
      ACTIVE,
      `<path d="${bubble}"/>
       <circle cx="9.2" cy="10.4" r="1" fill="#fff"/>
       <circle cx="14.6" cy="10.4" r="1" fill="#fff"/>`
    )
  },
  mine: {
    normal: outline(NORMAL, `<circle cx="12" cy="8.2" r="3.7"/><path d="${personBody}"/>`),
    active: solid(ACTIVE, `<circle cx="12" cy="8.2" r="3.9"/><path d="${personBody}z"/>`)
  }
}

const preview = []

for (const [name, pair] of Object.entries(icons)) {
  for (const [state, svg] of Object.entries(pair)) {
    const file = join(outDir, `${name}${state === 'active' ? '-active' : ''}.png`)
    const tmp = join(outDir, `.${name}-${state}.svg`)
    writeFileSync(tmp, svg)
    execFileSync('rsvg-convert', ['-w', String(SIZE), '-h', String(SIZE), '-o', file, tmp])
    execFileSync('rm', [tmp])
    console.log('generated', file)
    preview.push({ label: `${name}${state === 'active' ? '-active' : ''}`, svg })
  }
}

// 预览稿：8 个图标按 24 栅格等比放大排列，方便目检
const cell = 150
const pad = 20
const cols = 4
const rows = 2
const sheetW = cell * cols + pad * 2
const sheetH = cell * rows + pad * 2
const groups = preview
  .map(({ label, svg }, i) => {
    const attrs = svg.slice(svg.indexOf('<svg') + 4, svg.indexOf('>'))
      .replace(/\s(xmlns|width|height|viewBox)="[^"]*"/g, '')
      .trim()
    const inner = svg.slice(svg.indexOf('>') + 1, svg.lastIndexOf('</svg>'))
    const x = pad + (i % cols) * cell
    const y = pad + Math.floor(i / cols) * cell
    return `<g transform="translate(${x},${y}) scale(5)" ${attrs}>${inner}</g>
      <text x="${x + 60}" y="${y + cell - 12}" font-size="11" fill="#8a8f99" font-family="Helvetica" text-anchor="middle">${label}</text>`
  })
  .join('')

const sheet = `<svg xmlns="http://www.w3.org/2000/svg" width="${sheetW}" height="${sheetH}" viewBox="0 0 ${sheetW} ${sheetH}"><rect width="${sheetW}" height="${sheetH}" fill="#ffffff"/>${groups}</svg>`
const sheetSvg = join(outDir, '.preview.svg')
writeFileSync(sheetSvg, sheet)
execFileSync('rsvg-convert', ['-w', String(sheetW * 2), '-o', '/tmp/tabbar-preview.png', sheetSvg])
execFileSync('rm', [sheetSvg])
console.log('preview -> /tmp/tabbar-preview.png')
