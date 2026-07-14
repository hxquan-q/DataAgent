import column from './column.js';
export default (data, axis) => ({ ...column(data, axis), coordinate: { transform: [{ type: 'transpose' }] } });
