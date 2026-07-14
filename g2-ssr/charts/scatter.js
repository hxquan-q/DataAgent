import { base, fields } from './common.js';
export default (data, axis) => {
  const { x, y, color, size } = fields(axis, { x: 'x', y: 'y', color: 'group', size: 'size' });
  return { ...base, type: 'point', data, encode: { x, y, color, size }, scale: { size: { range: [5, 18] } }, axis: { x: { title: x }, y: { title: y } } };
};
