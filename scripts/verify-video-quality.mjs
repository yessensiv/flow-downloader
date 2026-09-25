// Explicit integration smoke test: downloads full public Blender film at three resolutions.
// Run with a local server: node scripts/verify-video-quality.mjs
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdir, writeFile } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
const base = process.env.FLOW_TEST_URL || 'http://127.0.0.1:3001';
const videoId = 'aqz-KE-bpKQ';
const probe = process.env.FFPROBE_PATH || path.resolve('.tools', process.platform === 'win32' ? 'ffprobe.exe' : 'ffprobe');
const run = promisify(execFile);
const results = [];
async function post(route, body) {
  const response = await fetch(base + route, { method: 'POST', headers: { Origin: base, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await response.json();
  if (!response.ok) throw new Error(`${response.status}: ${JSON.stringify(data)}`);
  return data;
}
await mkdir('.tools/quality-check', { recursive: true });
const heights = process.argv.slice(2).length ? process.argv.slice(2).map(Number) : [1080, 1440, 2160];
assert.ok(heights.every(height => [1080, 1440, 2160].includes(height)), 'Supported heights: 1080 1440 2160');
for (const height of heights) {
  const media = await post('/api/analyze', { url: `https://www.youtube.com/watch?v=${videoId}` });
  const candidates = media.options.filter(o => o.kind === 'video' && o.height === height && o.container === 'MP4' && o.size && o.size < 2 * 1024 ** 3);
  candidates.sort((a, b) => (/^avc/i.test(a.codec) ? 0 : 1) - (/^avc/i.test(b.codec) ? 0 : 1) || a.size - b.size);
  assert.ok(candidates.length, `Missing test candidate for ${height}p`);
  const choice = candidates[0];
  const job = await post('/api/downloads', { videoId, optionId: choice.id });
  console.log(JSON.stringify({ height, jobId: job.id, option: choice.id, expectedBytes: choice.size }));
  const deadline = Date.now() + 30 * 60_000;
  let state;
  let reported = '';
  try {
    do {
      await delay(1500);
      const response = await fetch(`${base}/api/downloads/${job.id}`);
      state = await response.json();
      assert.equal(response.status, 200);
      const milestone = `${state.state}:${Math.floor((state.progress || 0) / 10) * 10}`;
      if (milestone !== reported) { console.log(`${height}p ${milestone}`); reported = milestone; }
      if (state.state === 'error' || state.state === 'cancelled') throw new Error(JSON.stringify(state));
      if (Date.now() > deadline) throw new Error('Test timeout');
    } while (state.state !== 'ready');
    const response = await fetch(`${base}/api/downloads/${job.id}/file`);
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('content-type'), 'video/mp4');
    assert.match(response.headers.get('content-disposition'), /^attachment;/);
    const file = path.resolve('.tools/quality-check', `${height}p.mp4`);
    await pipeline(Readable.fromWeb(response.body), createWriteStream(file));
    const { stdout } = await run(probe, ['-v', 'error', '-show_entries', 'stream=codec_type,codec_name,width,height,avg_frame_rate:format=duration,size', '-of', 'json', file], { windowsHide: true });
    const info = JSON.parse(stdout);
    const video = info.streams.find(s => s.codec_type === 'video');
    const audio = info.streams.find(s => s.codec_type === 'audio');
    assert.equal(video?.height, height); assert.ok(audio);
    assert.ok(Math.abs(Number(info.format.duration) - media.duration) < 2, 'Incomplete duration');
    assert.equal(Number(info.format.size), state.size);
    results.push({ height, optionId: choice.id, jobId: job.id, ...info });
    await writeFile('.tools/quality-check/results.json', JSON.stringify(results, null, 2));
    console.log(`PASS ${height}p: ${video.width}x${video.height}, ${video.avg_frame_rate} fps, ${video.codec_name}+${audio.codec_name}, ${info.format.duration}s, ${info.format.size} bytes`);
  } catch (error) {
    await fetch(`${base}/api/downloads/${job.id}`, { method: 'DELETE', headers: { Origin: base } }).catch(() => {});
    throw error;
  }
}
