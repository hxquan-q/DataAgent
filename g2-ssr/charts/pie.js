import { base, fields } from './common.js';
export default (data, axis) => {
  const { y, color } = fields(axis, { y: 'value', color: 'name' });
  return {
    ...base,
    type: 'interval',
    data,
    coordinate: { type: 'theta', outerRadius: 0.82 },
    transform: [{ type: 'stackY' }],
    encode: { y, color },
    legend: { color: { position: 'right' } },
    labels: [{ text: color, position: 'outside' }],
  };
};
