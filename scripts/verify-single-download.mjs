// Explicit integration check; pass a URL argument, never embed private/unlisted URLs here.
import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import { createWriteStream } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import path from 'node:path';
const base = process.env.FLOW_TEST_URL || 'http://127.0.0.1:3001';
async function post(route, body) {
  const response = await fetch(base + route, { method: 'POST', headers: { Origin: base, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await response.json(); assert.ok(response.ok, JSON.stringify(data)); return data;
}
assert.ok(process.argv[2], 'Pass a YouTube video URL');
const media = await post('/api/analyze', { url: process.argv[2] });
const choice = media.options.filter(o => o.kind === 'video' && o.container === 'MP4' && o.size).sort((a, b) => a.size - b.size)[0];
assert.ok(choice);
console.log(JSON.stringify({ analyzed: true, optionId: choice.id, height: choice.height, duration: media.duration }));
const created = await post('/api/downloads', { videoId: media.id, optionId: choice.id });
let job;
try {
  const deadline = Date.now() + 5 * 60_000;
  do {
    await delay(1000);
    job = await (await fetch(`${base}/api/downloads/${created.id}`)).json();
    console.log(JSON.stringify({ state: job.state, transfer: job.transfer, timings: job.timings }));
    assert.ok(!['error', 'cancelled'].includes(job.state), job.error);
    assert.ok(Date.now() < deadline, 'Timeout');
  } while (job.state !== 'ready');
  const response = await fetch(`${base}/api/downloads/${created.id}/file`);
  assert.equal(response.status, 200);
  const file = path.resolve('.tools/smoke-download.mp4');
  await pipeline(Readable.fromWeb(response.body), createWriteStream(file));
  const probe = process.env.FFPROBE_PATH || path.resolve('.tools', process.platform === 'win32' ? 'ffprobe.exe' : 'ffprobe');
  const { stdout } = await promisify(execFile)(probe, ['-v', 'error', '-show_entries', 'stream=codec_type,height:format=duration,size', '-of', 'json', file], { windowsHide: true });
  const info = JSON.parse(stdout);
  assert.ok(info.streams.some(s => s.codec_type === 'audio'));
  assert.ok(info.streams.some(s => s.codec_type === 'video' && s.height === choice.height));
  assert.ok(Math.abs(Number(info.format.duration) - media.duration) < 2);
  assert.equal(Number(info.format.size), job.size);
  console.log(JSON.stringify({ passed: true, timings: job.timings, ...info }));
} finally {
  if (job?.state !== 'ready') await fetch(`${base}/api/downloads/${created.id}`, { method: 'DELETE', headers: { Origin: base } });
}
