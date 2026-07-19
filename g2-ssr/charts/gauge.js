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
import { base, fields } from './common.js';
export default (data, axis) => {
  const { y } = fields(axis, { y: 'value' });
  const value = Math.max(0, Math.min(100, Number(data?.[0]?.[y] ?? 0)));
  return {
    ...base,
    type: 'interval',
    data: [{ part: `完成 ${value}%`, value }, { part: '剩余', value: 100 - value }],
    coordinate: { type: 'theta', startAngle: -Math.PI * 1.25, endAngle: Math.PI * 0.25, innerRadius: 0.72, outerRadius: 0.9 },
    transform: [{ type: 'stackY' }],
    encode: { y: 'value', color: 'part' },
    scale: { color: { domain: [`完成 ${value}%`, '剩余'], range: ['#22c55e', '#e5e7eb'] } },
    legend: false,
    labels: [{ text: 'part', position: 'inside' }],
  };
};
