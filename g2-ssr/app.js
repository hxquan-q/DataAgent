import http from 'node:http';
import { mkdir } from 'node:fs/promises';
import { createReadStream, existsSync } from 'node:fs';
import { basename, dirname, extname, resolve, sep } from 'node:path';
import { createRequire } from 'node:module';
import { charts } from './charts/index.js';

const require = createRequire(import.meta.url);
const { registerFont } = require('canvas');
const { createChart } = require('@antv/g2-ssr');
const ROOT = resolve(import.meta.dirname);
const OUTPUT_DIR = resolve(process.env.OUTPUT_DIR || resolve(ROOT, 'output'));
const PORT = Number(process.env.PORT || 3000);
const TIMEOUT_MS = 15_000;

registerFont(resolve(ROOT, 'fonts/DroidSansFallbackFull.ttf'), { family: 'Droid Sans Fallback' });
await mkdir(OUTPUT_DIR, { recursive: true });

function json(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}

function outputPath(requested, type) {
  const name = basename(requested || `${type}-${Date.now()}.png`).replace(/[^\w.-]/g, '_');
  const file = resolve(OUTPUT_DIR, extname(name) ? name : `${name}.png`);
  if (!file.startsWith(`${OUTPUT_DIR}${sep}`)) throw new Error('非法输出路径');
  return file;
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 2_000_000) throw new Error('请求体超过 2MB');
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

async function render(body) {
  const { type, axis = {}, data, path } = body;
  if (!charts[type]) throw new Error(`不支持的图表类型: ${type}`);
  if (!Array.isArray(data) || data.length === 0) throw new Error('data 必须是非空数组');

  const file = outputPath(path, type);
  await mkdir(dirname(file), { recursive: true });
  const job = (async () => {
    const chart = await createChart(charts[type](data, axis));
    try {
      chart.exportToFile(file);
    } finally {
      chart.destroy();
    }
  })();
  await Promise.race([job, new Promise((_, reject) => setTimeout(() => reject(new Error('渲染超时 15s')), TIMEOUT_MS))]);
  return { url: `/charts/${basename(file)}`, path: file };
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === 'GET' && req.url === '/health') return json(res, 200, { status: 'ok' });
    if (req.method === 'GET' && req.url?.startsWith('/charts/')) {
      const file = resolve(OUTPUT_DIR, basename(req.url));
      if (!existsSync(file)) return json(res, 404, { error: 'not found' });
      res.writeHead(200, { 'content-type': 'image/png' });
      return createReadStream(file).pipe(res);
    }
    if (req.method !== 'POST' || req.url !== '/render') return json(res, 404, { error: 'not found' });
    return json(res, 200, await render(await readJson(req)));
  } catch (error) {
    return json(res, 400, { error: error.message });
  }
});

server.requestTimeout = TIMEOUT_MS;
server.listen(PORT, '0.0.0.0', () => console.log(`g2-ssr listening on :${PORT}`));
