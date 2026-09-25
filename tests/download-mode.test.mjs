import test from 'node:test';
import assert from 'node:assert/strict';
import { savedDownloadKey, readSavedDownload } from '../lib/saved-download.ts';

test('video and audio restore independent tasks when switching back and forth', () => {
  const storage = new Map();
  const video = '11111111-1111-4111-8111-111111111111';
  const audio = '22222222-2222-4222-8222-222222222222';
  storage.set(savedDownloadKey('video'), video);
  assert.equal(readSavedDownload(storage.get(savedDownloadKey('audio')) ?? null), null);
  storage.set(savedDownloadKey('audio'), audio);
  assert.equal(readSavedDownload(storage.get(savedDownloadKey('video'))), video);
  assert.equal(readSavedDownload(storage.get(savedDownloadKey('audio'))), audio);
  storage.delete(savedDownloadKey('audio'));
  assert.equal(storage.get(savedDownloadKey('video')), video);
});
