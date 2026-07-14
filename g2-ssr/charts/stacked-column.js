import { base, fields } from './common.js';
export default (data, axis) => {
  const { x, y, color } = fields(axis, { x: 'name', y: 'value', color: 'series' });
  return { ...base, type: 'interval', data, transform: [{ type: 'stackY' }], encode: { x, y, color }, axis: { x: { title: x }, y: { title: y } } };
};
