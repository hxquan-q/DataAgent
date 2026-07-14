import { base, fields } from './common.js';
export default (data, axis) => {
  const { x, y, color } = fields(axis, { x: 'stage', y: 'value', color: 'stage' });
  return {
    ...base,
    type: 'interval',
    data,
    coordinate: { transform: [{ type: 'transpose' }] },
    transform: [{ type: 'sortX', by: 'y', reverse: true }, { type: 'symmetryY' }],
    encode: { x, y, color },
    scale: { x: { padding: 0 } },
    style: { shape: 'funnel' },
    labels: [{ text: y, position: 'inside' }],
  };
};
