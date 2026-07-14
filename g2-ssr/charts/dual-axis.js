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
