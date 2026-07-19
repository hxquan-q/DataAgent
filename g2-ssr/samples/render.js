/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
