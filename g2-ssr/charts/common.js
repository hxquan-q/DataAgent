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
