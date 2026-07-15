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
  const { x, y, y2 } = fields(axis, { x: 'name', y: 'sales', y2: 'rate' });
  return {
    ...base,
    type: 'view',
    data,
    scale: { y: { independent: true } },
    children: [
      { type: 'interval', encode: { x, y }, axis: { y: { title: y, position: 'left' } } },
      { type: 'line', encode: { x, y: y2, color: () => '#f97316' }, style: { lineWidth: 3 }, axis: { y: { title: y2, position: 'right' } } },
      { type: 'point', encode: { x, y: y2, color: () => '#f97316' }, style: { r: 4 } },
    ],
  };
};
