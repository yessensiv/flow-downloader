import test from 'node:test';
import assert from 'node:assert/strict';
import { takeRequest } from '../lib/rate-limit.ts';
import { storageFits, storageUsage, GiB } from '../lib/storage-budget.ts';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';

test('request windows reject overflow, report retry and reopen at the exact boundary', () => {
  const windows = new Map();
  assert.equal(takeRequest(windows, 'download', 2, 1000), 0);
  assert.equal(takeRequest(windows, 'download', 2, 1001), 0);
  assert.equal(takeRequest(windows, 'download', 2, 1002), 60);
  assert.equal(takeRequest(windows, 'analyze', 2, 1002), 0);
  assert.equal(takeRequest(windows, 'download', 2, 60999), 1);
  assert.equal(takeRequest(windows, 'download', 2, 61000), 0);
  assert.equal(windows.size, 2);
});

test('storage includes retained files and in-progress tracks in task directories', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'flow-storage-test-'));
  try {
    const id = '11111111-1111-4111-8111-111111111111';
    await mkdir(path.join(root, id));
    await writeFile(path.join(root, id, 'media.mp4'), Buffer.alloc(100));
    await writeFile(path.join(root, id, 'media.audio.part'), Buffer.alloc(50));
    const usage = await storageUsage(root);
    assert.equal(usage.used, 150);
    assert.equal(usage.sizes.get(id), 150);
    assert.ok(usage.free > 0);
  } finally { await rm(root, { recursive: true, force: true }); }
});
test('storage reserves both inputs and merged output and keeps disk headroom', () => {
  assert.equal(storageFits(0, 0, 20 * GiB), true);
  assert.equal(storageFits(0, 4 * GiB, 20 * GiB), true);
  assert.equal(storageFits(3 * GiB, 4 * GiB, 20 * GiB), false);
  assert.equal(storageFits(2 * GiB, 4 * GiB, 20 * GiB), true);
  assert.equal(storageFits(0, 4 * GiB, 8 * GiB), false);
  assert.equal(storageFits(0, 0, 5 * GiB), true);
  assert.equal(storageFits(0, 0, 5 * GiB - 1), false);
});
