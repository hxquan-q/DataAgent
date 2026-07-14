import { base, fields } from './common.js';
export default (data, axis) => {
  const { x, y, color } = fields(axis, { x: 'name', y: 'value' });
  return { ...base, type: 'line', data, encode: { x, y, color }, style: { lineWidth: 3 }, axis: { x: { title: x }, y: { title: y } } };
};
