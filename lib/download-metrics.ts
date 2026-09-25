export type TransferMetrics = { speed: number | null; eta: number | null; updatedAt: number; track: number };
export function parseTransfer(line: string, now = Date.now()) {
  const match = /^FLOW:([^:]+):\s*([\d.]+)%:([^:]+):([^:]+)$/.exec(line.trim());
  if (!match || !Number.isFinite(Number(match[2]))) return null;
  const number = (value: string) => value.trim() && Number.isFinite(Number(value)) && Number(value) >= 0 ? Number(value) : null;
  return { id: match[1], percent: Number(match[2]), speed: number(match[3]), eta: number(match[4]), updatedAt: now };
}
export function freshTransfer(metrics: TransferMetrics | undefined, downloading: boolean, now = Date.now()) {
  return downloading && metrics && now - metrics.updatedAt < 10_000 ? metrics : null;
}
export function formatSpeed(bytes: number | null) {
  if (bytes === null || bytes <= 0) return 'Уточняем…';
  return bytes >= 1024 ** 2 ? `${(bytes / 1024 ** 2).toFixed(1)} МБ/с` : `${Math.max(1, Math.round(bytes / 1024))} КБ/с`;
}
export function formatWait(seconds: number | null) {
  if (seconds === null) return 'Уточняем…';
  if (seconds < 60) return `≈ ${Math.max(1, Math.ceil(seconds))} сек.`;
  return `≈ ${Math.ceil(seconds / 60)} мин.`;
}
