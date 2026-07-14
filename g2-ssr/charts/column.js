import { base, fields } from './common.js';
export default (data, axis) => {
  const { x, y, color } = fields(axis, { x: 'name', y: 'value' });
  return { ...base, type: 'interval', data, encode: { x, y, color }, axis: { x: { title: x }, y: { title: y } } };
};
