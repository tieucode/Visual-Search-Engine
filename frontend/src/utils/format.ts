export function formatAgo(iso: string, now = Date.now()): string {
  const diff = Math.max(0, Math.floor((now - new Date(iso).getTime()) / 1000));
  if (Number.isNaN(diff) || diff < 60) return 'now';
  if (diff < 3600) return `${Math.floor(diff / 60)}m`;
  if (diff < 86_400) return `${Math.floor(diff / 3600)}h`;
  return `${Math.floor(diff / 86_400)}d`;
}

export function formatNumber(value: number): string {
  return value.toLocaleString('en-US');
}

export function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / Math.pow(1024, i);
  const formatted = value >= 100 || Number.isInteger(value) ? Math.round(value).toString() : value.toFixed(1);
  return `${formatted} ${units[i]}`;
}

export function shortName(name: string, max = 24): string {
  if (name.length <= max) return name;
  const dot = name.lastIndexOf('.');
  const ext = dot > 0 ? name.slice(dot) : '';
  return `${name.slice(0, max - ext.length - 1)}…${ext}`;
}

