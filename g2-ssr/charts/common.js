export const base = {
  width: 800,
  height: 480,
  autoFit: false,
  animate: false,
  padding: 56,
  theme: 'classic',
};

export function fields(axis = {}, defaults = {}) {
  return { ...defaults, ...axis };
}
