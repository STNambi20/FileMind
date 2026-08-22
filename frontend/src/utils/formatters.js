export function formatBytes(bytes) {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unitIndex = 0;

  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }

  return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unitIndex]}`;
}

export function formatBytesDetailed(bytes) {
  return `${formatBytes(bytes)} (${formatCount(bytes || 0)} bytes)`;
}

export function formatPercent(value) {
  if (!value) return "0%";
  const percentage = value * 100;
  return `${percentage.toFixed(percentage >= 10 ? 0 : 1)}%`;
}

export function formatCount(value) {
  return new Intl.NumberFormat("en-US").format(value || 0);
}

export function formatDateTime(value) {
  if (!value) return "Not available";
  return new Date(value).toLocaleString();
}
