import test from 'node:test';
import assert from 'node:assert/strict';
import { trackProgress } from '../lib/download-progress.ts';
import { readSavedDownload } from '../lib/saved-download.ts';
test('two streams share download progress and retries do not move it backwards', () => {
  const parts = new Map();
  assert.equal(trackProgress(parts, '137', 100, 2), 50);
  assert.equal(trackProgress(parts, '140', 10, 2), 55);
  assert.equal(trackProgress(parts, '140', 0, 2), 55);
  assert.equal(trackProgress(parts, '140', 100, 2), 100);
  assert.equal(trackProgress(new Map(), '251', 60, 1), 60);
  assert.equal(trackProgress(parts, '140', NaN, 2), null);
});
test('saved task restores only UUIDs, not paths or arbitrary URLs', () => {
  assert.equal(readSavedDownload('cd867144-cbab-4292-9f2a-705fb165b155'), 'cd867144-cbab-4292-9f2a-705fb165b155');
  for (const input of [null, '', '../../file', 'https://example.com', '{}']) assert.equal(readSavedDownload(input), null);
});
