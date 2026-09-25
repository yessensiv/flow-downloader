type Window = { count: number; reset: number };
export function takeRequest(windows: Map<string, Window>, key: string, limit: number, now = Date.now(), duration = 60_000) {
  let window = windows.get(key);
  if (!window || now >= window.reset) { window = { count: 0, reset: now + duration }; windows.set(key, window); }
  if (window.count >= limit) return Math.max(1, Math.ceil((window.reset - now) / 1000));
  window.count++;
  return 0;
}
const shared = globalThis as typeof globalThis & { flowRateLimits?: Map<string, Window> };
const windows = shared.flowRateLimits ??= new Map<string, Window>();
// Aggregate limits for this single-process MVP. Do not trust client-supplied IP headers.
export function rateLimit(kind: 'analyze' | 'download') {
  const retry = takeRequest(windows, kind, kind === 'analyze' ? 30 : 10);
  return retry ? Response.json({ error: `Слишком много запросов. Попробуйте через ${retry} сек.`, code: 'RATE_LIMIT' }, { status: 429, headers: { 'Cache-Control': 'no-store', 'Retry-After': String(retry) } }) : null;
}
