export function trackProgress(parts: Map<string, number>, id: string, percent: number, count: number) {
  if (!Number.isFinite(percent) || percent < 0 || !id) return null;
  parts.set(id, Math.max(parts.get(id) || 0, Math.min(100, percent)));
  return Math.min(100, [...parts.values()].reduce((sum, value) => sum + value, 0) / Math.max(count, parts.size, 1));
}
