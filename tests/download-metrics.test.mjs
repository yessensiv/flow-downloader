import test from 'node:test';
import assert from 'node:assert/strict';
import { parseTransfer, freshTransfer, formatSpeed, formatWait } from '../lib/download-metrics.ts';
test('transfer parses numeric metrics and does not invent missing speed or ETA', () => {
  assert.deepEqual(parseTransfer('FLOW:299: 23.5%:1048576:12', 100), { id: '299', percent: 23.5, speed: 1048576, eta: 12, updatedAt: 100 });
  const missing = parseTransfer('FLOW:140:0.0%:NA:NA');
  assert.equal(missing.speed, null); assert.equal(missing.eta, null);
  assert.equal(parseTransfer('unrelated output'), null);
  assert.equal(parseTransfer('FLOW:140:12%:-1:Infinity').speed, null);
  assert.equal(parseTransfer('FLOW:140:12%:-1:Infinity').eta, null);
});
test('stale metrics and non-download stages never report old speed', () => {
  const metrics = { speed: 10, eta: 20, track: 1, updatedAt: 100 };
  assert.equal(freshTransfer(metrics, true, 1000), metrics);
  assert.equal(freshTransfer(metrics, true, 10100), null);
  assert.equal(freshTransfer(metrics, false, 1000), null);
  assert.equal(formatSpeed(null), 'Уточняем…');
  assert.equal(formatSpeed(1048576), '1.0 МБ/с');
  assert.equal(formatWait(null), 'Уточняем…');
  assert.equal(formatWait(65), '≈ 2 мин.');
});
