import test from 'node:test';
import assert from 'node:assert/strict';
import { validOptionId, downloadFormat } from '../lib/download-format.ts';

test('conversion IDs allow only supported bitrates and reject arbitrary selectors', () => {
  for (const id of ['140-drc', '137+140', '251:mp3:128', '251:mp3:192', '251:mp3:256', '251:mp3:320']) assert.equal(validOptionId(id), true);
  for (const id of ['251:mp3:999', '251:mp3:0', 'best/audio', '../file', '251;cmd', '251:mp3:320:extra', null]) assert.equal(validOptionId(id), false);
});
test('MP3 uses exact source and selected bitrate; native audio is never transcoded', () => {
  const mp3 = downloadFormat({ id: '251:mp3:192', kind: 'audio', container: 'MP3', conversion: true });
  assert.equal(mp3.mime, 'audio/mpeg');
  assert.deepEqual(mp3.args, ['-f', '251', '--extract-audio', '--audio-format', 'mp3', '--audio-quality', '192K']);
  const m4a = downloadFormat({ id: '140', kind: 'audio', container: 'M4A', conversion: false });
  assert.equal(m4a.mime, 'audio/mp4'); assert.deepEqual(m4a.args, ['-f', '140']);
  assert.equal(downloadFormat({ id: '251', kind: 'audio', container: 'WEBM', conversion: false }).mime, 'audio/webm');
});
test('video still merges; inconsistent conversion and output formats fail closed', () => {
  assert.deepEqual(downloadFormat({ id: '137+140', kind: 'video', container: 'MP4' }).args, ['-f', '137+140', '--merge-output-format', 'mp4']);
  assert.throws(() => downloadFormat({ id: '251:mp3:128', kind: 'audio', container: 'WEBM', conversion: true }));
  assert.throws(() => downloadFormat({ id: '140', kind: 'audio', container: 'MP3', conversion: false }));
});
