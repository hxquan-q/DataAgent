import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createRequire } from 'node:module';
import { charts } from '../charts/index.js';
import { samples } from './data.js';

const require = createRequire(import.meta.url);
const { registerFont } = require('canvas');
const { createChart } = require('@antv/g2-ssr');
const root = resolve(import.meta.dirname, '..');
registerFont(resolve(root, 'fonts/DroidSansFallbackFull.ttf'), { family: 'Droid Sans Fallback' });
await mkdir(resolve(root, 'samples'), { recursive: true });

for (const [type, sample] of Object.entries(samples)) {
  const chart = await createChart(charts[type](sample.data, sample.axis));
  try {
    chart.exportToFile(resolve(root, `samples/${type}.png`));
    console.log(`PASS ${type}`);
  } finally {
    chart.destroy();
  }
}
