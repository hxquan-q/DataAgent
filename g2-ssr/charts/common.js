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
// 企业级图表主题：统一色板 / 图例 / 提示框 / 留白，所有图表通过 base 共享
// ponytail: 静态 PNG 无需交互 tooltip，关闭以免渲染空白浮层
export const PALETTE = [
  '#5B8FF9', '#5AD8A6', '#5D7092', '#F6BD16', '#E8684A', '#6DC8EC', '#9270CA', '#FF9D4D',
  '#269A99', '#FF99C3', '#945FB9', '#5D8B7E',
];

export const base = {
  width: 800,
  height: 480,
  autoFit: false,
  animate: false,
  padding: [48, 40, 64, 64],
  theme: 'classic',
  scale: { color: { palette: PALETTE } },
  legend: {
    color: {
      position: 'top',
      layout: { justifyContent: 'center' },
      itemLabelFontSize: 13,
      itemLabelFill: '#333',
    },
  },
  tooltip: false,
  axis: {
    x: { titleFontSize: 13, labelFontSize: 12, labelFill: '#555' },
    y: { titleFontSize: 13, labelFontSize: 12, labelFill: '#555' },
  },
};

export function fields(axis = {}, defaults = {}) {
  return { ...defaults, ...axis };
}
