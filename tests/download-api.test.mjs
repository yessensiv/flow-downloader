import test from 'node:test';
import assert from 'node:assert/strict';

// Run against an already running local server: FLOW_TEST_URL=http://127.0.0.1:3001
const base = process.env.FLOW_TEST_URL;
test('download API rejects cross-origin, malformed, oversized and missing-file requests', { skip: !base }, async () => {
  const post = (body, origin = base) => fetch(`${base}/api/downloads`, { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body });
  assert.equal((await post('{}', 'https://example.org')).status, 403);
  assert.equal((await post('{')).status, 400);
  assert.equal((await post(JSON.stringify({ videoId: '../../bad', optionId: 'best' }))).status, 400);
  assert.equal((await post(JSON.stringify({ videoId: 'aqz-KE-bpKQ', optionId: 'best/../../file' }))).status, 400);
  assert.equal((await post(JSON.stringify({ padding: 'x'.repeat(5000) }))).status, 413);
  assert.equal((await fetch(`${base}/api/downloads/no-such-task`)).status, 404);
  assert.equal((await fetch(`${base}/api/downloads/no-such-task/file`)).status, 404);
});
