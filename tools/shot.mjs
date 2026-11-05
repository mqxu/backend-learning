/**
 * 用 Chrome DevTools Protocol 把本地 HTML 整页截成图片。
 * 只依赖 node 内置 fetch / WebSocket，不引入 puppeteer。
 *
 * 传 .png 就直接落 PNG；传 .webp 时先截 PNG 再用 cwebp 转无损 WebP，
 * 体积约为 PNG 的三分之一，像素完全一致（界面截图里的文字不能有损）。
 */
import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

const CHROME =
  process.env.CHROME_BIN ||
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

async function waitJson(url, timeoutMs = 15000) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    try {
      const res = await fetch(url);
      if (res.ok) return await res.json();
    } catch {
      /* 还没起来 */
    }
    await sleep(120);
  }
  throw new Error(`Chrome 调试端口在 ${timeoutMs}ms 内没起来`);
}

class Cdp {
  constructor(ws) {
    this.ws = ws;
    this.id = 0;
    this.pending = new Map();
    this.listeners = new Map();
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
      } else if (msg.method) {
        const fn = this.listeners.get(msg.method);
        if (fn) fn(msg.params);
      }
    });
  }

  static async connect(url) {
    const ws = new WebSocket(url);
    await new Promise((resolve, reject) => {
      ws.addEventListener('open', resolve, { once: true });
      ws.addEventListener('error', reject, { once: true });
    });
    return new Cdp(ws);
  }

  send(method, params = {}) {
    const id = ++this.id;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
  }

  once(method) {
    return new Promise((resolve) => this.listeners.set(method, resolve));
  }

  close() {
    this.ws.close();
  }
}

/**
 * @param {string} htmlPath 本地 HTML 绝对路径
 * @param {string} pngPath  输出 PNG 绝对路径
 * @param {{width?:number, scale?:number}} opts
 * @returns {Promise<{width:number,height:number}>} 截图尺寸（CSS 像素）
 */
export async function screenshot(htmlPath, pngPath, opts = {}) {
  const width = opts.width ?? 1180;
  const scale = opts.scale ?? 2;
  const port = 9300 + Math.floor(Math.random() * 600);

  const proc = spawn(
    CHROME,
    [
      '--headless=new',
      '--no-sandbox',
      '--disable-gpu',
      '--enable-unsafe-swiftshader',
      '--hide-scrollbars',
      '--disable-extensions',
      '--disable-background-networking',
      `--remote-debugging-port=${port}`,
      'about:blank',
    ],
    { stdio: 'ignore' },
  );

  try {
    const list = await waitJson(`http://127.0.0.1:${port}/json/list`);
    const page = list.find((t) => t.type === 'page');
    if (!page) throw new Error('找不到可用的页面 target');

    const cdp = await Cdp.connect(page.webSocketDebuggerUrl);
    try {
      await cdp.send('Page.enable');
      await cdp.send('Emulation.setDeviceMetricsOverride', {
        width,
        height: 1000,
        deviceScaleFactor: 1,
        mobile: false,
      });

      const loaded = cdp.once('Page.loadEventFired');
      await cdp.send('Page.navigate', { url: `file://${htmlPath}` });
      await Promise.race([loaded, sleep(8000)]);

      // 等字体和图片落位
      await cdp.send('Runtime.evaluate', {
        expression: 'document.fonts.ready.then(()=>true)',
        awaitPromise: true,
      });

      const { contentSize } = await cdp.send('Page.getLayoutMetrics');
      const height = Math.ceil(contentSize.height);
      const w = Math.ceil(Math.max(contentSize.width, width));

      const shot = await cdp.send('Page.captureScreenshot', {
        format: 'png',
        captureBeyondViewport: true,
        clip: { x: 0, y: 0, width: w, height, scale },
      });

      const { writeFileSync, unlinkSync } = await import('node:fs');
      const buffer = Buffer.from(shot.data, 'base64');
      if (pngPath.endsWith('.webp')) {
        // Chrome 只能截 PNG/JPEG，转 WebP 交给 cwebp。
        // 用无损编码：界面截图里的文字不能有损，否则细笔画会糊
        const tmp = `${pngPath}.tmp.png`;
        writeFileSync(tmp, buffer);
        try {
          await new Promise((resolve, reject) => {
            const encoder = spawn('cwebp', ['-quiet', '-lossless', '-m', '6', '-metadata', 'none', tmp, '-o', pngPath]);
            encoder.on('error', () => reject(new Error('找不到 cwebp，先执行 brew install webp')));
            encoder.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`cwebp 退出码 ${code}`))));
          });
        } finally {
          unlinkSync(tmp);
        }
      } else {
        writeFileSync(pngPath, buffer);
      }
      return { width: w, height };
    } finally {
      cdp.close();
    }
  } finally {
    proc.kill('SIGKILL');
  }
}
